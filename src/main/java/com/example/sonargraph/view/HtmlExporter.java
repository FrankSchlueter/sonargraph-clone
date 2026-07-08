package com.example.sonargraph.view;

import com.example.sonargraph.bundle.BundleModel;
import com.example.sonargraph.deprules.DependencyRulesService;
import com.example.sonargraph.example.ExampleModelGenerator;
import com.example.sonargraph.model.ArtifactModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rendert die Beispielgrafik als eigenständige HTML-Datei.
 *
 * <p>Hilfreich für Demos, Screenshots und Tests, weil das Ergebnis
 * ohne RAP-Server in jedem Browser geöffnet werden kann. In dieser
 * Variante werden die Java-Callbacks durch JavaScript-Log-Statements
 * ersetzt.
 *
 * <p>Aufruf:
 * <pre>{@code
 * HtmlExporter.exportExample(Path.of("demo.html"));
 * }</pre>
 */
public final class HtmlExporter {

    private HtmlExporter() {}

    public static void exportExample(Path target) throws IOException {
        ArtifactModel model = ExampleModelGenerator.generate();
        export(model, target);
    }

    public static void export(ArtifactModel model, Path target) throws IOException {
        String html = renderStandalone(model);
        Files.writeString(target, html, StandardCharsets.UTF_8);
    }

    /**
     * Liefert das eigenständige HTML — also die Inline-Visualisierungs-
     * Seite, eingebettet in eine minimale Hülle, mit Mock-Callbacks.
     */
    public static String renderStandalone(ArtifactModel model) {
        String json = JsonModelSerializer.toJson(model);
        String inner = readResource("/visualization/graph.html");
        // Mock-BrowserFunctions einsetzen
        String script =
                "<script>\n" +
                "  window.__javaOnEdgeClick = function(from, to, w, vio){\n" +
                "    console.log('[edge]', from, '->', to, 'w=' + w, vio?'(violation)':'(allowed)');\n" +
                "  };\n" +
                "  window.__javaOnArtifactClick = function(id){\n" +
                "    console.log('[artifact] open', id);\n" +
                "  };\n" +
                "  window.__javaOnDependencySelect = function(id, kind, selected){\n" +
                "    console.log('[dependency select]', id, kind, 'selected=' + selected);\n" +
                "  };\n" +
                "  window.__model = " + json + ";\n" +
                "  document.addEventListener('DOMContentLoaded', function(){\n" +
                "    if (window.__render) window.__render();\n" +
                "  });\n" +
                "</script>";
        return inner.replace("<!--INJECT-MODEL-->", script);
    }

    private static String readResource(String path) {
        try (InputStream in = HtmlExporter.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }

    // ------------------------------------------------------------------
    //  Bundle-Graph Standalone-Export
    // ------------------------------------------------------------------

    /**
     * Exportiert den Bundle-Dependency-Graphen als eigenständige HTML-Datei.
     *
     * <p>Bei Angabe eines {@link DependencyRulesService} werden die
     * Allowed-Dependencies direkt in das JSON eingebettet, sodass die
     * Datei ohne Java-Backend funktioniert.</p>
     */
    public static void exportBundleGraph(BundleModel model,
                                         Path target,
                                         DependencyRulesService rules) throws IOException {
        String html = renderBundleStandalone(model, rules);
        Files.writeString(target, html, StandardCharsets.UTF_8);
    }

    /**
     * Rendert den Bundle-Graph als Standalone-HTML.
     */
    public static String renderBundleStandalone(BundleModel model, DependencyRulesService rules) {
        String json = JsonBundleSerializer.toJson(model, rules);
        String inner = readResource("/visualization/bundlegraph.html");
        String script =
                "<script>\n" +
                "  window.__javaOnBundleClick = function(id){ console.log('[bundle] open', id); };\n" +
                "  window.__javaOnEdgeClick = function(from, to, w){ console.log('[edge]', from, '->', to, 'w=' + w); };\n" +
                "  window.__javaOnBundleSelect = function(id, kind, sel){ console.log('[select]', id, kind, 'sel=' + sel); };\n" +
                "  window.__javaOnDependencyCallback = function(isC, from, card, to){ console.log('[dep]', isC, from, '->', to, 'card=' + card); };\n" +
                "  // Allowed-Dependencies werden direkt aus dem Modell gelesen (kein Backend).\n" +
                "  window.__model = " + json + ";\n" +
                "  document.addEventListener('DOMContentLoaded', function(){\n" +
                "    if (window.__render) window.__render();\n" +
                "  });\n" +
                "</script>";
        return inner.replace("<!--INJECT-MODEL-->", script);
    }
}