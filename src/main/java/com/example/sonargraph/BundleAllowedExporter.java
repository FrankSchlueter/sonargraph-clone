package com.example.sonargraph;

import com.example.sonargraph.bundle.BundleDependencyRecord;
import com.example.sonargraph.bundle.BundleDependencyRecordCsvReader;
import com.example.sonargraph.bundle.BundleModel;
import com.example.sonargraph.bundle.BundleModelBuilder;
import com.example.sonargraph.deprules.DependencyRulesService;
import com.example.sonargraph.view.HtmlExporter;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Standalone-Exporter für den Bundle-Dependency-Graphen inklusive
 * der aus {@code dependencyrules2.xml} berechneten Allowed-Dependencies.
 *
 * <p>Erzeugt eine HTML-Datei, die ohne Java-Backend in jedem Browser
 * geöffnet werden kann und den vollen Funktionsumfang (inkl. Kontextmenü
 * für erlaubte Abhängigkeiten) bereitstellt.</p>
 *
 * <p>Aufruf:</p>
 * <pre>{@code
 * mvn -q -DskipTests compile exec:java \
 *     -Dexec.mainClass=com.example.sonargraph.BundleAllowedExporter \
 *     -Dexec.args="bundlegraph-allowed.html"
 * }</pre>
 *
 * <p>Die {@code bundleDependencies.csv} und die {@code dependencyrules2.xml}
 * werden aus dem aktuellen Arbeitsverzeichnis gelesen. Über die
 * System-Properties {@code bundle.deps.csv} und {@code bundle.rules.xml}
 * können alternative Pfade gesetzt werden. Die Hierarchie der Bundles
 * stammt vollständig aus der XML-Datei (Layer-Schachtelung).</p>
 */
public final class BundleAllowedExporter {

    private BundleAllowedExporter() {}

    public static void main(String[] args) throws IOException, SAXException {
        String outputName = args.length > 0 ? args[0] : "bundlegraph-allowed.html";
        Path depsPath = resolveDepsPath();
        Path rulesPath = resolveRulesPath();
        Path outFile = Path.of(outputName).toAbsolutePath().normalize();

        System.out.println("Lese Dependencies aus " + depsPath + " …");
        List<BundleDependencyRecord> deps = Files.exists(depsPath)
                ? BundleDependencyRecordCsvReader.read(depsPath)
                : List.of();
        System.out.printf(Locale.ROOT, "Modell: %d Dependencies%n", deps.size());

        DependencyRulesService rules = null;
        if (rulesPath != null && Files.exists(rulesPath)) {
            System.out.println("Parse dependencyrules2.xml aus " + rulesPath + " …");
            long t0 = System.currentTimeMillis();
            rules = DependencyRulesService.fromXml(rulesPath);
            long t1 = System.currentTimeMillis();
            System.out.printf(Locale.ROOT,
                    "Allowed-Dependencies berechnet in %d ms (%d Bundles, Layer: %s)%n",
                    (t1 - t0), rules.knownBundleNames().size(),
                    String.join(", ", rules.layerOrder()));
        } else {
            System.out.println("WARN: dependencyrules2.xml nicht gefunden — Allowed-Sicht deaktiviert");
        }

        BundleModel model = new BundleModelBuilder(rules, deps).build();

        System.out.println("Schreibe " + outFile + " …");
        HtmlExporter.exportBundleGraph(model, outFile, rules);
        System.out.println("Fertig. Datei: " + outFile + " ("
                + Files.size(outFile) + " bytes)");
    }

    private static Path resolveDepsPath() {
        String custom = System.getProperty("bundle.deps.csv");
        return custom != null && !custom.isEmpty() ? Path.of(custom) : Path.of("bundleDependencies.csv");
    }

    private static Path resolveRulesPath() {
        String custom = System.getProperty("bundle.rules.xml");
        if (custom != null && !custom.isEmpty()) return Path.of(custom);
        return Path.of("dependencyrules2.xml");
    }
}
