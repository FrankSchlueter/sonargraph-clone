package com.example.sonargraph.example;

import com.example.sonargraph.model.Artifact;
import com.example.sonargraph.model.ArtifactModel;
import com.example.sonargraph.model.Dependency;
import com.example.sonargraph.sort.DsmSorter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Generiert ein realistisch anmutendes Software-Architektur-Beispiel:
 *
 * <ul>
 *   <li>100 Artefakte verteilt auf 5 Schichten (z. B. UI / Service /
 *       Domain / Persistence / Common). Jede Schicht enthält 2
 *       Packages à 10 Klassen &mdash; ergibt eine 2-stufige Hierarchie.</li>
 *   <li>15.000 Abhängigkeiten (viele Mehrfachbeziehungen zwischen
 *       demselben Paar, die in der Visualisierung per Gewicht /
 *       Strichstärke dargestellt werden).</li>
 *   <li>1.000 davon sind <em>Violations</em>: Abhängigkeiten "nach oben"
 *       (von tieferer zu höherer Schicht) oder seitliche Zyklen.</li>
 * </ul>
 *
 * <p>Damit das Ergebnis reproduzierbar ist, wird ein fester Seed
 * verwendet. Aufruf: {@link #generate()} oder
 * {@link #generate(long, int, int, int)} mit eigenen Parametern.
 */
public final class ExampleModelGenerator {

    /** Standardwerte: 100 Knoten, 15.000 Edges, 1.000 Violations, Seed 42. */
    public static ArtifactModel generate() {
        return generate(42L, 100, 15_000, 1_000);
    }

    /**
     * Erzeugt das Modell.
     *
     * @param seed        Zufalls-Seed für Reproduzierbarkeit
     * @param nodeCount   Anzahl Artefakte (typisch 100)
     * @param edgeCount   Gesamtzahl Abhängigkeiten (typisch 15.000)
     * @param violationCount Zielanzahl Violations (typisch 1.000)
     */
    public static ArtifactModel generate(long seed, int nodeCount, int edgeCount, int violationCount) {
        if (nodeCount < 4) throw new IllegalArgumentException("nodeCount too small");
        if (violationCount < 0 || violationCount > edgeCount) {
            throw new IllegalArgumentException("violationCount out of range");
        }

        ArtifactModel model = new ArtifactModel();
        Random rng = new Random(seed);

        // ----------------------------------------------------------
        // 1) Artefakte mit Hierarchie aufbauen
        //
        // Schichtaufbau (oben -> unten):
        //   0: presentation
        //   1: application
        //   2: domain
        //   3: persistence
        //   4: common
        //
        // Jede Schicht enthält 2 Packages; pro Package 9 Klassen.
        // Packages erhalten eine *künstlich tiefere* Schicht
        // (li + 1), damit Klassen->Package als erlaubt zählt
        // (oben -> unten), während Package->Klasse eine Violation ist.
        // 5 * 2 * 9 = 90 Klassen + 10 Packages = 100 Artefakte.
        // ----------------------------------------------------------
        String[] layers = { "presentation", "application", "domain", "persistence", "common" };
        int pkgsPerLayer = 2;
        // 5 Schichten * 2 Packages * 9 Klassen = 90 Klassen + 10 Packages = 100 Artefakte
        int classesPerPackage = (nodeCount - layers.length * pkgsPerLayer) / (layers.length * pkgsPerLayer);
        if (classesPerPackage < 1) classesPerPackage = 1;

        // Top-Level Container "system"
        for (int li = 0; li < layers.length; li++) {
            String layerName = layers[li];
            for (int pi = 0; pi < pkgsPerLayer; pi++) {
                String pkgId = "pkg:" + layerName + "." + pi;
                String pkgName = "com.example." + layerName + ".pkg" + pi;
                // Package liegt eine Schicht tiefer als seine Klassen
                model.addArtifact(new Artifact(pkgId, pkgName, li + 1, "system"));
                for (int ci = 0; ci < classesPerPackage; ci++) {
                    String id = pkgId + "." + className(ci);
                    String name = pkgName + "." + className(ci);
                    model.addArtifact(new Artifact(id, name, li, pkgId));
                }
            }
        }
        // Korrektur, falls nodeCount kein Vielfaches von 5*2*10 ist:
        while (model.artifactCount() < nodeCount) {
            int n = model.artifactCount();
            String id = "extra:node" + n;
            model.addArtifact(new Artifact(id, "com.example.extra.Node" + n, 2, "system"));
        }

        List<String> ids = new ArrayList<>(model.orderedIds().isEmpty()
                ? idsOf(model)
                : model.orderedIds());
        if (ids.isEmpty()) ids = idsOf(model);

        // ----------------------------------------------------------
        // 2) Abhängigkeiten generieren
        //
        // Strategie:
        //   a) Erst die geforderten Violations setzen (Kanten "nach oben"
        //      oder zyklisch), deterministisch ausgewählt.
        //   b) Dann erlaubte Kanten (Schicht-rein oder seitlich gleiche
        //      Schicht, aber nicht in den Zyklus-Sack).
        //   c) Den Rest mit Mehrfach-Gewichten auffüllen.
        // ----------------------------------------------------------

        // a) Violations: jede zeigt von einem tieferen Knoten (höhere layer-Zahl
        //    ist "unten") zu einem höheren (kleinere layer-Zahl).
        //    Wir setzen erst alle deterministisch, dann notfalls random.
        int placed = 0;
        // Strategie 1: round-robin über (u: tiefer, v: höher)
        for (int u = 0; u < ids.size() && placed < violationCount; u++) {
            for (int v = 0; v < ids.size() && placed < violationCount; v++) {
                if (u == v) continue;
                Artifact a = model.byId(ids.get(u));
                Artifact b = model.byId(ids.get(v));
                if (a == null || b == null) continue;
                if (a.layer() > b.layer()) {
                    if (tryAdd(model, a.id(), b.id(), rng, 1, 3)) placed++;
                }
            }
        }
        // Strategie 2: mit zufälligem Salt auffüllen
        int safety = 0;
        while (placed < violationCount && safety++ < violationCount * 50) {
            int u = rng.nextInt(ids.size());
            int v = rng.nextInt(ids.size());
            if (u == v) continue;
            Artifact a = model.byId(ids.get(u));
            Artifact b = model.byId(ids.get(v));
            if (a == null || b == null) continue;
            if (a.layer() > b.layer()) {
                if (tryAdd(model, a.id(), b.id(), rng, 1, 3)) placed++;
            } else {
                // erlaubte Richtung -> mit kleiner Chance in eine Violation drehen
                if (rng.nextDouble() < 0.05 && placed < violationCount) {
                    if (tryAdd(model, b.id(), a.id(), rng, 1, 2)) placed++;
                }
            }
        }

        // b) Erlaubte Kanten: häufig, mit Gewicht
        int allowedTarget = edgeCount - placed;
        int allowed = 0;
        safety = 0;
        while (allowed < allowedTarget && safety++ < allowedTarget * 50) {
            int u = rng.nextInt(ids.size());
            int v = rng.nextInt(ids.size());
            if (u == v) continue;
            Artifact a = model.byId(ids.get(u));
            Artifact b = model.byId(ids.get(v));
            if (a == null || b == null) continue;
            if (a.layer() <= b.layer()) {
                if (tryAdd(model, a.id(), b.id(), rng, 1, 5)) allowed++;
            }
        }

        // Falls wir über die Zielzahl gerollt sind, Modell so lassen —
        // die Ziellänge ist eine Obergrenze.

        // ----------------------------------------------------------
        // 3) DSM-Sortierung anwenden
        // ----------------------------------------------------------
        new DsmSorter(model).sort();
        return model;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static List<String> idsOf(ArtifactModel model) {
        List<String> ids = new ArrayList<>(model.artifactCount());
        for (Artifact a : model.artifacts()) ids.add(a.id());
        return ids;
    }

    private static String className(int i) {
        // Klassenname "A", "B", ..., "Z", "A0", "A1", ...
        if (i < 26) return String.valueOf((char) ('A' + i));
        int hi = i / 26 - 1;
        int lo = i % 26;
        return String.valueOf((char) ('A' + hi)) + (char) ('A' + lo);
    }

    /**
     * Fügt eine Kante hinzu, falls Modell + Schichtkonvention es erlauben.
     * Bei Mehrfachbeziehungen wird das Gewicht inkrementiert.
     * Rückgabe: true, wenn tatsächlich etwas am Modell geändert wurde.
     */
    private static boolean tryAdd(ArtifactModel model, String from, String to,
                                  Random rng, int minW, int maxW) {
        if (from.equals(to)) return false;
        // Cap, damit kein Knoten zum "Super-Hub" wird (sonst sieht die
        // Visualisierung nicht mehr nach Schichten aus)
        if (model.outgoingOf(from).size() > 90) return false;
        int w = minW + rng.nextInt(Math.max(1, maxW - minW + 1));
        model.addDependency(new Dependency(from, to, w));
        return true;
    }

    /** Convenience-Formatter für die Konsole. */
    public static void printSummary(ArtifactModel model) {
        int violations = 0, allowed = 0;
        List<Artifact> all = model.artifacts();
        for (Dependency d : model.dependencies()) {
            Artifact a = model.byId(d.fromId());
            Artifact b = model.byId(d.toId());
            if (a == null || b == null) continue;
            if (a.layer() > b.layer()) violations++;
            else allowed++;
        }
        System.out.printf(Locale.ROOT,
                "Modell: %d Artefakte, %d Abhängigkeiten (%d allowed, %d violations)%n",
                all.size(), model.dependencyCount(), allowed, violations);
    }
}