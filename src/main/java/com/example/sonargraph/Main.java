package com.example.sonargraph;

import com.example.sonargraph.example.ExampleModelGenerator;
import com.example.sonargraph.view.HtmlExporter;

import java.nio.file.Path;

/**
 * Standalone-Einstiegspunkt ohne RAP-Lifecycle.
 *
 * <p>Erzeugt das Beispielmodell und exportiert es als HTML-Datei, die
 * in jedem Browser geöffnet werden kann. Nützlich für Demos und zum
 * Erzeugen von Screenshots, ohne einen RAP-Server hochfahren zu müssen.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Path target = args.length > 0 ? Path.of(args[0]) : Path.of("dependency-graph.html");
        ExampleModelGenerator.printSummary(ExampleModelGenerator.generate());
        HtmlExporter.exportExample(target);
        System.out.println("Wrote " + target.toAbsolutePath());
    }
}