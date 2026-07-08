package com.example.sonargraph.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests für die sequentielle int-ID-Vergabe in {@link ArtifactModel}.
 */
class ArtifactModelIntIdTest {

    @Test
    void intIdsAreAssignedSequentially() {
        ArtifactModel m = new ArtifactModel();
        m.addArtifact(new Artifact("a1", "A1", 0, null));
        m.addArtifact(new Artifact("a2", "A2", 0, null));
        m.addArtifact(new Artifact("a3", "A3", 0, null));

        assertEquals(0, m.intIdOf("a1"));
        assertEquals(1, m.intIdOf("a2"));
        assertEquals(2, m.intIdOf("a3"));
    }

    @Test
    void intIdsAreUnique() {
        ArtifactModel m = new ArtifactModel();
        m.addArtifact(new Artifact("a1", "A1", 0, null));
        m.addArtifact(new Artifact("a2", "A2", 0, null));
        m.addArtifact(new Artifact("a3", "A3", 0, null));

        assertNotEquals(m.intIdOf("a1"), m.intIdOf("a2"));
        assertNotEquals(m.intIdOf("a2"), m.intIdOf("a3"));
        assertNotEquals(m.intIdOf("a1"), m.intIdOf("a3"));
    }

    @Test
    void stringIdOfReturnsCorrectString() {
        ArtifactModel m = new ArtifactModel();
        m.addArtifact(new Artifact("a1", "A1", 0, null));
        m.addArtifact(new Artifact("a2", "A2", 0, null));

        assertEquals("a1", m.stringIdOf(0));
        assertEquals("a2", m.stringIdOf(1));
    }

    @Test
    void unknownIntIdReturnsNull() {
        ArtifactModel m = new ArtifactModel();
        m.addArtifact(new Artifact("a1", "A1", 0, null));

        assertEquals(null, m.stringIdOf(999));
    }

    @Test
    void unknownStringIdReturnsMinusOne() {
        ArtifactModel m = new ArtifactModel();

        assertEquals(-1, m.intIdOf("unknown"));
    }

    @Test
    void duplicateAddDoesNotIncrementCounter() {
        ArtifactModel m = new ArtifactModel();
        m.addArtifact(new Artifact("a1", "A1", 0, null));
        m.addArtifact(new Artifact("a1", "A1-Dup", 0, null));  // gleiche ID

        // a1 hat immer noch int-ID 0
        assertEquals(0, m.intIdOf("a1"));
        // Nächste ID ist 1
        m.addArtifact(new Artifact("a2", "A2", 0, null));
        assertEquals(1, m.intIdOf("a2"));
    }
}
