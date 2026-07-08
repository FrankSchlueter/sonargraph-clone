package com.example.sonargraph;

import com.example.sonargraph.example.ExampleModelGenerator;
import com.example.sonargraph.model.ArtifactModel;
import com.example.sonargraph.view.JsonModelSerializer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExampleModelGeneratorTest {

    @Test
    void producesRequestedCounts() {
        ArtifactModel m = ExampleModelGenerator.generate(42L, 100, 15_000, 1_000);
        assertEquals(100, m.artifactCount());
        // wir erlauben >= 15000 (Mehrfachgewichtung) und <=15000+ ein paar
        // Extrakanten, die der Generator als "allowed" anhängen kann
        assertTrue(m.dependencyCount() >= 15_000,
                "expected at least 15k edges, got " + m.dependencyCount());

        // Violations zählen
        int vio = 0, allowed = 0;
        for (var d : m.dependencies()) {
            var a = m.byId(d.fromId());
            var b = m.byId(d.toId());
            if (a == null || b == null) continue;
            if (a.layer() > b.layer()) vio++;
            else allowed++;
        }
        assertTrue(vio >= 900 && vio <= 1100,
                "expected ~1000 violations, got " + vio);
        // erlaubte dominieren
        assertTrue(allowed > vio);
    }

    @Test
    void jsonHasAllSections() {
        ArtifactModel m = ExampleModelGenerator.generate(42L, 100, 15_000, 1_000);
        String json = JsonModelSerializer.toJson(m);
        assertTrue(json.contains("\"artifacts\":["));
        assertTrue(json.contains("\"edges\":["));
        assertTrue(json.contains("\"order\":["));
        assertTrue(json.contains("\"stats\":{"));
        // mindestens ein paar hundred violations/allowed
        assertTrue(json.contains("\"violation\""));
        assertTrue(json.contains("\"allowed\""));
    }
}