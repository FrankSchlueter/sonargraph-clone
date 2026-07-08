package com.example.sonargraph.rap;

import com.example.sonargraph.bundle.BundleDependencyRecord;
import com.example.sonargraph.bundle.BundleModel;
import com.example.sonargraph.deprules.DependencyRulesService;
import com.example.sonargraph.view.JsonBundleSerializer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * SWT/RAP-Browser-Widget, das den Bundle-Dependency-Graphen rendert.
 *
 * <p>Das Widget nutzt einen zweistufigen Modell-Push:
 * <ol>
 *   <li>Beim Erzeugen wird das Modell-JSON in eine temporäre Datei
 *       geschrieben, die vom eingebetteten Jetty-Server ausgeliefert
 *       wird (siehe {@link #MODEL_URL}).</li>
 *   <li>Das HTML lädt das Modell per {@code fetch()} nach &mdash;
 *       ohne Größenlimit und ohne hunderte Roundtrips.</li>
 * </ol>
 *
 * <p>Wenn ein {@link DependencyRulesService} übergeben wird, werden
 * die Allowed-Dependencies und Allowed-Consumers in das JSON
 * eingebettet. Das Frontend liest sie dann <em>synchron</em> aus dem
 * Modell &mdash; in RAP ist {@code BrowserFunction.function()} wegen
 * des Client-Server-Roundtrips nicht synchron, daher ist die
 * Einbettung der einzig zuverlässige Weg.</p>
 */
public class BundleDependencyWidget {

    /** URL, unter der das Modell-JSON abrufbar ist (vom Launcher registriert). */
    public static final String MODEL_URL = "/bundle-model-v2.json";

    public interface BundleClickHandler {
        void onBundleClick(String bundleId);
    }

    public interface EdgeClickHandler {
        void onEdgeClick(String fromBundleId, String toBundleId, int weight);
    }

    public interface BundleSelectHandler {
        void onBundleSelect(String bundleId, String kind, boolean isSelected);
    }

    /**
     * Callback beim Klick auf ein Bundle in der linken (Consumer) oder
     * rechten (Used) Side-Tree-Spalte. Es wird KEINE Selektion im Center-Tree
     * ausgelöst &mdash; der Aufrufer kann darauf basierend eine externe Aktion
     * ausführen (z. B. IDE-Integration, Navigation, Drill-Down).
     */
    public interface DependencyCallbackHandler {
        /**
         * @param isConsumer   true = Klick in linker Spalte (Consumer dieses Bundles),
         *                     false = Klick in rechter Spalte (verwendetes Bundle)
         * @param fromBundle   Name des Bundles, das angeklickt wurde
         * @param cardinality Gewichtung der Dependency (Anzahl Code-Referenzen)
         * @param toBundle     Name des aktuell in der Center-Spalte selektierten Bundles
         */
        void onDependency(boolean isConsumer, String fromBundle, int cardinality, String toBundle);
    }

    private final Browser browser;
    private final BundleModel model;
    private final BrowserFunction bundleClickFn;
    private final BrowserFunction edgeClickFn;
    private final BrowserFunction bundleSelectFn;
    private final BrowserFunction dependencyCallbackFn;
    private BundleClickHandler onBundleClick;
    private EdgeClickHandler onEdgeClick;
    private BundleSelectHandler onBundleSelect;
    private DependencyCallbackHandler onDependency;

    /**
     * Synchron geladenes Regelservice. Wird im Konstruktor gesetzt;
     * danach read-only. Wird verwendet, um die Allowed-Dependencies in
     * die JSON-Datei einzubetten.
     */
    private final DependencyRulesService rulesService;

    public BundleDependencyWidget(Composite parent, BundleModel model) {
        this(parent, model, null, List.of());
    }

    /** Legacy-Delegate: ruft den 4-Arg-Konstruktor mit leerer Dep-Liste auf. */
    public BundleDependencyWidget(Composite parent, BundleModel model,
                                  DependencyRulesService rules) {
        this(parent, model, rules, List.of());
    }

    /**
     * Vollständiger Konstruktor mit Regelservice und Dependency-Records.
     * Die Records werden in die JSON-Datei eingebettet, damit die
     * Allowed-Dependencies- und Impl-Category-Anzeige im Frontend
     * ohne BrowserFunction-Roundtrip funktioniert.
     */
    public BundleDependencyWidget(Composite parent, BundleModel model,
                                  DependencyRulesService rules,
                                  List<BundleDependencyRecord> deps) {
        this.model = Objects.requireNonNull(model);
        this.rulesService = rules;

        // Modell-JSON in Datei schreiben — bei vorhandenem Regelservice
        // mit eingebetteten Allowed-Dependencies und Allowed-Consumers
        BundleModelFileStore.writeModel(model, rules, deps);

        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new FillLayout());

        this.browser = new Browser(container, SWT.NONE);
        this.bundleClickFn       = new BundleClickCallbackFn(browser, "javaOnBundleClick", this);
        this.edgeClickFn         = new EdgeClickCallbackFn(browser, "javaOnEdgeClick", this);
        this.bundleSelectFn      = new BundleSelectCallbackFn(browser, "javaOnBundleSelect", this);
        this.dependencyCallbackFn = new DependencyCallbackFn(browser, "javaOnDependencyCallback", this);

        browser.addProgressListener(new ProgressListener() {
            @Override public void changed(ProgressEvent event) {}
            @Override public void completed(ProgressEvent event) { triggerInitialRender(); }
        });
        browser.setText(loadHtmlPage());
    }

    public void setOnBundleClick(BundleClickHandler h) { this.onBundleClick = h; }
    public void setOnEdgeClick(EdgeClickHandler h) { this.onEdgeClick = h; }
    public void setOnBundleSelect(BundleSelectHandler h) { this.onBundleSelect = h; }
    public void setOnDependency(DependencyCallbackHandler h) { this.onDependency = h; }

    /** Re-Render des Modells anstoßen (z. B. nach Filter-Änderung). */
    public void refresh() {
        triggerInitialRender();
    }

    public Browser getBrowser() { return browser; }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void triggerInitialRender() {
        String script = "if (window.__render) window.__render();";
        if (Boolean.parseBoolean(System.getProperty("bundleGraph.autoClick", "false"))) {
            script += " window.__autoClickFirst = true;";
        }
        // Initial Tree-Level via System-Property setzen (Debug)
        String level = System.getProperty("bundleGraph.treeLevel");
        if (level != null) {
            script += " window.__initialTreeLevel = " + level + ";";
        }
        // Initialer Suchfilter (Debug)
        String filter = System.getProperty("bundleGraph.filter");
        if (filter != null) {
            script += " window.__initialFilter = '" + filter.replace("'", "\\'") + "';";
        }
        // JavaScript mitteilen, ob Regelservice verfügbar ist
        if (rulesService != null) {
            script += " if (window.__onRulesLoaded) window.__onRulesLoaded(true, null);";
        } else {
            script += " if (window.__onRulesLoaded) window.__onRulesLoaded(false, 'keine XML');";
        }
        browser.execute(script);
    }

    /** Liefert das geladene Regelservice oder null. */
    public DependencyRulesService getRulesService() {
        return rulesService;
    }

    private static String loadHtmlPage() {
        try (InputStream in = BundleDependencyWidget.class.getResourceAsStream(
                "/visualization/bundlegraph.html")) {
            if (in == null) return FALLBACK_HTML;
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Impl-Icon (locked.svg) als Inline-SVG einbetten, damit es
            // garantiert verfügbar ist (kein Classpath-Lookup zur
            // Render-Zeit, keine Data-URL-Security-Restriktionen).
            return injectImplIcon(html, loadImplIconMarkup());
        } catch (Exception ex) {
            return FALLBACK_HTML;
        }
    }

    /**
     * Liest {@code /icons/locked.svg} und liefert den rohen SVG-String.
     * Liefert einen leeren String, wenn die Datei nicht gefunden wird.
     */
    private static String loadImplIconMarkup() {
        try (InputStream in = BundleDependencyWidget.class.getResourceAsStream(
                "/icons/locked.svg")) {
            if (in == null) return "";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * Setzt den Platzhalter {@code __IMPL_ICON_SYMBOL__} im HTML durch
     * einen {@code <svg><defs><symbol id="locked-icon">…</symbol></defs></svg>}-Block.
     * Falls das HTML den Platzhalter nicht enthält, wird der Block direkt
     * nach {@code <body>} injiziert.
     */
    static String injectImplIcon(String html, String svgMarkup) {
        String block;
        if (svgMarkup == null || svgMarkup.isEmpty()) {
            block = "";
        } else {
            // Das SVG aus locked.svg in einen <symbol> einwickeln, damit es
            // via <use href="#locked-icon"/> referenziert werden kann.
            // Das Original-<svg>-Tag wird beibehalten, nur das öffnende <svg ...>
            // wird zu <symbol id="locked-icon" ...> und das schließende </svg>
            // wird zu </symbol>.
            String inner = svgMarkup.trim();
            String symbolOpen = "<svg style=\"display:none\" xmlns=\"http://www.w3.org/2000/svg\"><defs>";
            String symbolClose = "</defs></svg>";
            // Konvertiere: <svg ...>...</svg> → <symbol id="locked-icon" ...>...</symbol>
            int firstGt = inner.indexOf('>');
            if (firstGt > 0 && inner.startsWith("<svg")) {
                String openTag = inner.substring(0, firstGt + 1);
                String rest = inner.substring(firstGt + 1);
                int lastLt = rest.lastIndexOf("</svg>");
                String body = (lastLt >= 0) ? rest.substring(0, lastLt) : rest;
                // Ersetze <svg durch <symbol und füge id hinzu
                String symbolTag = openTag.replaceFirst("<svg", "<symbol id=\"locked-icon\"");
                block = symbolOpen + symbolTag + body + "</symbol>" + symbolClose;
            } else {
                // Fallback: rohes SVG einbetten
                block = symbolOpen + inner + "</symbol>" + symbolClose;
            }
        }
        if (html.contains("__IMPL_ICON_SYMBOL__")) {
            return html.replace("__IMPL_ICON_SYMBOL__", block);
        }
        // Fallback: nach <body> injizieren
        int idx = html.indexOf("<body>");
        if (idx >= 0) {
            return html.substring(0, idx + 6) + block + html.substring(idx + 6);
        }
        return block + html;
    }

    // ------------------------------------------------------------------
    // BrowserFunctions — JS -> Java Callbacks
    // ------------------------------------------------------------------

    void handleBundleClick(Object[] args) {
        if (onBundleClick == null || args == null || args.length < 1) return;
        try {
            onBundleClick.onBundleClick(String.valueOf(args[0]));
        } catch (RuntimeException ignored) { }
    }

    void handleEdgeClick(Object[] args) {
        if (onEdgeClick == null || args == null || args.length < 3) return;
        try {
            String from = String.valueOf(args[0]);
            String to   = String.valueOf(args[1]);
            int weight  = toInt(args[2]);
            onEdgeClick.onEdgeClick(from, to, weight);
        } catch (RuntimeException ignored) { }
    }

    void handleBundleSelect(Object[] args) {
        if (onBundleSelect == null || args == null || args.length < 3) return;
        try {
            String bundleId = String.valueOf(args[0]);
            String kind     = String.valueOf(args[1]);
            boolean sel     = Boolean.parseBoolean(String.valueOf(args[2]));
            onBundleSelect.onBundleSelect(bundleId, kind, sel);
        } catch (RuntimeException ignored) { }
    }

    void handleDependencyCallback(Object[] args) {
        if (onDependency == null || args == null || args.length < 4) return;
        try {
            boolean isConsumer = Boolean.parseBoolean(String.valueOf(args[0]));
            String fromBundle  = String.valueOf(args[1]);
            int cardinality    = toInt(args[2]);
            String toBundle    = String.valueOf(args[3]);
            onDependency.onDependency(isConsumer, fromBundle, cardinality, toBundle);
        } catch (RuntimeException ignored) { }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (NumberFormatException ex) { return 0; }
    }

    private static final String FALLBACK_HTML = "<!doctype html><meta charset=utf-8>"
            + "<title>Bundle Dependency Graph</title>"
            + "<body><h1>bundlegraph.html nicht gefunden</h1></body>";

    // ------------------------------------------------------------------
    // Innere Klassen — pro BrowserFunction eine eigene Klasse
    // ------------------------------------------------------------------

    private static final class BundleClickCallbackFn extends BrowserFunction {
        private final BundleDependencyWidget owner;
        BundleClickCallbackFn(Browser browser, String name, BundleDependencyWidget owner) {
            super(browser, name);
            this.owner = owner;
        }
        @Override public Object function(Object[] arguments) {
            owner.handleBundleClick(arguments);
            return null;
        }
    }

    private static final class EdgeClickCallbackFn extends BrowserFunction {
        private final BundleDependencyWidget owner;
        EdgeClickCallbackFn(Browser browser, String name, BundleDependencyWidget owner) {
            super(browser, name);
            this.owner = owner;
        }
        @Override public Object function(Object[] arguments) {
            owner.handleEdgeClick(arguments);
            return null;
        }
    }

    private static final class BundleSelectCallbackFn extends BrowserFunction {
        private final BundleDependencyWidget owner;
        BundleSelectCallbackFn(Browser browser, String name, BundleDependencyWidget owner) {
            super(browser, name);
            this.owner = owner;
        }
        @Override public Object function(Object[] arguments) {
            owner.handleBundleSelect(arguments);
            return null;
        }
    }

    private static final class DependencyCallbackFn extends BrowserFunction {
        private final BundleDependencyWidget owner;
        DependencyCallbackFn(Browser browser, String name, BundleDependencyWidget owner) {
            super(browser, name);
            this.owner = owner;
        }
        @Override public Object function(Object[] arguments) {
            owner.handleDependencyCallback(arguments);
            return null;
        }
    }
}
