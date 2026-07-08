package com.example.sonargraph.rap;

import com.example.sonargraph.bundle.BundleDependencyRecord;
import com.example.sonargraph.bundle.BundleDependencyRecordCsvReader;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.rap.rwt.engine.RWTServlet;
import org.eclipse.rap.rwt.engine.RWTServletContextListener;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Programmatischer Launcher für die Bundle-Dependency-RAP-Anwendung.
 *
 * <p>Startet einen eingebetteten Jetty-Server auf Port 8085, der
 * <em>beide</em> Anwendungen parallel ausliefert:
 * <ul>
 *   <li>{@code /graph} &mdash; Sonargraph-Architecture-View (Beispielmodell)</li>
 *   <li>{@code /bundleGraph} &mdash; Bundle-Dependency-View (XML + Dep-Records)</li>
 * </ul>
 *
 * <p>Aufruf: {@code java com.example.sonargraph.rap.LaunchBundleGraph [port]}.
 * Die Bundle-Hierarchie stammt aus {@code dependencyrules2.xml}
 * (Layer-Schachtelung). Die tatsächlichen Code-Dependencies werden
 * aus der {@code bundleDependencies.csv} im aktuellen Arbeitsverzeichnis
 * gelesen und in eine {@code List<BundleDependencyRecord>} umgewandelt
 * &mdash; perspektivisch ersetzt eine Cypher-Query den CSV-Reader.</p>
 *
 * <p>System-Properties:
 * <ul>
 *   <li>{@code bundle.deps.csv} &mdash; Pfad zur
 *       {@code bundleDependencies.csv} (Default: aktuelles Verzeichnis)</li>
 *   <li>{@code bundle.rules.xml} &mdash; Pfad zur
 *       {@code dependencyrules2.xml} (Default: aktuelles Verzeichnis)</li>
 * </ul></p>
 */
public final class LaunchBundleGraph {

    public static void main(String[] args) throws Exception {
        int port = 8085;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Ungültiger Port, verwende Standard-Port 8085.");
            }
        }

        // Datenquellen auflösen: bundleDependencies.csv + dependencyrules2.xml.
        Path depsPath = resolveDepsPath();
        Path rulesPath = resolveRulesPath();
        System.out.println("Lese Dependency-Records aus " + depsPath + " …");
        List<BundleDependencyRecord> deps = Files.exists(depsPath)
                ? BundleDependencyRecordCsvReader.read(depsPath)
                : List.of();
        System.out.printf("Records: %d%n", deps.size());

        // Dependency-Records vor dem Start an die Application übergeben
        // (RAP-Entry-Points haben einen parameterlosen Konstruktor, daher
        // erfolgt die Datenübergabe über einen statischen Setter).
        BundleDependencyApplication.setBundleDependencies(deps);

        // Vorab: Bundle-Modell aufbauen und in Datei schreiben.
        // Dadurch ist die JSON-Datei bereits verfügbar, sobald der Browser
        // die fetch-Anfrage stellt — unabhängig davon, ob der Entry-Point
        // schon initialisiert wurde.
        System.out.println("Lade Bundle-Modell …");
        try {
            var model = BundleDependencyApplication.loadBundleModel(rulesPath, deps);
            var rules = BundleDependencyApplication.loadRules(rulesPath);
            BundleModelFileStore.writeModel(model, rules, deps);
        } catch (Exception ex) {
            System.err.println("Fehler beim Vorab-Laden des Modells: " + ex.getMessage());
        }

        Server server = new Server(port);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // RWT benötigt ein temporäres Verzeichnis auf der Festplatte.
        File tempDir = new File("target/tmp/rap-rwt-context");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        ResourceFactory resourceFactory = ResourceFactory.of(context);
        Resource baseResource = resourceFactory.newResource(tempDir.getAbsolutePath());
        context.setBaseResource(baseResource);
        context.setAttribute("resource_root_location", tempDir.getAbsolutePath());
        context.setAttribute("jakarta.servlet.context.tempdir", tempDir);

        // RWT-Listener und Anwendungskonfiguration.
        context.addEventListener(new RWTServletContextListener());
        context.setInitParameter("org.eclipse.rap.applicationConfiguration",
                UnifiedRapApplication.class.getName());

        // Modell-JSON-Servlet (exakter Pfad hat Vorrang vor RWT-Servlets)
        context.addServlet(new ServletHolder("modelJson", new ModelJsonServlet()),
                BundleDependencyWidget.MODEL_URL);

        // Zwei RWT-Servlets an den Entry-Point-Pfaden.
        context.addServlet(new ServletHolder("rwtGraph", new RWTServlet()), "/graph");
        context.addServlet(new ServletHolder("rwtBundleGraph", new RWTServlet()), "/bundleGraph");

        // Default-Servlet für statische Ressourcen
        context.addServlet(new ServletHolder("default", org.eclipse.jetty.ee10.servlet.DefaultServlet.class), "/");

        server.setHandler(context);

        System.out.println("Starte eingebetteten Jetty-Server auf Port " + port + "...");
        server.start();
        System.out.println("RAP-Anwendungen gestartet!");
        System.out.println("Dependency Graph:    http://localhost:" + port + "/graph");
        System.out.println("Bundle Dependency:   http://localhost:" + port + "/bundleGraph");
        System.out.println("Arbeitsverzeichnis:   " + new File(".").getAbsolutePath());

        server.join();
    }

    private static Path resolveDepsPath() {
        String custom = System.getProperty("bundle.deps.csv");
        if (custom != null && !custom.isEmpty()) return Path.of(custom);
        return Path.of("bundleDependencies.csv");
    }

    private static Path resolveRulesPath() {
        String custom = System.getProperty("bundle.rules.xml");
        if (custom != null && !custom.isEmpty()) return Path.of(custom);
        return Path.of("dependencyrules2.xml");
    }
}
