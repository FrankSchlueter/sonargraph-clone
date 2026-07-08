package com.example.sonargraph;

import com.example.sonargraph.example.ExampleModelGenerator;
import com.example.sonargraph.model.Artifact;
import com.example.sonargraph.model.ArtifactModel;
import com.example.sonargraph.model.Dependency;
import com.example.sonargraph.sort.DsmSorter;
import com.example.sonargraph.view.JsonModelSerializer;

import java.util.List;

/**
 * Plain-Main, das die wichtigsten Eigenschaften verifiziert.
 * Praktisch als Ersatz für eine JUnit-Suite, falls die RAP-Maven-
 * Toolchain noch nicht aufgesetzt ist.
 *
 * <p>Aufruf: {@code java com.example.sonargraph.TestMain}
 */
public final class TestMain {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testSortTopDown();
        testCycleIntact();
        testLayeredSccSeparation();
        testEmptyModel();
        testExampleCounts();
        testJsonStructure();
        System.out.println("--- " + passed + " passed, " + failed + " failed ---");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------

    static void testSortTopDown() {
        ArtifactModel m = new ArtifactModel()
                .addArtifact(new Artifact("C", "C", 2, null))
                .addArtifact(new Artifact("A", "A", 0, null))
                .addArtifact(new Artifact("B", "B", 1, null))
                .addDependency(new Dependency("A", "B"))
                .addDependency(new Dependency("B", "C"));
        List<String> order = new DsmSorter(m).sort();
        assertEquals(List.of("A", "B", "C"), order, "sortTopDown");
    }

    static void testCycleIntact() {
        ArtifactModel m = new ArtifactModel()
                .addArtifact(new Artifact("A", "A", 0, null))
                .addArtifact(new Artifact("B", "B", 0, null))
                .addArtifact(new Artifact("C", "C", 0, null))
                .addDependency(new Dependency("A", "B"))
                .addDependency(new Dependency("B", "C"))
                .addDependency(new Dependency("C", "A"));
        List<String> order = new DsmSorter(m).sort();
        assertEquals(3, order.size(), "cycleIntact.size");
        assertTrue(order.containsAll(List.of("A", "B", "C")), "cycleIntact.members");
    }

    static void testLayeredSccSeparation() {
        // SCC: A<->B (gleiche Schicht, beide mit hohem Coupling)
        // C  -> A  (C hat niedrigere Coupling, kommt vor der SCC)
        ArtifactModel m = new ArtifactModel()
                .addArtifact(new Artifact("A", "A", 0, null))
                .addArtifact(new Artifact("B", "B", 0, null))
                .addArtifact(new Artifact("C", "C", 1, null))
                .addDependency(new Dependency("A", "B"))
                .addDependency(new Dependency("B", "A"))
                .addDependency(new Dependency("C", "A"));
        List<String> order = new DsmSorter(m).sort();
        // A und B müssen benachbart sein (gleiche SCC)
        int idxA = order.indexOf("A"), idxB = order.indexOf("B");
        assertTrue(Math.abs(idxA - idxB) == 1,
                "layeredScc.A_and_B_adjacent (order=" + order + ")");
        // C darf nicht zwischen A und B liegen
        int idxC = order.indexOf("C");
        assertTrue(idxC < idxA || idxC > idxB,
                "layeredScc.C_outside_SCC (order=" + order + ")");
    }

    static void testEmptyModel() {
        ArtifactModel m = new ArtifactModel();
        List<String> order = new DsmSorter(m).sort();
        assertEquals(List.of(), order, "emptyModel");
    }

    static void testExampleCounts() {
        ArtifactModel m = ExampleModelGenerator.generate(42L, 100, 15_000, 1_000);
        assertEquals(100, m.artifactCount(), "example.artifactCount");
        assertTrue(m.dependencyCount() >= 15_000, "example.depCount >= 15000 (got " + m.dependencyCount() + ")");

        int vio = 0, allowed = 0;
        for (Dependency d : m.dependencies()) {
            Artifact a = m.byId(d.fromId());
            Artifact b = m.byId(d.toId());
            if (a == null || b == null) continue;
            if (a.layer() <= b.layer()) vio++;
            else allowed++;
        }
        assertTrue(vio >= 950 && vio <= 1050,
                "example.violations ~1000 (got " + vio + ")");
        assertTrue(allowed > vio, "example.allowedDominates");
    }

    static void testJsonStructure() {
        ArtifactModel m = ExampleModelGenerator.generate(42L, 100, 15_000, 1_000);
        String json = JsonModelSerializer.toJson(m);
        assertTrue(json.contains("\"artifacts\":["), "json.artifacts");
        assertTrue(json.contains("\"edges\":["),       "json.edges");
        assertTrue(json.contains("\"order\":["),       "json.order");
        assertTrue(json.contains("\"stats\":{"),       "json.stats");
        assertTrue(json.contains("\"violation\""),     "json.kindViolation");
        assertTrue(json.contains("\"allowed\""),      "json.kindAllowed");
    }

    // ------------------------------------------------------------------
    // Tiny assert helpers (kein JUnit nötig)

    private static void assertEquals(Object expected, Object actual, String name) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            System.out.println("ok   " + name);
            passed++;
        } else {
            System.out.println("FAIL " + name + " :: expected=" + expected + " actual=" + actual);
            failed++;
        }
    }

    private static void assertEquals(int expected, int actual, String name) {
        assertEquals(Integer.valueOf(expected), Integer.valueOf(actual), name);
    }

    private static void assertTrue(boolean cond, String name) {
        if (cond) { System.out.println("ok   " + name); passed++; }
        else      { System.out.println("FAIL " + name); failed++; }
    }
}