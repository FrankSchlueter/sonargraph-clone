package com.example.sonargraph.rap;

import com.example.sonargraph.bundle.BundleDependencyRecord;
import com.example.sonargraph.bundle.BundleModel;
import com.example.sonargraph.bundle.BundleModelBuilder;
import com.example.sonargraph.deprules.DependencyRulesService;
import org.eclipse.rap.rwt.application.AbstractEntryPoint;
import org.eclipse.rap.rwt.application.Application;
import org.eclipse.rap.rwt.application.ApplicationConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * RAP-Entry-Point, der den Bundle-Dependency-Graphen darstellt.
 *
 * <p>Liest die {@code dependencyrules2.xml} und eine Liste von
 * {@link BundleDependencyRecord} (vom Launcher per
 * {@link #setBundleDependencies(List)} gesetzt) und stellt das
 * resultierende Modell unter {@code /bundleGraph} bereit.</p>
 *
 * <p>Die Hierarchie der Bundles wird vollständig aus der Layer-
 * Schachtelung der XML-Datei abgeleitet. Die im EntryPoint
 * angezeigten Code-Dependencies stammen aus den übergebenen
 * Dependency-Records (heute aus einer CSV, künftig aus einer
 * Cypher-Query).</p>
 */
public class BundleDependencyApplication implements ApplicationConfiguration {

    /**
     * Vom Launcher vor {@code server.start()} gesetzte Liste der
     * tatsächlichen Code-Dependencies. Wird im Entry-Point gelesen.
     */
    private static volatile List<BundleDependencyRecord> SHARED_DEPS = List.of();

    public static void setBundleDependencies(List<BundleDependencyRecord> deps) {
        SHARED_DEPS = List.copyOf(deps);
    }

    public static List<BundleDependencyRecord> getBundleDependencies() {
        return SHARED_DEPS;
    }

    public BundleDependencyApplication() {
        System.out.println("BundleDependencyApplication gestartet");
    }

    @Override
    public void configure(Application application) {
        if (application instanceof org.eclipse.rap.rwt.internal.application.ApplicationImpl) {
            org.eclipse.rap.rwt.internal.application.ApplicationImpl impl =
                (org.eclipse.rap.rwt.internal.application.ApplicationImpl) application;
            jakarta.servlet.ServletContext context = impl.getApplicationContext().getServletContext();
            if (context != null && context.getAttribute("resource_root_location") == null) {
                java.io.File tempDir = new java.io.File("target/tmp/rap-rwt-context");
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }
                context.setAttribute("resource_root_location", tempDir.getAbsolutePath());
            }
        }

        application.addEntryPoint("/bundleGraph", BundleEntryPoint.class, null);
        application.setOperationMode(Application.OperationMode.SWT_COMPATIBILITY);
    }

    public static class BundleEntryPoint extends AbstractEntryPoint {

        @Override
        protected void createContents(Composite parent) {
            parent.setLayout(new FillLayout());
            Shell shell = parent.getShell();
            shell.setText("Bundle Dependency Graph");

            List<BundleDependencyRecord> deps = getBundleDependencies();
            Path rulesPath = resolveRulesPath();

            DependencyRulesService rules = null;
            if (rulesPath != null) {
                try {
                    long t0 = System.currentTimeMillis();
                    rules = DependencyRulesService.fromXml(rulesPath);
                    long t1 = System.currentTimeMillis();
                    System.out.printf(java.util.Locale.ROOT,
                            "[BundleEntryPoint] Allowed-Deps synchron geladen in %d ms (%d Bundles)%n",
                            (t1 - t0), rules.knownBundleNames().size());
                } catch (Exception ex) {
                    System.err.println("[BundleEntryPoint] Konnte dependencyrules2.xml nicht laden: " + ex.getMessage());
                }
            }

            BundleModel model = loadBundleModel(rulesPath, deps);
            BundleDependencyWidget widget = new BundleDependencyWidget(parent, model, rules, deps);
            widget.setOnBundleClick(id ->
                    System.out.println("[Bundle] open " + id));
            widget.setOnEdgeClick((from, to, weight) ->
                    System.out.printf("[Edge] %s -> %s  (cardinality=%d)%n", from, to, weight));
            widget.setOnBundleSelect((id, kind, selected) ->
                    System.out.printf("[BundleSelect] id=%s, kind=%s, selected=%b%n",
                            id, kind, selected));
            widget.setOnDependency((isConsumer, fromBundle, cardinality, toBundle) ->
                    System.out.printf("[DependencyCallback] isConsumer=%b, fromBundle=%s, cardinality=%d, toBundle=%s%n",
                            isConsumer, fromBundle, cardinality, toBundle));
        }
    }

    /**
     * Lädt das Bundle-Modell aus dem übergebenen Rules-Pfad und den
     * Dependency-Records. Die Hierarchie kommt vollständig aus der
     * {@code <layer>}-Schachtelung der XML-Datei.
     */
    public static BundleModel loadBundleModel(Path rulesPath,
                                              List<BundleDependencyRecord> deps) {
        DependencyRulesService rules = loadRules(rulesPath);
        if (rules == null) {
            System.out.println("WARN: Keine dependencyrules2.xml — Modell ohne Hierarchie");
        }
        BundleModel model = new BundleModelBuilder(rules, deps).build();
        System.out.printf(Locale.ROOT,
                "Modell: %d Bundles, %d Dependencies geladen%n",
                countLeaves(model), deps.size());
        return model;
    }

    /**
     * Lädt die {@link DependencyRulesService} aus dem gegebenen Pfad
     * (oder {@code null}, wenn die Datei fehlt).
     */
    public static DependencyRulesService loadRules(Path rulesPath) {
        if (rulesPath == null || !Files.exists(rulesPath)) return null;
        try {
            return DependencyRulesService.fromXml(rulesPath);
        } catch (Exception ex) {
            System.err.println("[BundleDependencyApplication] Konnte dependencyrules2.xml nicht laden: " + ex.getMessage());
            return null;
        }
    }

    private static int countLeaves(BundleModel model) {
        int n = 0;
        for (var a : model.artifactModel().artifacts()) {
            if (!a.id().startsWith("bundle:fold:") && !a.id().equals("bundle:root")) n++;
        }
        return n;
    }

    /**
     * Liefert den Pfad zur {@code dependencyrules2.xml}. Default ist
     * {@code dependencyrules2.xml} im aktuellen Verzeichnis; überschreibbar
     * via System-Property {@code bundle.rules.xml}.
     */
    public static Path resolveRulesPath() {
        String custom = System.getProperty("bundle.rules.xml");
        if (custom != null && !custom.isEmpty()) return Path.of(custom);
        return Path.of("dependencyrules2.xml");
    }
}
