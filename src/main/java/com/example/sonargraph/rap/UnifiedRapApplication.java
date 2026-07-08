package com.example.sonargraph.rap;

import org.eclipse.rap.rwt.application.AbstractEntryPoint;
import org.eclipse.rap.rwt.application.Application;
import org.eclipse.rap.rwt.application.ApplicationConfiguration;

import org.eclipse.rap.rwt.internal.application.ApplicationImpl;
import jakarta.servlet.ServletContext;

import java.io.File;

/**
 * Vereint beide RAP-Anwendungen unter einem Servlet-Context:
 * <ul>
 *   <li>{@code /graph} &mdash; Sonargraph-Architecture-View (Beispielmodell)</li>
 *   <li>{@code /bundleGraph} &mdash; Bundle-Dependency-View (CSV-basiert)</li>
 * </ul>
 *
 * <p>Notwendig, weil RWT die {@code applicationConfiguration} als
 * <em>Context</em>-Init-Parameter erwartet &mdash; pro Servlet-Context
 * lässt sich nur eine Konfiguration registrieren. Diese Klasse delegiert
 * an die Entry-Points der bestehenden Application-Klassen.
 */
public class UnifiedRapApplication implements ApplicationConfiguration {

    @Override
    public void configure(Application application) {
        if (application instanceof ApplicationImpl impl) {
            ServletContext context = impl.getApplicationContext().getServletContext();
            if (context != null && context.getAttribute("resource_root_location") == null) {
                File tempDir = new File("target/tmp/rap-rwt-context");
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }
                context.setAttribute("resource_root_location", tempDir.getAbsolutePath());
            }
        }

        // Beide Entry-Points unter dem gleichen Application-Objekt registrieren
        application.addEntryPoint("/graph",       DependencyGraphApplication.GraphEntryPoint.class, null);
        application.addEntryPoint("/bundleGraph", BundleDependencyApplication.BundleEntryPoint.class, null);
        application.setOperationMode(Application.OperationMode.SWT_COMPATIBILITY);
    }
}
