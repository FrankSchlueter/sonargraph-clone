# Sonargraph Architecture View — Clone

Java-Projekt zur Visualisierung von Abhängigkeitsgraphen im Stile von
**Sonargraph Architecture View**. Die Artefakte werden in einer Spalte
hierarchisch geschichtet dargestellt; rechts zeichnen grüne gerundete
Verbindungen erlaubte Verwendungen (oben → unten), links rote
Verbindungen als Violations (unten → oben). Visualisiert wird in einem
**Eclipse RWT Browser-Widget**; Klicks auf Verbindungen und Artefakt-
Links lösen Java-Callbacks aus.

## Neue Features & Optimierungen

* **Kummulierte Dependencies der Root-Artefakte**:
  Die Abhängigkeiten der Root-Artefakte (Packages) fassen die Abhängigkeiten aller Sub-Artefakte (Klassen), die auf dasselbe Ziel-Artefakt verweisen, zu einer kummulierten Dependency mit summiertem Gewicht zusammen. Dies ist sowohl im Java-Modell (`ArtifactModel`) als auch in der JavaScript-Visualisierung implementiert.
* **Präzises Selektionsverhalten**:
  Bei der Selektion eines Root-Artefaktes (z. B. Klick auf dessen Dependency-Node) werden dessen Child-Artefakte nicht mehr mit selektiert oder als aktive Quelle visualisiert. Die Kanten entspringen sauber dem ausgewählten Root-Element.
* **Flackerfreie Custom-Tooltips (Dual-Path-Rendering)**:
  Ein moderner HTML-Tooltip mit Dark-Mode-Glassmorphismus (`backdrop-filter`) ersetzt die trägen nativen Browser-Tooltips. Jede Verbindung besitzt einen breiten, unsichtbaren Hover-Kanal (`12px`), welcher Mausereignisse abfängt. Dies verhindert jegliches Zappeln und Flickern der Tooltips bei minimalen Mausbewegungen.
* **Erweiterte Badges**:
  Die Allowed-, Violation- und Usage-Badges zeigen bei Mouseover detaillierte Beschreibungen per Custom-Tooltip an.
* **Allowed Dependencies aus `dependencyrules2.xml`**:
  Rechtsklick auf ein Bundle öffnet ein Kontextmenü mit den Einträgen *Allowed Dependencies* (rechte Spalte), *Allowed Consumers* (linke Spalte) und *Used Dependencies*. Die Sicht wird **synchron** aus der XML berechnet — kein Lade-Warten, keine UI-Verzögerung. Im Allowed-Modus zeigt die jeweilige Spalte den erlaubten Klassenpfad aus `dependencyrules2.xml` als hierarchischen Baum in der Reihenfolge der Top-Layer; ausgeschlossene Einträge sind rot und kursiv mit einem Tooltip, der den exakten Regeltext (ohne `<exclude>`-Markup) und die Zeilennummer in der XML enthält. Impl-Bundles, die nur für das eigene Produkt sichtbar sind, werden in **lila und kursiv** mit dem Tooltip `Verboten wegen Impl Category` dargestellt. Es existiert ein Standalone-Exporter (`BundleAllowedExporter`) für die Verwendung ohne RAP-Server.

## Aufbau

```
src/main/java/com/example/sonargraph/
├── Main.java                         # Standalone-HTML-Exporter (ohne RAP)
├── model/
│   ├── Artifact.java                 # Knoten (id, name, layer, parentId)
│   ├── Dependency.java               # gerichtete Kante (from, to, weight)
│   └── ArtifactModel.java            # Knoten + Kanten + Adjazenz-Indizes (inkl. Kummulierung)
├── sort/
│   └── DsmSorter.java                # Tarjan SCC + topo + coupling
├── example/
│   └── ExampleModelGenerator.java    # 100 Knoten, 15k Edges, 1k Violations
├── bundle/                           # Bundle-Dependency-Graph (XML + CSV-basiert)
│   ├── BundleDependencyRecord.java   # Record (fromBundle, cardinality, toBundle)
│   ├── BundleDependencyRecordCsvReader.java  # RFC-4180-konformer CSV-Parser
│   ├── BundleModelBuilder.java       # Baut hierarchisches ArtifactModel inkl. Ordinal-Sortierung
│   └── BundleModel.java              # Wrapper um ArtifactModel + BundleInfo (Product, Category)
├── deprules/                         # Allowed-Dependencies aus dependencyrules2.xml
│   ├── BundleRuleEntry.java          # Bundle-Definition aus der XML (mit Zeilennummer)
│   ├── LayerNode.java                # Layer-Hierarchie
│   ├── Filter.java                   # <filter> mit include/exclude-Regeln
│   ├── AllowedDependency.java        # Einzelne erlaubte (oder ausgeschlossene) Dep
│   ├── DependencyRulesParser.java    # SAX-Parser mit Zeilen-Tracking
│   └── DependencyRulesService.java   # Klassenpfad-Algorithmus (Forward + Reverse)
├── view/
│   ├── JsonModelSerializer.java        # Modell → JSON für JS
│   ├── JsonBundleSerializer.java       # Legacy: BundleModel → JSON v1 (mit isPackage/product, allowedDeps/allowedConsumers)
│   ├── CompactJsonBundleSerializer.java # v2: BundleModel → JSON mit int-IDs, ~5x kleiner
│   └── HtmlExporter.java               # standalone-HTML mit Mock-Callbacks
├── BundleAllowedExporter.java        # Standalone-HTML mit eingebetteten Allowed-Deps
└── rap/
    ├── DependencyGraphWidget.java    # RWT Browser + BrowserFunction
    ├── DependencyGraphApplication.java# Entry-Point (URL: /graph)
    ├── BundleDependencyWidget.java   # RWT Browser + 4 BrowserFunctions
    ├── BundleDependencyApplication.java# Entry-Point (URL: /bundleGraph)
    ├── Launcher.java                 # Standalone-Launcher für /graph
    └── LaunchBundleGraph.java        # Unified-Launcher: /graph + /bundleGraph

src/main/resources/visualization/
├── graph.html                        # HTML + CSS + JS (Dependency-Graph)
└── bundlegraph.html                  # HTML + CSS + JS (Bundle-Dependency-Graph)

src/test/java/com/example/sonargraph/
├── DsmSorterTest.java                # Unit Tests (DSM + Kummulierungen)
├── ExampleModelGeneratorTest.java   # Unit Tests (Beispielmodell)
├── bundle/BundleModelBuilderTest.java # BundleModel-Tests
└── deprules/                         # Tests für Allowed-Dependencies
    ├── DependencyRulesParserTest.java
    └── DependencyRulesServiceTest.java
```

## Visualisierung (graph.html)

Layout (3-spaltig):

| Spalte            | Inhalt                                                |
| ----------------- | ----------------------------------------------------- |
| **Links**         | SVG-Overlay mit roten Bezier-Kurven (Violations)      |
| **Mitte**         | Artefakt-Spalte, hierarchisch (Packages + Klassen)     |
| **Rechts**        | SVG-Overlay mit grünen Bezier-Kurven (Allowed)        |

Die Artefaktnamen sind `<a>`-Links. Beim Klick wird
`window.__javaOnArtifactClick(id)` aufgerufen — in RAP greift die
zugehörige `BrowserFunction` dies ab und dispatcht an einen Java-Handler.

## DSM-Algorithmus

`DsmSorter` sortiert die Artefakte so, dass die DSM-Matrix (Dependency
Structure Matrix) möglichst kompakt wirkt:

1. **Tarjan SCC** — stark zusammenhängende Komponenten (= Zyklen)
   werden zu Blöcken zusammengefasst.
2. **Topologische Sortierung des SCC-DAG** — Senken (Module, von denen
   nichts abhängt) wandern nach unten in die Matrix, Quellen oben.
3. **Innerhalb-SCC nach Coupling** — Knoten mit vielen rein/raus-
   Beziehungen werden gruppiert.
4. **Layer als Tie-Break** — die hierarchische Schichtung bleibt
   visuell erhalten.

Komplexität: O(V + E) für SCC + Topo, O(V log V) für die Innen-
Sortierung.

## Build & Run

### Standalone-HTML (ohne RAP-Server)

```bash
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.example.sonargraph.Main
# → dependency-graph.html im aktuellen Verzeichnis
```

Die Datei kann in jedem modernen Browser geöffnet werden.

### In einer RAP-Anwendung

```java
ArtifactModel model = ExampleModelGenerator.generate();
DependencyGraphWidget widget = new DependencyGraphWidget(parent, model);
widget.setOnEdgeClick((from, to, weight, isViolation) -> {
    System.out.println("Kante " + from + " -> " + to + " (" + weight + ")");
});
widget.setOnArtifactClick(id -> System.out.println("Artefakt " + id));
```

In RAP registriert das Widget drei `BrowserFunction`s
(`javaOnEdgeClick`, `javaOnArtifactClick`, `javaOnDependencySelect`), die aus JavaScript
aufrufbar sind.

## Beispielmodell

| Eigenschaft             | Wert       |
| ----------------------- | ---------- |
| Artefakte               | 100        |
| Abhängigkeiten (gesamt) | ≥ 15 000   |
| davon Violations        | ≈ 1 000    |
| Schichten               | 5          |
| Packages pro Schicht    | 2          |
| Klassen pro Package     | 10         |

Schichten (oben → unten): `presentation`, `application`, `domain`,
`persistence`, `common`. Eine "Violation" liegt vor, wenn eine
Kante `from.layer <= to.layer` zeigt.

## Tests

```bash
mvn test
```

* `DsmSorterTest` — Top-down-Sort, Zyklen, gemischte SCCs, leeres Modell sowie Verifizierung der kummulierten Abhängigkeiten (`accumulatesDependenciesForRootArtifacts`)
* `ExampleModelGeneratorTest` — Modell-Generierung + JSON-Serialisierung
* `BundleModelBuilderTest` — Hierarchie-Aufbau aus XML-Layer-Schachtelung, Impl-Category, BundleDependencyRecord-CSV-Parser (inkl. BOM), Dedup
* `DependencyRulesParserTest` — XML-Parsing mit Zeilennummern, Kommentar-Behandlung, geschachtelte Layer
* `DependencyRulesServiceTest` — Klassenpfad-Algorithmus: Forward + Reverse, Include/Exclude-Semantik, Performance-Smoke-Test gegen die echte `dependencyrules2.xml`

## Bundle Dependency Application

Zweite RAP-Anwendung (`/bundleGraph`), die `dependencyrules2.xml` und
`bundleDependencies.csv` einliest und als hierarchischen Bundle-Graph
visualisiert. Die Hierarchie wird vollständig aus der
`<layer>`-Schachtelung der XML gewonnen — es wird **keine**
`bundles.csv` mehr benötigt.

### Eingabedateien

**`dependencyrules2.xml`** (alleinige Quelle für Hierarchie und Metadaten)

* `<product name="…"><layer name="…">…</layer></product>` — die
  Schachtelungstiefe der `<layer>`-Knoten bildet die Hierarchie des
  Graphen (Top-Layer = oberste Ebene, Sub-Layer = Kind-Folder, …).
* `<bundle name="…" category="api|impl" produkt="…"/>` — pro Bundle
  wird ein Leaf-Artifact angelegt. Optionale Attribute:
  * `category="impl"` (Default `api`) — markiert ein Bundle als
    implementierungsspezifisch. Impl-Bundles sind nur für Bundles
    **desselben** `produkt` sichtbar; aus Sicht anderer Produkte
    erscheinen sie als verboten (lila, kursiv, Tooltip
    `Verboten wegen Impl Category`).
  * `produkt="…"` — Produktzugehörigkeit. Impl-Bundles ohne
    `produkt` werden wie `api`-Bundles behandelt.
* `<exclude layer="…"/>` / `<include layer="…"/>` — bestimmen die
  erlaubten Abhängigkeiten (Klassenpfad-Berechnung).

**`bundleDependencies.csv`**

| Spalte | Bedeutung |
|---|---|
| `fromBundle` | Quell-Bundle (verwendet →) |
| `cardinality` | Anzahl der Code-Abhängigkeiten (Import, Type-Use, Method-Call) |
| `toBundle` | Ziel-Bundle (← wird verwendet) |

Wird heute eingelesen; perspektivisch ersetzt durch eine Cypher-Query
gegen einen Neo4j-Graphen (Schnittstelle `List<BundleDependencyRecord>`
bleibt unverändert). Doppelte `(from,to)`-Paare werden zu einer einzigen
Abhängigkeit mit aufsummierter Cardinality zusammengefasst.

### Verhalten

* **Drei-Spalten-Layout**:
  - **Mitte (Tree)**: Hierarchischer Bundle-Baum, gefiltert nach
    Root-Klassifikationen und Tree-Level. Bei Klick auf einen
    Bundle-Namen wird das Bundle selektiert.
  - **Links (Consumers)**: Sobald ein Bundle in der Mitte selektiert ist,
    zeigt die linke Spalte alle nutzenden Bundles als hierarchischen
    Tree nach gemeinsamer Classification. Jede Zeile zeigt die Anzahl
    der Dependencies. Über den Mode-Switch **Used | Allowed Consumers**
    kann zwischen den tatsächlichen Konsumenten und den nach
    `dependencyrules2.xml` erlaubten Konsumenten umgeschaltet werden.
  - **Rechts (Used | Allowed Dependencies)**: Ein Mode-Switch in der
    Kopfzeile der rechten Spalte erlaubt das Umschalten zwischen:
    - **Used**: alle tatsächlich genutzten Dependencies (aus
      `bundleDependencies.csv`).
    - **Allowed Dependencies**: alle nach `dependencyrules2.xml` im
      Klassenpfad erlaubten Bundles (siehe nächster Abschnitt).
* **Root-Klassifikations-Dropdown** im Header mit Checkboxen für jede
  Top-Level-Klassifikation (z. B. `DEV-APP`, `QS-FRAMEWORK`, `EXTERNAL`).
  Damit lassen sich ganze Subtrees ein- und ausblenden. Per Default
  sind `EXTERNAL`, `HOTSWAP`, `DEV-FRAMEWORK` und `QS-FRAMEWORK`
  ausgeblendet. Über die Links im Dropdown-Menü können alle
  ein-/ausgeblendet oder die Default-Sicht wiederhergestellt werden.
  Der Button zeigt jederzeit `(sichtbar/gesamt)` an.
* **Tree-Level-Dropdown**: Steuert die initiale maximale Tiefe des
  Baums in der Center-Spalte. Optionen: `Only Root Level 1` (nur
  Top-Level), `Level 2` (Root + 1. Child-Ebene), `All` (alle Ebenen).
  Wichtig: der Filter ist ein Initial-Limit — vom User explizit
  expandierte Folder zeigen ihre Children auch dann, wenn der Filter
  eigentlich eine tiefere Ebene verstecken würde. So kann man z. B. bei
  `Only Root Level 1` einen Top-Level-Folder per Klick aufklappen und
  dessen Children sichtbar machen.
* **Bundle-Reihen-Cardinality**: Jede Bundle-Zeile zeigt rechts zwei
  Badges: `↗ N` (Anzahl eingehender Dependencies) und `↘ N` (Anzahl
  ausgehender Dependencies).
* **Hierarchischer Baum**: Jede Stufe der `bundleClassification` bildet
  einen einklappbaren Folder-Knoten.
* **Tooltips**:
  - **Center** (Bundle): `Bundlname`, `Produkt`, `Abteilung`,
    `Anzahl Nutzer` (eingehend), `Genutzte Dependencies` (ausgehend).
  - **Links/Rechts** (Side-Tree): `<N> Dependencies von <Quelle> nach <Ziel>`,
    `Bundlenamen`, `Produkt`, `Abteilung`.
  - **Allowed-Dependencies** (rechts): Bei ausgeschlossenen Einträgen
    `Verboten wegen: <Regeltext> in Zeile: <N>` (Regeltext und
    Zeilennummer aus `dependencyrules2.xml`). Bei Impl-Category-
    Verstößen `Verboten wegen Impl Category` (lila, kursiv).

### Allowed Dependencies (Klassenpfad aus `dependencyrules2.xml`)

Liegt im CSV-Verzeichnis eine Datei `dependencyrules2.xml`, wird sie
beim Start des Entry-Points **synchron** geladen (im selben
UI-Thread vor dem Widget-Aufbau). Ein Rechtsklick auf ein Bundle
(oder einen Folder) öffnet ein Kontextmenü mit den Einträgen:

* **Allowed Dependencies** (für Bundles) bzw. **Allowed Dependencies
  (Aggregat)** (für Folder, vereinigte Sicht über alle Kinder) — schaltet
  die rechte Spalte in den Allowed-Dependencies-Modus.
* **Allowed Consumers** (nur für Bundles) — schaltet die linke Spalte
  in den Allowed-Consumers-Modus.
* **Used Dependencies** (Zurückschalten auf den klassischen Modus)

Rechtsklick-Menü und Mode-Switch sind synchron: das Ergebnis liegt
sofort vor, kein Warte-Indikator, keine asynchrone Verzögerung.

Die entsprechende Spalte zeigt den hierarchischen Baum in der
**Reihenfolge der Top-Layer aus `dependencyrules2.xml`** (also z. B.
`EXTERNAL → HOTSWAP → DEV-FRAMEWORK → …`). Verbundene Abhängigkeiten
sind **rot und kursiv** dargestellt; der Hover-Tooltip enthält den
exakten Text der ausschließenden `<exclude>`-Regel samt Zeilennummer.

Wenn `dependencyrules2.xml` fehlt, sind die Kontextmenü-Einträge und
die Mode-Switch-Buttons disabled und bleiben auf "Used" fixiert.

### Compact JSON Format (v2)

Das Modell wird seit der Compact-Serialisierung in das v2-Format
geschrieben (`bundle-model-v2.json` im `target/tmp/rap-rwt-context/`).
Es ist ~5x kleiner als das Legacy-v1-Format, weil:

* **Sequentielle int-IDs** statt langer String-IDs (`bundle:leaf:foo.bar.baz`)
* **Compact-Keys**: `i` (id), `n` (name), `p` (product), `c` (category),
  `cl` (classification), `l` (layer), `pkg` (isPackage), `pi` (parentId)
* **Dependencies als 3-int-Tupel**: `[srcId, cardinality, targetId]`
* **Allowed-Deps als Map<intId, Array<[targetId, excluded, implViolation, rule?, line?]>>**
* **Kein `allowedConsumers`**: wird im Browser aus `allowedDeps` per
  Reverse-Iteration berechnet

Beispiel (gekürzt):

```json
{
  "v": 2,
  "bundles": [
    { "i": 0, "n": "bundle.A", "cl": "API", "l": "API", "pkg": false, "pi": 1, "p": "PROD_A", "c": "api" },
    { "i": 1, "n": "API",      "cl": "API", "l": "API", "pkg": true,  "pi": 0 }
  ],
  "deps": [[0, 5, 2]],
  "allowedDeps": {
    "0": [[1, 0, 0], [2, 1, 0, "<exclude rule>", 42]]
  },
  "layerOrder": ["API", "INTERNAL"]
}
```

**Compact-Key-Referenz:**

| Key | Bedeutung |
|---|---|
| `i` | int-ID (sequentiell ab 0) |
| `n` | Bundle-Name |
| `cl` | Classification-Pfad (z. B. `DEV-FRAMEWORK/API`) |
| `l` | Layer-Name |
| `pkg` | `true` = Folder, `false` = Bundle |
| `pi` | int-ID des Parents (`-1` = root) |
| `p` | Produktname (nur bei non-package) |
| `c` | Category: `api` oder `impl` (nur bei non-package) |

**Allowed-Dep-Tupel-Format:**
- `[t, 0, 0]` = erlaubt (target, not excluded, not impl-violation)
- `[t, 0, 1]` = Impl-Violation (nicht excluded, aber impl aus anderem Produkt)
- `[t, 1, 0, "rule", line]` = excluded via `<exclude>`-Regel
- `[t, 1, 1, "rule", line]` = excluded UND impl-violation (selten, aber möglich)

**Frontend-Adapter:** `bundlegraph.html` enthält einen JS-Adapter
(`adaptV2ToV1`), der das v2-Format transparent in das v1-Format
umwandelt. Dadurch funktioniert der bestehende Render-Code ohne
Änderung.

**Rückwärtskompatibilität:** Die alte v1-Datei (`bundle-model.json`)
kann weiterhin via `BundleModelFileStore.writeLegacyModel(...)`
geschrieben werden, falls ein alter Client sie erwartet.

### Starten

```bash
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.example.sonargraph.rap.LaunchBundleGraph
# → http://localhost:8085/bundleGraph
# → http://localhost:8085/graph (parallel erreichbar)
```

`LaunchBundleGraph` startet eine Jetty-Instanz auf Port 8085 und
registriert **beide** RWT-Servlets:
* `/graph` &rarr; `DependencyGraphApplication` (Beispielmodell)
* `/bundleGraph` &rarr; `BundleDependencyApplication` (XML + CSV)

Die `dependencyrules2.xml` und `bundleDependencies.csv` werden aus dem
aktuellen Arbeitsverzeichnis gelesen. Über die System-Properties
`bundle.rules.xml` (Pfad zur XML) und `bundle.deps.csv` (Pfad zur CSV)
können explizit Pfade gesetzt werden:

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.example.sonargraph.rap.LaunchBundleGraph \
    -Dexec.args="8090 -Dbundle.rules.xml=/path/to/dependencyrules2.xml -Dbundle.deps.csv=/path/to/bundleDependencies.csv"
```

### Standalone-Export mit Allowed-Dependencies

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.example.sonargraph.BundleAllowedExporter \
    -Dexec.args="bundlegraph-allowed.html"
```

Erzeugt eine **eigenständige HTML-Datei** (~110 MB bei den
Beispieldaten) mit eingebetteten Allowed-Dependencies, die ohne
Java-Backend in jedem Browser geöffnet werden kann. Auch hier sind
die Allowed-Einträge rot/kursiv markiert mit Regeltext- und
Zeilennummer-Tooltip.

### Architektur-Hooks

```java
BundleModel model = new BundleModelBuilder(bundles, deps).build();
BundleDependencyWidget widget = new BundleDependencyWidget(parent, model);
widget.setOnBundleClick(id  -> System.out.println("open " + id));
widget.setOnEdgeClick((from, to, w) -> System.out.println(from + " -> " + to + " (" + w + ")"));
widget.setOnBundleSelect((id, kind, selected) ->
    System.out.println(id + " cycle=" + kind + " selected=" + selected));
```

In RAP registriert das Widget vier `BrowserFunction`s
(`javaOnBundleClick`, `javaOnEdgeClick`, `javaOnBundleSelect`,
`javaOnAllowedDeps`), die aus JavaScript aufrufbar sind. Die
Allowed-Dependencies werden on-demand via
`javaOnAllowedDeps(bundleName, kind, callbackId)` mit `kind` in
`"fwd"` (Forward) oder `"rev"` (Reverse) abgefragt.

## Lizenz

MIT — Beispielcode, nach Belieben anpassbar.