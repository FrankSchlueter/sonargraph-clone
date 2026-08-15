# RAP 4.4.0 mit Jetty 12.0.10 — Skill

Diese Datei ist als **Skill** für eine KI gedacht, die ein Eclipse-RAP-Projekt
(Eclipse Remote Application Platform) auf Basis von RWT (RAP Widget Toolkit)
mit eingebettetem Jetty 12 aufsetzen oder erweitern soll. Sie beschreibt
die Architektur, die Konventionen und liefert ein lauffähiges
Minimal-Template (pom + Application + Launcher).

---

## 1. Was ist RAP / RWT?

- **RAP** ist das Server-seitige Pendant zu RCP: Single-Page-Webanwendungen,
  die SWT/Java-Programmiermodell nutzen. SWT-Widgets werden serverseitig
  gerendert, der Client ist ein Browser.
- **RWT** ist das Runtime-Webframework hinter RAP. Es bringt den
  `RWTServlet`, einen `ApplicationConfiguration`-SPI und ein
  Session-/UI-Thread-Modell mit.
- Der **EntryPoint** ist das Gegenstück zu RCPs `IApplication`/`WorkbenchWindow`:
  ein parameterloser Konstruktor + `createContents(Composite)`-Methode, die
  pro Browser-Session einmal aufgerufen wird.

RAP 4.x ist modular, aber für ein einzelnes, server-seitig eingebettetes
RWT reicht ein einziges Artefakt: `org.eclipse.rap.rwt`.

---

## 2. Architektur-Überblick

```
┌────────────────────────────────────────────────────────────┐
│ Launcher (main, embedded Jetty 12)                         │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ ServletContextHandler ("/")                          │  │
│  │   ├─ temp dir:  target/tmp/rap-rwt-context           │  │
│  │   ├─ event:     RWTServletContextListener             │  │
│  │   ├─ init:      org.eclipse.rap.applicationConfiguration│  │
│  │   │              → UnifiedApp implements ApplicationConfig │
│  │   ├─ servlet:  /model.json  → ModelJsonServlet (HTTP)│  │
│  │   ├─ servlet:  /graph       → RWTServlet → EntryPoint │  │
│  │   ├─ servlet:  /bundleGraph  → RWTServlet → EntryPoint│  │
│  │   └─ servlet:  /            → DefaultServlet (statics) │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌─────────────────────────────┐
              │ Application (per RWT)        │
              │  - ApplicationConfiguration  │
              │  - addEntryPoint(path, …)     │
              │  - setOperationMode(SWT_COMPAT)│
              └─────────────────────────────┘
                              │
                              ▼
              ┌─────────────────────────────┐
              │ EntryPoint (per Browser)     │
              │  - AbstractEntryPoint        │
              │  - createContents(Composite)  │
              │    (SWT-Widgets hier anlegen) │
              └─────────────────────────────┘
```

Wichtige Konventionen:

- **Eine `ApplicationConfiguration` pro RWT-Servlet-Context.** Wenn du
  mehrere Entry-Points unter demselben Kontext laufen lassen willst
  (`/graph` UND `/bundleGraph` unter `LaunchBundleGraph`), registrierst du
  sie in *einer* `Application`-Klasse.
- **EntryPoint-Klassen müssen einen parameterlosen Konstruktor haben.**
  Wenn du Daten in den Entry-Point reichen willst, übergib sie **vor**
  `server.start()` über statische Setter (z. B. `BundleDependencyApplication.
  setBundleDependencies(deps)`) oder schreibe sie in eine Datei, die der
  Entry-Point dann per Classpath liest.
- **Pfade werden mit führendem Slash registriert**:
  `application.addEntryPoint("/graph", GraphEntryPoint.class, null)`.
- **OperationMode** muss auf `SWT_COMPATIBILITY` stehen, damit
  `Composite`/`Shell` aus dem klassischen SWT funktionieren.

---

## 3. Maven-Konfiguration (pom.xml)

Das nachfolgende Template ist das **Minimum** für ein lauffähiges
RAP-Setup mit eingebettetem Jetty 12. Es verwendet **kein** WAR-Deployment
und **keine** `web.xml` — die Anwendung ist rein `main()`-getrieben.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                              http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>rap-app</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <rap.version>4.4.0</rap.version>
    </properties>

    <dependencies>
        <!-- RAP / RWT runtime -->
        <dependency>
            <groupId>org.eclipse.rap</groupId>
            <artifactId>org.eclipse.rap.rwt</artifactId>
            <version>${rap.version}</version>
        </dependency>

        <!-- Servlet API (provided, weil Jetty sie beisteuert) -->
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <version>6.0.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- Eingebetteter Jetty 12 (EE10 = jakarta.servlet 6) -->
        <dependency>
            <groupId>org.eclipse.jetty</groupId>
            <artifactId>jetty-server</artifactId>
            <version>12.0.10</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.eclipse.jetty.ee10</groupId>
            <artifactId>jetty-ee10-servlet</artifactId>
            <version>12.0.10</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.6.3</version>
                <configuration>
                    <classpathScope>runtime</classpathScope>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Start (Programm-Args: `[port]`):

```bash
mvn -q -DskipTests compile exec:java \
    -Dexec.mainClass=com.example.rap.Launcher
```

---

## 4. Application + EntryPoint (eine Anwendung, ein EntryPoint)

`ApplicationConfiguration` registriert den EntryPoint und setzt den
OperationMode. `AbstractEntryPoint` enthält die SWT-UI.

```java
package com.example.rap;

import org.eclipse.rap.rwt.application.AbstractEntryPoint;
import org.eclipse.rap.rwt.application.Application;
import org.eclipse.rap.rwt.application.ApplicationConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Eine einzelne RAP-Anwendung mit einem EntryPoint an {@code /}.
 *
 * Wird im Launcher referenziert via
 *   context.setInitParameter("org.eclipse.rap.applicationConfiguration",
 *                           SimpleApp.class.getName());
 */
public class SimpleApp implements ApplicationConfiguration {

    public SimpleApp() {
        System.out.println("SimpleApp gestartet");
    }

    @Override
    public void configure(Application application) {
        application.addEntryPoint("/", SimpleEntryPoint.class, null);
        application.setOperationMode(Application.OperationMode.SWT_COMPATIBILITY);
    }

    /** SWT-UI: pro Browser-Session einmal instanziiert. */
    public static class SimpleEntryPoint extends AbstractEntryPoint {

        @Override
        protected void createContents(Composite parent) {
            parent.setLayout(new FillLayout());
            Shell shell = parent.getShell();
            shell.setText("Simple RAP App");

            Label label = new Label(parent, SWT.NONE);
            label.setText("Hallo aus RAP " + System.getProperty("rap.version") + "!");
        }
    }
}
```

---

## 5. Launcher mit ZWEI Entry-Points (zwei Anwendungen im selben Server)

Wenn du **mehrere Entry-Points** unter demselben RWT-Servlet-Context
ausliefern willst (z. B. `/graph` und `/bundleGraph`), registrierst du sie
in *einer* `Application`-Klasse und mapst beide Pfade auf
`org.eclipse.rap.rwt.engine.RWTServlet`. Genau das macht der folgende
`Launcher` für eine `UnifiedApp` mit zwei Entry-Points:

```java
package com.example.rap;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.rap.rwt.engine.RWTServlet;
import org.eclipse.rap.rwt.engine.RWTServletContextListener;

import java.io.File;

/**
 * Programmatischer Launcher für eine RAP-Anwendung mit zwei Entry-Points
 * auf demselben eingebetteten Jetty-Server.
 *
 * <ul>
 *   <li>{@code /app1} &mdash; Application 1 (DemoApp)</li>
 *   <li>{@code /app2} &mdash; Application 2 (DemoApp2)</li>
 * </ul>
 *
 * Aufruf: {@code java com.example.rap.Launcher [port]}.
 */
public final class Launcher {

    public static void main(String[] args) throws Exception {
        int port = 8085;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) {
                System.err.println("Ungültiger Port, verwende Standard-Port 8085.");
            }
        }

        Server server = new Server(port);
        ServletContextHandler context =
                new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // RWT braucht ein temporäres Verzeichnis für Session-Daten.
        File tempDir = new File("target/tmp/rap-rwt-context");
        if (!tempDir.exists()) tempDir.mkdirs();
        ResourceFactory resourceFactory = ResourceFactory.of(context);
        Resource baseResource = resourceFactory.newResource(tempDir.getAbsolutePath());
        context.setBaseResource(baseResource);
        context.setAttribute("resource_root_location", tempDir.getAbsolutePath());
        context.setAttribute("jakarta.servlet.context.tempdir", tempDir);

        // RWT-Listener + zentrale ApplicationConfiguration.
        context.addEventListener(new RWTServletContextListener());
        context.setInitParameter(
                "org.eclipse.rap.applicationConfiguration",
                UnifiedApp.class.getName());

        // Zwei RWT-Servlets — jeder bekommt seinen eigenen EntryPoint.
        context.addServlet(new ServletHolder("rwtApp1", new RWTServlet()), "/app1");
        context.addServlet(new ServletHolder("rwtApp2", new RWTServlet()), "/app2");

        // Statische Ressourcen.
        context.addServlet(
                new ServletHolder("default",
                        org.eclipse.jetty.ee10.servlet.DefaultServlet.class),
                "/");

        server.setHandler(context);
        System.out.println("Starte eingebetteten Jetty auf Port " + port + " …");
        server.start();
        System.out.println("RAP-Anwendungen gestartet!");
        System.out.println("App1:    http://localhost:" + port + "/app1");
        System.out.println("App2:    http://localhost:" + port + "/app2");

        server.join();
    }
}
```

Die zugehörige `UnifiedApp`, die **beide** Entry-Points in einem
Servlet-Context registriert:

```java
package com.example.rap;

import org.eclipse.rap.rwt.application.AbstractEntryPoint;
import org.eclipse.rap.rwt.application.Application;
import org.eclipse.rap.rwt.application.ApplicationConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Vereint beide Entry-Points unter einem RWT-Servlet-Context.
 *
 * Notwendig, weil RWT die {@code applicationConfiguration} als
 * <em>Context</em>-Init-Parameter erwartet — pro Servlet-Context
 * lässt sich nur eine Konfiguration registrieren. Diese Klasse delegiert
 * an die EntryPoints der beiden DemoApps.
 */
public class UnifiedApp implements ApplicationConfiguration {

    public UnifiedApp() {
        System.out.println("UnifiedApp gestartet");
    }

    @Override
    public void configure(Application application) {
        application.addEntryPoint("/app1", DemoApp.EntryPoint.class, null);
        application.addEntryPoint("/app2", DemoApp2.EntryPoint.class, null);
        application.setOperationMode(Application.OperationMode.SWT_COMPATIBILITY);
    }
}

/** Erste Mini-Anwendung. */
class DemoApp implements ApplicationConfiguration {

    public static class EntryPoint extends AbstractEntryPoint {
        @Override
        protected void createContents(Composite parent) {
            parent.setLayout(new FillLayout());
            parent.getShell().setText("Demo App 1");
            new Label(parent, SWT.NONE).setText("Willkommen in App 1");
        }
    }
}

/** Zweite Mini-Anwendung. */
class DemoApp2 implements ApplicationConfiguration {

    public static class EntryPoint extends AbstractEntryPoint {
        @Override
        protected void createContents(Composite parent) {
            parent.setLayout(new FillLayout());
            parent.getShell().setText("Demo App 2");
            new Label(parent, SWT.NONE).setText("Willkommen in App 2");
        }
    }
}
```

---

## 6. Konventionen & Best Practices

### EntryPoint
- **Parameterloser Konstruktor** ist Pflicht (RAP-Reflection). Daten
  müssen vorher via statische Setter oder in eine Datei geschrieben
  werden.
- **`createContents(Composite parent)`** ist der Einstiegspunkt. Hier
  werden SWT-Widgets instanziert.
- Niemals langlaufende Operationen in `createContents` — der
  UI-Thread blockiert sonst für alle anderen Sessions.

### ApplicationConfiguration
- Setze **immer** `application.setOperationMode(
  Application.OperationMode.SWT_COMPATIBILITY)`, sonst verhalten sich
  einige SWT-Widgets ungewohnt (z. B. `Browser`).
- Verwende **eine** `ApplicationConfiguration` pro Servlet-Context.

### Launcher
- Lege das `tempDir` an **bevor** der `RWTServletContextListener`
  registriert wird.
- Setze sowohl `resource_root_location` als auch
  `jakarta.servlet.context.tempdir` auf das `tempDir` — verschiedene
  RWT-Versionen greifen auf unterschiedliche Attribute zu.
- Setze `applicationConfiguration` als Init-Parameter **am Context**,
  nicht am Servlet.
- Reihenfolge der Servlet-Registrierungen ist relevant: exakte Pfade
  (z. B. `/api/data.json`) müssen **vor** den RWT-Servlets registriert
  werden, sonst fängt RWT alle Requests ab.

### Datenübergabe an EntryPoints
Da EntryPoints parameterlose Konstruktoren haben, gibt es drei
gängige Muster:

1. **Statische Setter** im Application-Companion:
   `BundleDependencyApplication.setBundleDependencies(deps)`.
2. **Datei auf der Platte**, die der EntryPoint beim Start liest (z. B.
   `target/tmp/model.json`). Das ist robust gegen
   Re-Deployments, weil der Launcher die Datei vor `server.start()`
   schreiben kann.
3. **Servlet-Context-Attribute**, die in `configure()` gesetzt und in
   `createContents()` gelesen werden.

### Fehlersuche
- Wenn der Browser "503 Service Unavailable" zeigt, fehlt meistens
  `resource_root_location` oder `jakarta.servlet.context.tempdir`
  im ServletContext.
- Wenn der Browser hängt, prüfe den UI-Thread — ein blockierender
  Call in `createContents` legt alle Sessions lahm.
- Console-Output aus dem Server landet auf stdout — `System.out.println`
  in `createContents` ist erlaubt und nützlich.

---

## 7. Häufige Imports

```java
// RWT / RAP
import org.eclipse.rap.rwt.application.AbstractEntryPoint;
import org.eclipse.rap.rwt.application.Application;
import org.eclipse.rap.rwt.application.ApplicationConfiguration;
import org.eclipse.rap.rwt.client.WebClient;
import org.eclipse.rap.rwt.engine.RWTServlet;
import org.eclipse.rap.rwt.engine.RWTServletContextListener;

// SWT (klassisch, mit OperationMode SWT_COMPATIBILITY)
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

// Jetty 12 (EE10 = jakarta.servlet 6)
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
```

---

## 8. Schnellreferenz

| Was                              | Wo                                       |
| -------------------------------- | ---------------------------------------- |
| Einstiegspunkt pro Browser-Session | `AbstractEntryPoint.createContents`      |
| EntryPoint-Registrierung          | `application.addEntryPoint("/path", …)`  |
| Application-Setup                 | `implements ApplicationConfiguration`    |
| RWT-Servlet (Servlet-Context)    | `new RWTServlet()`                       |
| Init-Parameter                    | `org.eclipse.rap.applicationConfiguration` |
| Embedded Jetty 12 EE10           | `ServletContextHandler` + `RWTServletContextListener` |
| Statische Ressourcen              | `DefaultServlet` an `/` mappen           |
| HTTP-Servlet (z. B. JSON-API)    | `new ServletHolder(name, new …())`       |
| Session-Temp-Verzeichnis          | `context.setAttribute("jakarta.servlet.context.tempdir", file)` |
| Resource-Base                     | `context.setBaseResource(resourceFactory.newResource(path))` |
