package com.example.sonargraph.deprules;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für den {@link DependencyRulesParser}.
 */
class DependencyRulesParserTest {

    private static DependencyRulesParser.ParseResult parseResource(String name)
            throws IOException, SAXException {
        try (var in = DependencyRulesParserTest.class.getResourceAsStream(
                "/deprules/" + name)) {
            assertNotNull(in, "Ressource fehlt: " + name);
            return DependencyRulesParser.parse(in);
        }
    }

    @Test
    void parsesSimpleLayerStructure() throws Exception {
        var r = parseResource("simple.xml");
        // 2 Top-Layer
        assertEquals(2, r.topLayers().size());
        assertEquals("A", r.topLayers().get(0).name());
        assertEquals("B", r.topLayers().get(1).name());
        // Bundles
        assertEquals(3, r.bundlesByName().size());
        assertTrue(r.bundlesByName().containsKey("a.bundle1"));
        assertTrue(r.bundlesByName().containsKey("a.bundle2"));
        assertTrue(r.bundlesByName().containsKey("b.bundle1"));
        // Klassifikationen
        assertEquals("A/X", r.bundlesByName().get("a.bundle1").classification());
        assertEquals("A/Y", r.bundlesByName().get("a.bundle2").classification());
        assertEquals("B/Z", r.bundlesByName().get("b.bundle1").classification());
        // Layer-Order
        assertEquals(List.of("A", "B"), r.layerOrder());
    }

    @Test
    void skipsCommentedOutNodes() throws Exception {
        var r = parseResource("commented.xml");
        // Das auskommentierte Bundle darf NICHT in der Map sein
        assertTrue(r.bundlesByName().containsKey("visible.bundle"));
        assertFalse(r.bundlesByName().containsKey("commented.bundle"));
        assertFalse(r.bundlesByName().containsKey("also.commented"));
        // COMMENTED_LAYER wurde ignoriert
        assertEquals(1, r.topLayers().size());
        assertEquals("A", r.topLayers().get(0).name());
    }

    @Test
    void parsesNestedLayers() throws Exception {
        var r = parseResource("multi-layer.xml");
        // 2 Top-Layer: EXTERNAL, DEV
        assertEquals(2, r.topLayers().size());
        var external = r.topLayers().get(0);
        assertEquals("EXTERNAL", external.name());
        assertEquals(1, external.subLayers().size());
        assertEquals("BASE", external.subLayers().get(0).name());
        // 2 Bundles in EXTERNAL/BASE
        assertEquals(2, external.subLayers().get(0).bundles().size());
        // DEV hat 2 Sub-Layer
        var dev = r.topLayers().get(1);
        assertEquals(2, dev.subLayers().size());
        assertEquals("API", dev.subLayers().get(0).name());
        assertEquals("CORE", dev.subLayers().get(1).name());
        // API hat 1 Bundle
        assertEquals(1, dev.subLayers().get(0).bundles().size());
        // CORE hat 1 Bundle und 1 Filter (im Service-Event-Stream)
    }

    @Test
    void preservesDocumentOrder() throws Exception {
        var r = parseResource("simple.xml");
        // Events: LayerEvent(BEGIN A), BundleEvent(a.bundle1), BundleEvent(a.bundle2),
        //         LayerEvent(BEGIN B), FilterEvent, BundleEvent(b.bundle1),
        //         LayerEvent(END A), LayerEvent(END B)
        var events = r.events();
        assertTrue(events.size() >= 7);
        assertTrue(events.get(0) instanceof DependencyRulesParser.LayerEvent);
        var firstLayer = (DependencyRulesParser.LayerEvent) events.get(0);
        assertEquals("A", firstLayer.name());
        assertTrue(firstLayer.begin());
        // Filter kommt vor dem Bundle
        long filterIdx = -1;
        long bundleBIdx = -1;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) instanceof DependencyRulesParser.FilterEvent) filterIdx = i;
            if (events.get(i) instanceof DependencyRulesParser.BundleEvent be
                    && "b.bundle1".equals(be.bundle().name())) bundleBIdx = i;
        }
        assertTrue(filterIdx >= 0);
        assertTrue(bundleBIdx > filterIdx, "Filter muss vor Bundle b.bundle1 stehen");
    }

    @Test
    void extractsLineNumbers() throws Exception {
        // Minimales XML mit bekannten Zeilen
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <system version="2">
                  <layers>
                    <layer name="L1" cycles="false">
                      <bundle name="b1" />
                    </layer>
                  </layers>
                </system>
                """;
        var r = DependencyRulesParser.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var entry = r.bundlesByName().get("b1");
        assertNotNull(entry);
        assertEquals(5, entry.lineNumber(), "Bundle sollte in Zeile 5 sein");
    }

    @Test
    void filtersInLayerAreRecordedAsEvents() throws Exception {
        var r = parseResource("simple.xml");
        long filterCount = r.events().stream()
                .filter(e -> e instanceof DependencyRulesParser.FilterEvent)
                .count();
        assertEquals(1, filterCount);
    }
}
