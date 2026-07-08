package com.example.sonargraph.rap;

import com.example.sonargraph.example.ExampleModelGenerator;
import com.example.sonargraph.model.ArtifactModel;

import org.eclipse.rap.rwt.application.AbstractEntryPoint;
import org.eclipse.rap.rwt.application.Application;
import org.eclipse.rap.rwt.application.ApplicationConfiguration;
import org.eclipse.rap.rwt.client.WebClient;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

/**
 * RAP-Entry-Point, der den Dependency-Graphen darstellt.
 *
 * <p>Konfiguration in {@link ApplicationConfiguration} &mdash; beides
 * ist in der gleichen Klasse gehalten, weil RAP-Anwendungen üblicherweise
 * ein zentrales {@code ApplicationConfig} und einen {@code EntryPoint}
 * haben.
 */
public class DependencyGraphApplication implements ApplicationConfiguration {

	public DependencyGraphApplication() {
		System.out.println("DependencyGraphApplication gestartet");
	}
	
    @Override
    public void configure(Application application) {
        // Sicherstellen, dass das Ressourcen-Verzeichnis gesetzt ist, um 503-Fehler
        // beim Starten über den Eclipse RWT-Launcher zu verhindern.
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

        application.addEntryPoint("/graph", GraphEntryPoint.class, null);
        // Default-URL auf unseren Entry-Point zeigen lassen:
        application.setOperationMode(Application.OperationMode.SWT_COMPATIBILITY);
    }

    /** Beispiel-Entry-Point — eigenständige RAP-Anwendung. */
    public static class GraphEntryPoint extends AbstractEntryPoint {

        @Override
        protected void createContents(Composite parent) {
            parent.setLayout(new FillLayout());
            Shell shell = parent.getShell();
            shell.setText("Dependency Graph — Sonargraph-style");

            ArtifactModel model = ExampleModelGenerator.generate();
            ExampleModelGenerator.printSummary(model);

            DependencyGraphWidget widget = new DependencyGraphWidget(parent, model);
            widget.setOnEdgeClick((from, to, weight, isViolation) ->
                    System.out.printf("[Edge] %s -> %s  (w=%d, %s)%n",
                            from, to, weight, isViolation ? "VIOLATION" : "allowed"));
            widget.setOnArtifactClick(id ->
                    System.out.println("[Artifact] open " + id));
            widget.setOnDependencySelect((id, kind, selected) ->
                    System.out.printf("[DependencySelect] id=%s, kind=%s, selected=%b%n",
                            id, kind, selected));
        }
    }
}