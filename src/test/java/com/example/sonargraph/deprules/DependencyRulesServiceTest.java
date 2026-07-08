package com.example.sonargraph.deprules;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für den {@link DependencyRulesService} — Klassenpfad-Algorithmus.
 */
class DependencyRulesServiceTest {

    private static DependencyRulesService loadService(String resource) throws IOException, SAXException {
        try (var in = DependencyRulesServiceTest.class.getResourceAsStream(
                "/deprules/" + resource)) {
            assertNotNull(in, "Ressource fehlt: " + resource);
            return DependencyRulesService.from(DependencyRulesParser.parse(in));
        }
    }

    @Test
    void firstBundleInDevFrameworkSeesOnlyExternal() throws Exception {
        var svc = loadService("simple.xml");
        // a.bundle1 ist das erste Bundle und sollte keinen Vorgänger haben
        var deps = svc.allowedDepsFor("a.bundle1");
        assertEquals(0, deps.size(), "Erstes Bundle hat keine Vorgänger");
    }

    @Test
    void secondBundleSeesFirstBundle() throws Exception {
        var svc = loadService("simple.xml");
        // a.bundle2 sieht a.bundle1
        var deps = svc.allowedDepsFor("a.bundle2");
        var names = namesOf(deps);
        assertEquals(1, deps.size());
        assertTrue(names.contains("a.bundle1"));
        assertFalse(deps.get(0).isExcluded());
    }

    @Test
    void excludeLayerRemovesBundlesFromClasspath() throws Exception {
        var svc = loadService("simple.xml");
        // b.bundle1 hat ein Filter "exclude A" — also sind a.bundle1 und a.bundle2
        // aus dem Klassenpfad entfernt.
        var deps = svc.allowedDepsFor("b.bundle1");
        assertEquals(2, deps.size());
        for (var d : deps) {
            assertTrue(d.isExcluded(), "Beide müssen ausgeschlossen sein: " + d.bundleName());
            assertEquals("<exclude layer=\"A\"/>", d.excludedRuleText());
            assertTrue(d.excludedRuleLine() > 0);
        }
    }

    @Test
    void includeBundleReAddsAfterLayerExclude() throws Exception {
        var svc = loadService("include-exclude.xml");
        // c.bundle1 sollte:
        //   - a.bundle1 sehen (allowed, durch include wieder hinzugefügt)
        //   - a.bundle2 sehen (excluded, exclude A ist aktiv)
        //   - b.bundle1 sehen (allowed, liegt vor c.bundle1)
        var deps = svc.allowedDepsFor("c.bundle1");
        var byName = deps.stream()
                .collect(Collectors.toMap(AllowedDependency::bundleName, d -> d));
        assertEquals(3, deps.size());
        // a.bundle1 ist erlaubt (wieder inkludiert)
        assertFalse(byName.get("a.bundle1").isExcluded());
        // a.bundle2 ist verboten
        assertTrue(byName.get("a.bundle2").isExcluded());
        assertEquals("<exclude layer=\"A\"/>", byName.get("a.bundle2").excludedRuleText());
        // b.bundle1 ist erlaubt
        assertFalse(byName.get("b.bundle1").isExcluded());
    }

    @Test
    void selfDependencyIsIgnored() throws Exception {
        var svc = loadService("simple.xml");
        // a.bundle1 darf sich selbst nicht als Dep haben
        var deps = svc.allowedDepsFor("a.bundle1");
        for (var d : deps) {
            assertNotEquals("a.bundle1", d.bundleName());
        }
    }

    @Test
    void allowedConsumersIsInverseOfAllowedDeps() throws Exception {
        var svc = loadService("include-exclude.xml");
        // a.bundle1 wird von b.bundle1 NICHT gesehen (A ist exkludiert für Layer B).
        // a.bundle1 wird von c.bundle1 gesehen (durch include wieder inkludiert).
        var consumers = svc.allowedConsumersFor("a.bundle1");
        var consumerNames = namesOf(consumers);
        assertTrue(consumerNames.contains("c.bundle1"));
        assertFalse(consumerNames.contains("b.bundle1"));

        // b.bundle1 wird von c.bundle1 gesehen.
        var consumersOfB = svc.allowedConsumersFor("b.bundle1");
        var names = namesOf(consumersOfB);
        assertTrue(names.contains("c.bundle1"));
    }

    @Test
    void multiLayerSubLayersShareClasspath() throws Exception {
        var svc = loadService("multi-layer.xml");
        // ext.base1, ext.base2 sind im EXTERNAL/BASE Layer.
        // dev.api1 ist im DEV/API Layer und sollte ext.base1+ext.base2 sehen.
        var deps = svc.allowedDepsFor("dev.api1");
        var names = namesOf(deps);
        assertTrue(names.contains("ext.base1"));
        assertTrue(names.contains("ext.base2"));
        // dev.api1 sieht sich selbst nicht
        assertFalse(names.contains("dev.api1"));
    }

    @Test
    void excludeLayerInSubLayerAppliesToBundles() throws Exception {
        var svc = loadService("multi-layer.xml");
        // dev.core1 hat ein Filter "exclude DEV/API".
        // dev.core1 sollte dev.api1 als ausgeschlossen sehen.
        var deps = svc.allowedDepsFor("dev.core1");
        var byName = deps.stream()
                .collect(Collectors.toMap(AllowedDependency::bundleName, d -> d));
        assertTrue(byName.containsKey("dev.api1"));
        assertTrue(byName.get("dev.api1").isExcluded());
        assertEquals("<exclude layer=\"DEV/API\"/>", byName.get("dev.api1").excludedRuleText());
    }

    @Test
    void lineNumberIsPreservedInExcludeRule() throws Exception {
        var svc = loadService("simple.xml");
        // Im simple.xml ist das <exclude layer="A"/> in Zeile 11 (manuell geprüft)
        var deps = svc.allowedDepsFor("b.bundle1");
        for (var d : deps) {
            if (d.isExcluded()) {
                assertTrue(d.excludedRuleLine() > 0, "Zeilennummer muss gesetzt sein");
            }
        }
    }

    @Test
    void layerOrderIsReturnedFromParseResult() throws Exception {
        var svc = loadService("simple.xml");
        var order = svc.layerOrder();
        assertEquals(List.of("A", "B"), order);
    }

    @Test
    void knownBundleNamesReturnsAllDefinedBundles() throws Exception {
        var svc = loadService("simple.xml");
        var names = svc.knownBundleNames();
        assertEquals(Set.of("a.bundle1", "a.bundle2", "b.bundle1"), names);
    }

    @Test
    void unknownBundleReturnsEmptyList() throws Exception {
        var svc = loadService("simple.xml");
        assertTrue(svc.allowedDepsFor("does.not.exist").isEmpty());
        assertTrue(svc.allowedConsumersFor("does.not.exist").isEmpty());
    }

    /**
     * Performance-Smoke-Test: parst die echte dependencyrules2.xml und
     * prüft, dass die Berechnung innerhalb eines sinnvollen Zeitlimits
     * abgeschlossen wird.
     */
    @Test
    void performanceOnRealFile() throws IOException, SAXException {
        Path repoRoot = Path.of(".").toAbsolutePath().normalize();
        Path xml = repoRoot.resolve("dependencyrules2.xml");
        if (!Files.exists(xml)) {
            // CI-Umgebung ohne Datei — Test überspringen
            return;
        }
        long t0 = System.currentTimeMillis();
        var svc = DependencyRulesService.fromXml(xml);
        long t1 = System.currentTimeMillis();
        long parseMs = t1 - t0;
        // Konsolenausgabe zur Beobachtung
        System.out.printf("[Performance] Parse+Compute %d ms, %d Bundles, %d Deps%n",
                parseMs, svc.knownBundleNames().size(),
                svc.allowedDepsFor("tk.util.base").size() + svc.allowedDepsFor("tk.sys.core").size());
        // 5 Sekunden ist großzügig dimensioniert (typisch < 500 ms)
        assertTrue(parseMs < 5000, "Parse+Compute dauerte " + parseMs + " ms");
    }

    private static Set<String> namesOf(List<AllowedDependency> deps) {
        Set<String> s = new HashSet<>();
        for (var d : deps) s.add(d.bundleName());
        return s;
    }
}
