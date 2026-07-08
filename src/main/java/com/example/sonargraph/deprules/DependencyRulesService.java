package com.example.sonargraph.deprules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Berechnet aus einem {@link DependencyRulesParser.ParseResult} für jedes
 * Bundle die Liste seiner erlaubten Abhängigkeiten (Klassenpfad) sowie die
 * inverse Sicht ("Allowed Consumers").
 *
 * <h2>Algorithmus</h2>
 * Die Events werden in Document-Order durchlaufen. Ein akkumulierter
 * Klassenpfad wird mitgeführt:
 * <ol>
 *   <li>Bei einem {@link DependencyRulesParser.FilterEvent} werden zuerst
 *       alle Excludes angewendet (entfernen betroffene Bundles aus dem
 *       Klassenpfad und merken sich die Regel), dann alle Includes
 *       (fügen betroffene Bundles wieder hinzu und löschen die
 *       Exclude-Information).</li>
 *   <li>Bei einem {@link DependencyRulesParser.BundleEvent} wird die
 *       aktuelle Sicht als erlaubte Deps des Bundles gespeichert. Das
 *       Bundle selbst wird anschließend dem Klassenpfad hinzugefügt.</li>
 *   <li>Bei einem {@link DependencyRulesParser.LayerEvent} (begin) wird
 *       ein neuer Layer geöffnet, bei (end) geschlossen. Sub-Layer
 *       teilen den Klassenpfad des Parents (Klassenpfad wächst nur
 *       durch Bundles, nicht durch Layer-Grenzen).</li>
 * </ol>
 *
 * <h2>Threading</h2>
 * Die Berechnung erfolgt einmalig im Konstruktor. Die Instanz ist
 * danach immutable und thread-safe.
 */
public final class DependencyRulesService {

    private final DependencyRulesParser.ParseResult parseResult;

    /** Forward: bundleName -> Liste erlaubter (oder ausgeschlossener) Deps. */
    private final Map<String, List<AllowedDependency>> allowedDeps;

    /** Reverse: bundleName -> Liste der Bundles, die dieses nutzen dürfen. */
    private final Map<String, List<AllowedDependency>> allowedConsumers;

    /**
     * Cache für wiederholte Anfragen (LRU-light: einfache Map, max 1000
     * Einträge; reicht für UI-Zugriffe).
     */
    private final Map<String, List<AllowedDependency>> forwardCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<AllowedDependency>> eldest) {
            return size() > 1000;
        }
    };

    /** Cache für gefilterte Consumers (siehe {@link #filterByProduct}). */
    private final Map<String, List<AllowedDependency>> reverseCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<AllowedDependency>> eldest) {
            return size() > 1000;
        }
    };

    /**
     * Berechnet die Allowed-Dependencies aus dem Parse-Ergebnis.
     */
    public static DependencyRulesService from(DependencyRulesParser.ParseResult result) {
        return new DependencyRulesService(result);
    }

    /**
     * Parst die XML-Datei und berechnet die Allowed-Dependencies in einem Schritt.
     */
    public static DependencyRulesService fromXml(java.nio.file.Path xml) throws java.io.IOException, org.xml.sax.SAXException {
        return new DependencyRulesService(DependencyRulesParser.parse(xml));
    }

    private DependencyRulesService(DependencyRulesParser.ParseResult parseResult) {
        this.parseResult = parseResult;
        this.allowedDeps = new LinkedHashMap<>();
        this.allowedConsumers = new LinkedHashMap<>();

        // Sammelt alle bislang gesehenen Bundles in Document-Order
        List<BundleRuleEntry> originalClasspath = new ArrayList<>();
        // Name -> aktuelle Sichtbarkeit & Exclude-Info
        Map<String, ExcludedInfo> excludedBy = new HashMap<>();
        // Name -> ob aktuell im Klassenpfad sichtbar
        Set<String> visible = new LinkedHashSet<>();

        for (DependencyRulesParser.Event ev : parseResult.events()) {
            if (ev instanceof DependencyRulesParser.FilterEvent fe) {
                applyFilter(fe.filter(), originalClasspath, visible, excludedBy);
            } else if (ev instanceof DependencyRulesParser.BundleEvent be) {
                BundleRuleEntry b = be.bundle();
                // Aktueller Klassenpfad = alle in originalClasspath sichtbaren Bundles
                List<AllowedDependency> deps = new ArrayList<>();
                for (BundleRuleEntry other : originalClasspath) {
                    if (other.name().equals(b.name())) continue; // self
                    ExcludedInfo info = excludedBy.get(other.name());
                    if (info != null) {
                        deps.add(AllowedDependency.excluded(
                                other.name(), other.classification(), otherLayerPath(other),
                                info.ruleText(), info.ruleLine()));
                    } else {
                        deps.add(AllowedDependency.allowed(
                                other.name(), other.classification(), otherLayerPath(other)));
                    }
                }
                allowedDeps.put(b.name(), Collections.unmodifiableList(deps));

                // Reverse-Index aufbauen: jeder NICHT-ausgeschlossene Dep-Eintrag
                // → dieser Bundle ist Consumer
                for (AllowedDependency ad : deps) {
                    if (ad.isExcluded()) continue; // ausgeschlossene Deps zählen nicht als Consumer
                    allowedConsumers
                            .computeIfAbsent(ad.bundleName(), k -> new ArrayList<>())
                            .add(AllowedDependency.allowed(
                                    b.name(), b.classification(), otherLayerPath(b)));
                }

                // Bundle zum Klassenpfad hinzufügen
                originalClasspath.add(b);
                visible.add(b.name());
                excludedBy.remove(b.name()); // sicher ist sicher
            }
            // LayerEvent ignorieren — beeinflusst den Klassenpfad nicht direkt
        }
    }

    /**
     * Wendet einen Filter auf den aktuellen Klassenpfad an.
     * Reihenfolge: erst Excludes, dann Includes.
     */
    private static void applyFilter(Filter f,
                                    List<BundleRuleEntry> originalClasspath,
                                    Set<String> visible,
                                    Map<String, ExcludedInfo> excludedBy) {
        // 1) Excludes: alle betroffenen Bundles aus visible entfernen
        for (String exBundle : f.excludedBundles()) {
            if (visible.contains(exBundle)) {
                visible.remove(exBundle);
                excludedBy.put(exBundle, new ExcludedInfo(
                        "<exclude bundle=\"" + exBundle + "\"/>", f.lineNumber()));
            }
        }
        for (String exLayer : f.excludedLayers()) {
            for (BundleRuleEntry b : originalClasspath) {
                if (b.classification() != null
                        && (b.classification().equals(exLayer)
                            || b.classification().startsWith(exLayer + "/"))) {
                    if (visible.contains(b.name())) {
                        visible.remove(b.name());
                        excludedBy.put(b.name(), new ExcludedInfo(
                                "<exclude layer=\"" + exLayer + "\"/>", f.lineNumber()));
                    }
                }
            }
        }
        // 2) Includes: alle inkludierten Bundles wieder sichtbar machen
        for (String inBundle : f.includedBundles()) {
            visible.add(inBundle);
            excludedBy.remove(inBundle);
        }
        for (String inLayer : f.includedLayers()) {
            for (BundleRuleEntry b : originalClasspath) {
                if (b.classification() != null
                        && (b.classification().equals(inLayer)
                            || b.classification().startsWith(inLayer + "/"))) {
                    visible.add(b.name());
                    excludedBy.remove(b.name());
                }
            }
        }
    }

    /**
     * Liefert die erlaubten Abhängigkeiten für das gegebene Bundle,
     * <em>ohne</em> Impl-Category-Berücksichtigung. Wird vom Frontend
     * für die Bundle-Hierarchie und die Tooltip-Texte verwendet, wenn
     * keine konkrete Anfrage-Bundle-Selektion vorliegt.
     *
     * @param bundleName Name des Bundles (aus {@code dependencyrules2.xml})
     * @return unveränderliche Liste der erlaubten Deps, oder leere Liste
     *         wenn das Bundle unbekannt ist
     */
    public List<AllowedDependency> allowedDepsFor(String bundleName) {
        return allowedDepsFor(bundleName, null);
    }

        /**
     * Liefert die erlaubten Abhängigkeiten für das gegebene Bundle und
     * berücksichtigt dabei die Impl-Category-Sichtbarkeitsregel.
     *
     * <p>Für jede erlaubte Dep wird geprüft: wenn die Dep
     * {@code category="impl"} hat <em>und</em> ein {@code produkt}
     * besitzt, das vom {@code requestingProduct} abweicht, wird sie
     * als {@link AllowedDependency#implViolated} markiert. Die
     * Prüfung ist unabhängig von der Category des anfragenden
     * Bundles &mdash; ein api-Bundle aus Produkt&nbsp;A bekommt
     * genauso die Impl-Violation-Markierung wie ein impl-Bundle
     * aus Produkt&nbsp;A, wenn die Dep ein impl-Bundle aus
     * Produkt&nbsp;B ist.</p>
     *
     * <p>Bundles ohne {@code produkt}-Attribut werden wie
     * {@code category="api"} behandelt (also für alle sichtbar).</p>
     *
     * @param bundleName        Name des Bundles, dessen Klassenpfad berechnet wird
     * @param requestingProduct Optional; das Produkt, in dessen Kontext der
     *                           Klassenpfad angefragt wird. Bei {@code null}
     *                           werden alle Deps unverändert zurückgegeben.
     */
    public List<AllowedDependency> allowedDepsFor(String bundleName, String requestingProduct) {
        return filterByProduct(forwardCache,
                allowedDeps.getOrDefault(bundleName, List.of()),
                bundleName, requestingProduct);
    }

    /**
     * Liefert die inverse Sicht mit Impl-Category-Berücksichtigung: alle
     * Bundles, die das gegebene Bundle in ihrem erlaubten Klassenpfad
     * haben, wobei Impl-Consumer aus anderen Produkten als
     * {@link AllowedDependency#implViolated} markiert werden.
     */
    public List<AllowedDependency> allowedConsumersFor(String bundleName, String requestingProduct) {
        return filterByProduct(reverseCache,
                allowedConsumers.getOrDefault(bundleName, List.of()),
                bundleName, requestingProduct);
    }

    /**
     * Liefert die inverse Sicht ohne Impl-Category-Berücksichtigung.
     */
    public List<AllowedDependency> allowedConsumersFor(String bundleName) {
        return allowedConsumers.getOrDefault(bundleName, List.of());
    }

    /**
     * Wendet die Impl-Category-Filterung auf eine Liste von
     * {@link AllowedDependency} an und cached das Ergebnis.
     *
     * @param cache             LRUCache für die gefilterten Ergebnisse
     * @param base              ungefilterte Liste (Forward oder Reverse)
     * @param contextName       Bundle-Name, zu dem die Liste gehört
     *                          (für den Cache-Key)
     * @param requestingProduct das Produkt, in dessen Kontext gefiltert wird
     * @return gefilterte, unveränderliche Liste
     */
    private List<AllowedDependency> filterByProduct(
            Map<String, List<AllowedDependency>> cache,
            List<AllowedDependency> base,
            String contextName,
            String requestingProduct) {
        if (requestingProduct == null) return base;
        final String cacheKey = contextName + "\0" + requestingProduct;
        List<AllowedDependency> cached = cache.get(cacheKey);
        if (cached != null) return cached;
        List<AllowedDependency> filtered = new ArrayList<>(base.size());
        for (AllowedDependency d : base) {
            BundleRuleEntry depEntry = bundleEntry(d.bundleName());
            boolean depIsImpl = depEntry != null
                    && "impl".equalsIgnoreCase(depEntry.category());
            if (depIsImpl && depEntry.produkt() != null
                    && !requestingProduct.equals(depEntry.produkt())) {
                filtered.add(AllowedDependency.implViolated(
                        d.bundleName(), d.classification(), d.layerPath()));
            } else {
                filtered.add(d);
            }
        }
        List<AllowedDependency> imm = Collections.unmodifiableList(filtered);
        cache.put(cacheKey, imm);
        return imm;
    }

    /** Liefert das vollständige Parse-Ergebnis (Layer-Hierarchie, Events). */
    public DependencyRulesParser.ParseResult parseResult() {
        return parseResult;
    }

    /** Liefert die Top-Layer-Reihenfolge (z. B. EXTERNAL, HOTSWAP, DEV-FRAMEWORK, …). */
    public List<String> layerOrder() {
        return parseResult.layerOrder();
    }

    /** Liefert die {@link BundleRuleEntry} für den gegebenen Namen oder null. */
    public BundleRuleEntry bundleEntry(String bundleName) {
        return parseResult.bundlesByName().get(bundleName);
    }

    /** Liefert alle Bundle-Namen, die in der XML definiert sind. */
    public Set<String> knownBundleNames() {
        return parseResult.bundlesByName().keySet();
    }

    private static String otherLayerPath(BundleRuleEntry b) {
        // Top-Layer-Pfad = alles vor dem ersten '/'
        String c = b.classification();
        if (c == null || c.isEmpty()) return "";
        int slash = c.indexOf('/');
        return slash < 0 ? c : c.substring(0, slash);
    }

    /** Hilfs-Record für Exclude-Information. */
    private record ExcludedInfo(String ruleText, int ruleLine) {}
}
