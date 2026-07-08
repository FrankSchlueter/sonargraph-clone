package com.example.sonargraph;

import com.example.sonargraph.model.Artifact;
import com.example.sonargraph.model.ArtifactModel;
import com.example.sonargraph.model.Dependency;
import com.example.sonargraph.sort.DsmSorter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DsmSorterTest {

    @Test
    void sortsLayeredTopDown() {
        //    A (top)
        //    |
        //    v
        //    B
        //    |
        //    v
        //    C (bottom)
        ArtifactModel m = new ArtifactModel()
                .addArtifact(new Artifact("C", "C", 2, null))
                .addArtifact(new Artifact("A", "A", 0, null))
                .addArtifact(new Artifact("B", "B", 1, null))
                .addDependency(new Dependency("A", "B"))
                .addDependency(new Dependency("B", "C"));

        List<String> order = new DsmSorter(m).sort();
        assertEquals(List.of("A", "B", "C"), order);
    }

    @Test
    void keepsCycleIntact() {
        // A <-> B <-> C, alle in einer SCC
        ArtifactModel m = new ArtifactModel()
                .addArtifact(new Artifact("A", "A", 0, null))
                .addArtifact(new Artifact("B", "B", 0, null))
                .addArtifact(new Artifact("C", "C", 0, null))
                .addDependency(new Dependency("A", "B"))
                .addDependency(new Dependency("B", "C"))
                .addDependency(new Dependency("C", "A"));

        List<String> order = new DsmSorter(m).sort();
        // alle drei müssen in einer SCC bleiben, also in beliebiger Reihenfolge
        assertEquals(3, order.size());
        assertTrue(order.containsAll(List.of("A", "B", "C")));
    }

    @Test
    void separatesIndependentDagFromCycle() {
        // SCC: A<->B; danach C -> A (C ist unter A)
        ArtifactModel m = new ArtifactModel()
                .addArtifact(new Artifact("A", "A", 0, null))
                .addArtifact(new Artifact("B", "B", 0, null))
                .addArtifact(new Artifact("C", "C", 1, null))
                .addDependency(new Dependency("A", "B"))
                .addDependency(new Dependency("B", "A"))
                .addDependency(new Dependency("C", "A"));

        List<String> order = new DsmSorter(m).sort();
        // C (untere Schicht) muss nach A,B (obere Schicht) kommen
        int idxC = order.indexOf("C");
        int idxA = order.indexOf("A");
        int idxB = order.indexOf("B");
        assertTrue(idxC > idxA && idxC > idxB,
                "C should appear after A/B (layer 1 vs layer 0)");
    }

    @Test
    void handlesEmptyModel() {
        ArtifactModel m = new ArtifactModel();
        List<String> order = new DsmSorter(m).sort();
        assertTrue(order.isEmpty());
    }

    @Test
    void accumulatesDependenciesForRootArtifacts() {
        ArtifactModel m = new ArtifactModel()
                .addArtifact(new Artifact("RootA", "RootA", 0, null))
                .addArtifact(new Artifact("A1", "A1", 0, "RootA"))
                .addArtifact(new Artifact("A2", "A2", 0, "RootA"))
                .addArtifact(new Artifact("RootB", "RootB", 0, null))
                .addArtifact(new Artifact("B1", "B1", 0, "RootB"))
                .addArtifact(new Artifact("C", "C", 0, null))
                .addDependency(new Dependency("A1", "B1", 2))
                .addDependency(new Dependency("A2", "B1", 3))
                .addDependency(new Dependency("A1", "C", 1));

        var out = m.outgoingOf("RootA");
        assertEquals(5, out.get("B1"));
        assertEquals(1, out.get("C"));
        assertEquals(2, out.size());

        var inB1 = m.incomingOf("B1");
        assertEquals(2, inB1.get("A1"));
        assertEquals(3, inB1.get("A2"));

        var inRootB = m.incomingOf("RootB");
        assertEquals(2, inRootB.get("A1"));
        assertEquals(3, inRootB.get("A2"));
    }
}