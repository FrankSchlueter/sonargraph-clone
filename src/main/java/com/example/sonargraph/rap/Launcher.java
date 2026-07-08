package com.example.sonargraph.rap;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.rap.rwt.engine.RWTServlet;
import org.eclipse.rap.rwt.engine.RWTServletContextListener;

/**
 * Programmatischer Launcher für die RAP-Anwendung.
 * Startet einen eingebetteten Jetty-Server auf Port 8080.
 *
 * <p>Aufruf: {@code java com.example.sonargraph.rap.Launcher}
 */
public final class Launcher {

    public static void main(String[] args) throws Exception {
        int port = 8085;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Ungültiger Port, verwende Standard-Port 8080.");
            }
        }

        Server server = new Server(port);

        // Erstelle den Servlet-Context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // RWT benötigt ein temporäres Verzeichnis auf der Festplatte, um Web-Ressourcen (JS, CSS, Bilder) abzulegen.
        // Ohne dieses Verzeichnis wirft RAP beim Starten eine NullPointerException bezüglich 'contextDirectory'.
        java.io.File tempDir = new java.io.File("target/tmp/rap-rwt-context");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        ResourceFactory resourceFactory = ResourceFactory.of(context);
        Resource baseResource = resourceFactory.newResource(tempDir.getAbsolutePath());
        context.setBaseResource(baseResource);
        context.setAttribute("resource_root_location", tempDir.getAbsolutePath());
        context.setAttribute("jakarta.servlet.context.tempdir", tempDir);

        // Registriere RAP-Listener und Anwendungskonfiguration
        context.addEventListener(new RWTServletContextListener());
        context.setInitParameter("org.eclipse.rap.applicationConfiguration", DependencyGraphApplication.class.getName());

        // Registriere das RAP RWT Servlet
        ServletHolder servletHolder = new ServletHolder(new RWTServlet());
        servletHolder.setName("rwtServlet");
        context.addServlet(servletHolder, "/graph");

        // Registriere das Default-Servlet für statische Ressourcen
        ServletHolder defaultHolder = new ServletHolder("default", org.eclipse.jetty.ee10.servlet.DefaultServlet.class);
        context.addServlet(defaultHolder, "/");

        server.setHandler(context);

        System.out.println("Starte eingebetteten Jetty-Server auf Port " + port + "...");
        server.start();
        System.out.println("RAP-Anwendung gestartet!");
        System.out.println("Öffne: http://localhost:" + port + "/graph");
        
        server.join();
    }
}
