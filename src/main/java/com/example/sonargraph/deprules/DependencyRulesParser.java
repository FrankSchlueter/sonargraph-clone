package com.example.sonargraph.deprules;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parst die {@code dependencyrules2.xml} in eine lineare Event-Liste, die
 * vom {@link DependencyRulesService} zur Klassenpfad-Berechnung verwendet
 * wird.
 *
 * <p>Intern wird SAX verwendet, um an die exakten Zeilennummern jedes
 * Elements zu kommen. Die Events sind in Dokument-Order (depth-first):</p>
 * <pre>
 *   FilterEvent   — vor den nachfolgenden Bundles wirksam
 *   BundleEvent   — definiert ein Bundle, das danach im Klassenpfad liegt
 *   LayerEvent(BEGIN/END) — schachtelt hierarchische Layer
 * </pre>
 *
 * <p>Auskommentierte Elemente ({@code <!-- … -->}) werden übersprungen,
 * ebenso wie Whitespace-only-Text-Knoten.</p>
 */
public final class DependencyRulesParser {

    /**
     * Parst die XML-Datei und liefert das Ergebnis als {@link ParseResult}.
     *
     * @param xml Pfad zur dependencyrules2.xml
     * @return vollständiges Parse-Ergebnis
     * @throws IOException wenn die Datei nicht gelesen werden kann
     * @throws SAXException bei XML-Fehlern
     */
    public static ParseResult parse(Path xml) throws IOException, SAXException {
        try (InputStream in = Files.newInputStream(xml)) {
            return parse(in);
        }
    }

    /**
     * Parst einen XML-Stream. Wird sowohl für Live-Dateien als auch für
     * Test-Fixtures verwendet.
     */
    public static ParseResult parse(InputStream in) throws IOException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        // Sicherheit: externe Entities deaktivieren
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (ParserConfigurationException | SAXException ignored) {
            // Feature nicht überall verfügbar
        }
        SAXParser parser;
        try {
            parser = factory.newSAXParser();
        } catch (ParserConfigurationException ex) {
            throw new SAXException("SAX-Parser nicht erzeugbar", ex);
        }
        Handler handler = new Handler();
        parser.parse(in, handler);
        return handler.toResult();
    }

    // ------------------------------------------------------------------
    //  Event-Typen
    // ------------------------------------------------------------------

    /** Marker-Interface für dokument-order Events. */
    public sealed interface Event permits FilterEvent, BundleEvent, LayerEvent {}

    /** Ein Filter (Collection von include/exclude Regeln). */
    public record FilterEvent(Filter filter) implements Event {}

    /** Definition eines Bundles (an dieser Stelle im Klassenpfad). */
    public record BundleEvent(BundleRuleEntry bundle) implements Event {}

    /** Beginn oder Ende eines Layers. */
    public record LayerEvent(boolean begin, String name, String fullPath, int depth, int lineNumber)
            implements Event {}

    /** Container für Layer-Hierarchie + lineare Event-Liste. */
    public record ParseResult(
            List<LayerNode> topLayers,
            List<Event> events,
            Map<String, BundleRuleEntry> bundlesByName,
            List<String> layerOrder
    ) {}

    /** Mutable Hilfsklasse für den SAX-Handler. */
    private static final class MutableLayer {
        final String name;
        final String fullPath;
        final int depth;
        final MutableLayer parent;
        final List<MutableLayer> subLayers = new ArrayList<>();
        final List<BundleRuleEntry> bundles = new ArrayList<>();

        MutableLayer(String name, String fullPath, int depth, MutableLayer parent) {
            this.name = name;
            this.fullPath = fullPath;
            this.depth = depth;
            this.parent = parent;
        }

        LayerNode toImmutable() {
            return toImmutable(new IdentityHashMap<>());
        }

        /**
         * Konvertiert diesen {@code MutableLayer} in einen
         * unveränderlichen {@link LayerNode}. Der {@code parent}-Pointer
         * wird über die {@code cache}-Map (MutableLayer → bereits
         * erzeugter LayerNode) aufgelöst, um Endlosrekursion zu
         * vermeiden: ein gemeinsamer Parent mehrerer Sub-Layer wird nur
         * einmal konvertiert.
         */
        LayerNode toImmutable(IdentityHashMap<MutableLayer, LayerNode> cache) {
            LayerNode self = cache.get(this);
            if (self != null) return self;
            // Kinder rekursiv konvertieren (parent wird später gesetzt)
            List<LayerNode> kids = new ArrayList<>(subLayers.size());
            for (MutableLayer m : subLayers) kids.add(m.toImmutable(cache));
            self = new LayerNode(name, fullPath, depth, null,
                    Collections.unmodifiableList(kids),
                    Collections.unmodifiableList(bundles));
            cache.put(this, self);
            // Parent nachträglich setzen, damit der Pointer auf die
            // bereits erzeugte (oder noch zu erzeugende) LayerNode-
            // Instanz verweist, ohne eine Endlosrekursion auszulösen.
            if (parent != null) {
                LayerNode parentNode = parent.toImmutable(cache);
                // self ist eine frische Instanz; wir liefern sie gleich
                // zurück, müssen aber den parent nachträglich ersetzen.
                // Da LayerNode ein Record mit final-Feldern ist, bauen
                // wir ihn ein zweites Mal mit dem aufgelösten Parent.
                LayerNode withParent = new LayerNode(self.name(), self.fullPath(),
                        self.depth(), parentNode, self.subLayers(), self.bundles());
                cache.put(this, withParent);
                return withParent;
            }
            return self;
        }
    }

    // ------------------------------------------------------------------
    //  SAX-Handler
    // ------------------------------------------------------------------

    private static final class Handler extends DefaultHandler {

        private Locator locator;

        // Resultat
        private final List<Event> events = new ArrayList<>();
        private final Map<String, BundleRuleEntry> bundlesByName = new LinkedHashMap<>();

        // Aufbau der Layer-Hierarchie
        private final List<MutableLayer> topLayers = new ArrayList<>();
        private final Deque<MutableLayer> stack = new ArrayDeque<>();
        // Top-Layer-Reihenfolge
        private final List<String> layerOrder = new ArrayList<>();
        // Aktueller Bundle-Ordinal innerhalb des aktuellen Top-Layers
        private final Map<String, Integer> ordinalPerLayer = new LinkedHashMap<>();
        private int topLayerIndex = 0;

        // Aktueller Filter, der gerade aufgebaut wird
        private FilterBuilder currentFilterBuilder;
        private int currentFilterLine = 0;

        // Impliziter Filter für <include>/<exclude> direkt im <layer>
        // (nicht in <filter> gewrappt). Wird vor dem nächsten Bundle geflusht.
        private FilterBuilder layerLevelFilterBuilder;
        private int layerLevelFilterLine = 0;

        // Filter-Sammlung pro Layer (in Document-Order)
        private final Deque<List<Filter>> filterStack = new ArrayDeque<>();

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        private int currentLine() {
            return locator != null ? locator.getLineNumber() : 0;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            String tag = qName;
            switch (tag) {
                case "layer" -> {
                    // Vorheriger Layer implizit schließen
                    flushLayerLevelFilter();
                    String name = attributes.getValue("name");
                    if (name == null || name.isEmpty()) {
                        throw new SAXException("<layer> ohne name-Attribut in Zeile " + currentLine());
                    }
                    MutableLayer parent = stack.peek();
                    String fullPath = parent == null ? name : parent.fullPath + "/" + name;
                    int depth = parent == null ? 0 : parent.depth + 1;
                    if (parent == null) {
                        // Top-Layer
                        topLayerIndex++;
                        layerOrder.add(fullPath);
                    }
                    MutableLayer node = new MutableLayer(name, fullPath, depth, parent);
                    if (parent != null) {
                        parent.subLayers.add(node);
                    } else {
                        topLayers.add(node);
                    }
                    stack.push(node);
                    ordinalPerLayer.putIfAbsent(fullPath, 0);
                    filterStack.push(new ArrayList<>());
                    events.add(new LayerEvent(true, name, fullPath, depth, currentLine()));
                }
                case "filter" -> {
                    // Vorheriger impliziter Layer-Level-Filter wird durch
                    // expliziten <filter> abgelöst.
                    flushLayerLevelFilter();
                    currentFilterBuilder = new FilterBuilder();
                    currentFilterLine = currentLine();
                }
                case "include" -> {
                    String bundle = attributes.getValue("bundle");
                    String layer  = attributes.getValue("layer");
                    if (currentFilterBuilder != null) {
                        if (bundle != null) currentFilterBuilder.includedBundles.add(bundle);
                        if (layer  != null) currentFilterBuilder.includedLayers.add(layer);
                    } else {
                        // include direkt im Layer — impliziter Filter
                        if (layerLevelFilterBuilder == null) {
                            layerLevelFilterBuilder = new FilterBuilder();
                            layerLevelFilterLine = currentLine();
                        }
                        if (bundle != null) layerLevelFilterBuilder.includedBundles.add(bundle);
                        if (layer  != null) layerLevelFilterBuilder.includedLayers.add(layer);
                    }
                }
                case "exclude" -> {
                    String bundle = attributes.getValue("bundle");
                    String layer  = attributes.getValue("layer");
                    if (currentFilterBuilder != null) {
                        if (bundle != null) currentFilterBuilder.excludedBundles.add(bundle);
                        if (layer  != null) currentFilterBuilder.excludedLayers.add(layer);
                    } else {
                        // exclude direkt im Layer — impliziter Filter
                        if (layerLevelFilterBuilder == null) {
                            layerLevelFilterBuilder = new FilterBuilder();
                            layerLevelFilterLine = currentLine();
                        }
                        if (bundle != null) layerLevelFilterBuilder.excludedBundles.add(bundle);
                        if (layer  != null) layerLevelFilterBuilder.excludedLayers.add(layer);
                    }
                }
                case "bundle" -> {
                    // Wenn ein impliziter Layer-Level-Filter existiert, jetzt flushen
                    flushLayerLevelFilter();
                    String name = attributes.getValue("name");
                    if (name == null || name.isEmpty()) {
                        throw new SAXException("<bundle> ohne name-Attribut in Zeile " + currentLine());
                    }
                    MutableLayer parent = stack.peek();
                    if (parent == null) {
                        throw new SAXException("<bundle> außerhalb eines <layer> in Zeile " + currentLine());
                    }
                    String ownClassification = attributes.getValue("classification");
                    // Im dependencyrules2.xml ist das classification-Attribut
                    // der absolute Pfad (z. B. "EXTERNAL/SERVER"). Falls
                    // nicht gesetzt, wird der aktuelle Layer-Pfad verwendet.
                    String fullClassification;
                    if (ownClassification != null && !ownClassification.isEmpty()) {
                        fullClassification = ownClassification;
                    } else {
                        fullClassification = parent.fullPath;
                    }
                    int ordinal = ordinalPerLayer.getOrDefault(parent.fullPath, 0);
                    ordinalPerLayer.put(parent.fullPath, ordinal + 1);
                    BundleRuleEntry entry = new BundleRuleEntry(
                            name,
                            attributes.getValue("groupID"),
                            fullClassification,
                            attributes.getValue("repo"),
                            attributes.getValue("produkt"),
                            attributes.getValue("category"),
                            topLayerIndex,
                            ordinal,
                            currentLine()
                    );
                    parent.bundles.add(entry);
                    bundlesByName.put(name, entry);
                    events.add(new BundleEvent(entry));
                }
                // "system" und "layers" werden ignoriert (nur Container)
                default -> { /* ignore */ }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            switch (qName) {
                case "filter" -> {
                    if (currentFilterBuilder == null) return;
                    Filter f = currentFilterBuilder.build(currentFilterLine);
                    currentFilterBuilder = null;
                    // Filter an den aktuellen Layer anhängen
                    List<Filter> list = filterStack.peek();
                    if (list == null) {
                        // Filter außerhalb eines Layers — als global behandeln
                        return;
                    }
                    list.add(f);
                    events.add(new FilterEvent(f));
                }
                case "layer" -> {
                    MutableLayer finished = stack.pop();
                    // Impliziten Filter vor Layer-Ende flushen
                    flushLayerLevelFilter();
                    filterStack.pop();
                    events.add(new LayerEvent(false, finished.name, finished.fullPath,
                            finished.depth, currentLine()));
                }
                default -> { /* ignore */ }
            }
        }

        /**
         * Schließt den impliziten Layer-Level-Filter (falls vorhanden) ab
         * und hängt ihn als regulären Filter an den aktuellen Layer.
         */
        private void flushLayerLevelFilter() {
            if (layerLevelFilterBuilder == null) return;
            Filter f = layerLevelFilterBuilder.build(layerLevelFilterLine);
            layerLevelFilterBuilder = null;
            layerLevelFilterLine = 0;
            List<Filter> list = filterStack.peek();
            if (list == null) return;
            list.add(f);
            events.add(new FilterEvent(f));
        }

        ParseResult toResult() {
            List<LayerNode> imm = new ArrayList<>(topLayers.size());
            for (MutableLayer m : topLayers) imm.add(m.toImmutable());
            return new ParseResult(
                    Collections.unmodifiableList(imm),
                    Collections.unmodifiableList(events),
                    Collections.unmodifiableMap(bundlesByName),
                    Collections.unmodifiableList(layerOrder)
            );
        }
    }

    /** Hilfs-Builder für Filter. */
    static final class FilterBuilder {
        final List<String> includedBundles = new ArrayList<>();
        final List<String> includedLayers  = new ArrayList<>();
        final List<String> excludedBundles = new ArrayList<>();
        final List<String> excludedLayers  = new ArrayList<>();

        Filter build(int line) {
            return new Filter(includedBundles, includedLayers,
                    excludedBundles, excludedLayers, line);
        }
    }
}
