package com.example.sonargraph.rap;

import com.example.sonargraph.model.ArtifactModel;
import com.example.sonargraph.view.JsonModelSerializer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * SWT/RAP-Browser-Widget, das den Dependency-Graphen rendert.
 *
 * <p>Das Widget ist bewusst schlank gehalten:
 * <ul>
 *   <li>Ein {@link Browser} enthält eine minimale HTML-Seite mit
 *       Inline-CSS / Inline-JS. Das spart einen Webserver und macht
 *       die Demo in sich geschlossen.</li>
 *   <li>Java → JS: nach dem Laden des Dokuments wird das Modell als
 *       JSON-Variable an JS übergeben ({@code window.__model =
 *       &lt;json&gt;; window.renderModel();}).</li>
 *   <li>JS → Java: zwei {@link BrowserFunction}s werden registriert,
 *       die Java-Callbacks für "Klick auf Verbindung" und
 *       "Klick auf Artefakt-Link" entgegennehmen.</li>
 * </ul>
 *
 * <p>Beispiel:
 * <pre>{@code
 * ArtifactModel model = ExampleModelGenerator.generate();
 * DependencyGraphWidget w = new DependencyGraphWidget(parent, model);
 * w.setOnEdgeClick((from, to) -> System.out.println("edge " + from + " -> " + to));
 * w.setOnArtifactClick(id -> System.out.println("artifact " + id));
 * }</pre>
 */
public class DependencyGraphWidget {

    public interface EdgeClickHandler {
        void onEdgeClick(String fromArtifactId, String toArtifactId, int weight, boolean isViolation);
    }

    public interface ArtifactClickHandler {
        void onArtifactClick(String artifactId);
    }

    public interface DependencySelectHandler {
        void onDependencySelect(String artifactId, String kind, boolean selected);
    }

    private final Browser browser;
    private final ArtifactModel model;
    private final BrowserFunction edgeFn;
    private final BrowserFunction artifactFn;
    private final BrowserFunction dependencySelectFn;
    private EdgeClickHandler onEdge;
    private ArtifactClickHandler onArtifact;
    private DependencySelectHandler onDependencySelect;

    public DependencyGraphWidget(Composite parent, ArtifactModel model) {
        this.model = Objects.requireNonNull(model);

        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new FillLayout());

        this.browser = new Browser(container, SWT.NONE);
        this.edgeFn = new EdgeCallbackFunction(browser, "javaOnEdgeClick", this);
        this.artifactFn = new ArtifactCallbackFunction(browser, "javaOnArtifactClick", this);
        this.dependencySelectFn = new DependencySelectCallbackFunction(browser, "javaOnDependencySelect", this);

        browser.addProgressListener(new ProgressListener() {
            @Override public void changed(ProgressEvent event) {}
            @Override public void completed(ProgressEvent event) { pushModel(); }
        });
        browser.setText(loadHtmlPage());
    }

    public void setOnEdgeClick(EdgeClickHandler h) { this.onEdge = h; }
    public void setOnArtifactClick(ArtifactClickHandler h) { this.onArtifact = h; }
    public void setOnDependencySelect(DependencySelectHandler h) { this.onDependencySelect = h; }

    /** Re-Push des Modells an die JS-Seite (z. B. nach Filter-Änderung). */
    public void refresh() {
        pushModel();
    }

    public Browser getBrowser() { return browser; }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void pushModel() {
        String json = JsonModelSerializer.toJson(model);
        // In RWT landet execute() per Default in der aktuellen Browser-Session.
        String script = "(function(){"
                + "try { window.__model = " + json + "; }"
                + "catch(e){ console.error('model push failed', e); }"
                + "if (window.__render) window.__render();"
                + "})();";
        // 250kB Script-Sicherheitsgrenze pro execute() beachten — bei sehr
        // großen Modellen ggf. in mehrere Chunks splitten.
        if (script.length() > 200_000) {
            chunkedPush(json);
        } else {
            browser.execute(script);
        }
    }

    private void chunkedPush(String json) {
        // Sehr einfache Aufteilung: Modell in ein Hidden-DOM-Element
        // schreiben, dann rendern. Hier nur als Skelett — die Demo
        // liegt deutlich unterhalb der Grenze.
        int chunk = 180_000;
        for (int off = 0; off < json.length(); off += chunk) {
            int end = Math.min(json.length(), off + chunk);
            String part = json.substring(off, end)
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", " ");
            String op = off == 0 ? "=" : "+=";
            browser.execute("window.__modelChunk " + op + " '" + part + "';");
        }
        browser.execute("try{ window.__model = JSON.parse(window.__modelChunk); }"
                + "catch(e){ console.error('chunk parse failed', e); }"
                + "if (window.__render) window.__render();");
    }

    private static String loadHtmlPage() {
        // Inline-HTML aus Ressource "visualization/graph.html" laden.
        // Fällt auf einen eingebauten Stub zurück, falls die Datei fehlt.
        try (InputStream in = DependencyGraphWidget.class.getResourceAsStream(
                "/visualization/graph.html")) {
            if (in == null) return FALLBACK_HTML;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return FALLBACK_HTML;
        }
    }

    // ------------------------------------------------------------------
    // BrowserFunctions — JS -> Java Callbacks
    // ------------------------------------------------------------------

    void handleEdgeClick(Object[] args) {
        if (onEdge == null || args == null || args.length < 2) return;
        try {
            String from = String.valueOf(args[0]);
            String to   = String.valueOf(args[1]);
            int weight  = args.length > 2 ? toInt(args[2]) : 1;
            boolean vio = args.length > 3 && Boolean.parseBoolean(String.valueOf(args[3]));
            onEdge.onEdgeClick(from, to, weight, vio);
        } catch (RuntimeException ignored) { /* swallow — UI thread */ }
    }

    void handleArtifactClick(Object[] args) {
        if (onArtifact == null || args == null || args.length < 1) return;
        try {
            onArtifact.onArtifactClick(String.valueOf(args[0]));
        } catch (RuntimeException ignored) { }
    }

    void handleDependencySelect(Object[] args) {
        if (onDependencySelect == null || args == null || args.length < 3) return;
        try {
            String artifactId = String.valueOf(args[0]);
            String kind = String.valueOf(args[1]);
            boolean selected = Boolean.parseBoolean(String.valueOf(args[2]));
            onDependencySelect.onDependencySelect(artifactId, kind, selected);
        } catch (RuntimeException ignored) { }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (NumberFormatException ex) { return 1; }
    }

    private static String FALLBACK_HTML = "<!doctype html><meta charset=utf-8>"
            + "<title>Dependency Graph</title>"
            + "<body><h1>graph.html nicht gefunden</h1></body>";

    // ------------------------------------------------------------------
    // Innere Klassen — pro BrowserFunction eine eigene Klasse, weil der
    // RWT-Lifecycle sie anhand des Function-Namens identifiziert.
    // ------------------------------------------------------------------

    private static final class EdgeCallbackFunction extends BrowserFunction {
        private final DependencyGraphWidget owner;
        EdgeCallbackFunction(Browser browser, String name, DependencyGraphWidget owner) {
            super(browser, name);
            this.owner = owner;
        }
        @Override public Object function(Object[] arguments) {
            owner.handleEdgeClick(arguments);
            return null;
        }
    }

    private static final class ArtifactCallbackFunction extends BrowserFunction {
        private final DependencyGraphWidget owner;
        ArtifactCallbackFunction(Browser browser, String name, DependencyGraphWidget owner) {
            super(browser, name);
            this.owner = owner;
        }
        @Override public Object function(Object[] arguments) {
            owner.handleArtifactClick(arguments);
            return null;
        }
    }

    private static final class DependencySelectCallbackFunction extends BrowserFunction {
        private final DependencyGraphWidget owner;
        DependencySelectCallbackFunction(Browser browser, String name, DependencyGraphWidget owner) {
            super(browser, name);
            this.owner = owner;
        }
        @Override public Object function(Object[] arguments) {
            owner.handleDependencySelect(arguments);
            return null;
        }
    }
}