package com.example.sonargraph.view;

import com.example.sonargraph.bundle.BundleDependencyRecord;
import com.example.sonargraph.bundle.BundleModel;
import com.example.sonargraph.bundle.BundleModelBuilder;
import com.example.sonargraph.deprules.DependencyRulesParser;
import com.example.sonargraph.deprules.DependencyRulesService;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests für die Compact-JSON-Serialisierung (v2).
 */
class CompactJsonBundleSerializerTest {

    @Test
    void v2ContainsVersionField() throws Exception {
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="API">
                      <bundle name="bundle.A" category="api" produkt="PROD_A" classification="API"/>
                    </layer>
                  </layers>
                  <filter>
                    <include layer="API"/>
                  </filter>
                </system>
                """;
        DependencyRulesService rules = parse(xml);
        BundleModel model = new BundleModelBuilder(rules, List.<BundleDependencyRecord>of()).build();
        String json = CompactJsonBundleSerializer.toJson(model, rules, List.<BundleDependencyRecord>of());

        assertTrue(json.startsWith("{\"v\":2"), "JSON must start with version field: " + json.substring(0, 50));
    }

    @Test
    void bundlesUseCompactKeys() throws Exception {
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="API">
                      <bundle name="bundle.A" category="api" produkt="PROD_A" classification="API"/>
                    </layer>
                  </layers>
                  <filter>
                    <include layer="API"/>
                  </filter>
                </system>
                """;
        DependencyRulesService rules = parse(xml);
        BundleModel model = new BundleModelBuilder(rules, List.<BundleDependencyRecord>of()).build();
        String json = CompactJsonBundleSerializer.toJson(model, rules, List.<BundleDependencyRecord>of());

        // Compact-Keys vorhanden
        assertTrue(json.contains("\"i\""), "must have int id field");
        assertTrue(json.contains("\"n\""), "must have name field");
        assertTrue(json.contains("\"cl\""), "must have classification field");
        assertTrue(json.contains("\"l\""), "must have layer field");
        assertTrue(json.contains("\"pkg\""), "must have isPackage field");
        assertTrue(json.contains("\"pi\""), "must have parentId field");
        // Product + Category nur bei non-package
        assertTrue(json.contains("\"p\":\"PROD_A\""), "must have product");
        assertTrue(json.contains("\"c\":\"api\""), "must have category");
    }

    @Test
    void dependenciesAreIntTuples() throws Exception {
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="A">
                      <bundle name="a.bundle1" classification="A"/>
                    </layer>
                    <layer name="B">
                      <bundle name="b.bundle1" classification="B"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="A"/>
                        <include layer="B"/>
                      </filter>
                      <bundle name="a.bundle2" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService rules = parse(xml);
        List<BundleDependencyRecord> deps = List.of(
                new BundleDependencyRecord("a.bundle1", 5, "b.bundle1"));
        BundleModel model = new BundleModelBuilder(rules, deps).build();
        String json = CompactJsonBundleSerializer.toJson(model, rules, deps);

        // Deps als int-Array [srcId, card, targetId]
        assertTrue(json.contains("\"deps\":["), "must have deps array");
        // Pattern: [N,5,N] (5 = cardinality)
        assertTrue(json.matches(".*\\[[0-9]+,5,[0-9]+\\].*"),
                "deps must be [srcId, cardinality, targetId]: " + json);
    }

    @Test
    void allowedDepsUseIntIdsAndArrayFormat() throws Exception {
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="API">
                      <bundle name="bundle.A" category="api" produkt="PROD_A" classification="API"/>
                    </layer>
                    <layer name="I">
                      <bundle name="bundle.I" category="impl" produkt="PROD_B" classification="I"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="API"/>
                        <include layer="I"/>
                      </filter>
                      <bundle name="bundle.A2" category="api" produkt="PROD_A" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService rules = parse(xml);
        BundleModel model = new BundleModelBuilder(rules, List.<BundleDependencyRecord>of()).build();
        String json = CompactJsonBundleSerializer.toJson(model, rules, List.<BundleDependencyRecord>of());

        // allowedDeps: Map<String, Array<Array>>
        assertTrue(json.contains("\"allowedDeps\":{"), "must have allowedDeps: " + json);
        // Impl-Violation: bundle.I (impl aus PROD_B) ist erlaubte Dep für bundle.A2 (PROD_A)
        // → Format: [[targetId, 0, 1]] (nicht excluded, impl-violation=1)
        assertTrue(json.contains(",0,1]"),
                "allowedDeps must contain an impl-violation entry [..,0,1]: " + json);
    }

    @Test
    void noAllowedConsumersInV2() throws Exception {
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="API">
                      <bundle name="bundle.A" classification="API"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="API"/>
                      </filter>
                      <bundle name="bundle.B" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService rules = parse(xml);
        BundleModel model = new BundleModelBuilder(rules, List.<BundleDependencyRecord>of()).build();
        String json = CompactJsonBundleSerializer.toJson(model, rules, List.<BundleDependencyRecord>of());

        assertFalse(json.contains("allowedConsumers"),
                "v2 must NOT contain allowedConsumers: " + json);
    }

    @Test
    void compactJsonIsSignificantlySmallerThanLegacy() throws Exception {
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="API">
                      <bundle name="bundle.A" category="api" produkt="PROD_A" classification="API"/>
                    </layer>
                    <layer name="I">
                      <bundle name="bundle.I" category="impl" produkt="PROD_B" classification="I"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="API"/>
                        <include layer="I"/>
                      </filter>
                      <bundle name="bundle.A2" category="api" produkt="PROD_A" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService rules = parse(xml);
        BundleModel model = new BundleModelBuilder(rules, List.<BundleDependencyRecord>of()).build();
        List<BundleDependencyRecord> deps = List.<BundleDependencyRecord>of();

        String legacy = JsonBundleSerializer.toJson(model, rules, deps);
        String compact = CompactJsonBundleSerializer.toJson(model, rules, deps);

        assertTrue(compact.length() < legacy.length(),
                "compact must be smaller than legacy: compact=" + compact.length()
                        + ", legacy=" + legacy.length());
        System.out.printf("[Size] legacy=%d bytes, compact=%d bytes (%.1f%% of legacy)%n",
                legacy.length(), compact.length(),
                100.0 * compact.length() / legacy.length());
    }

    private static DependencyRulesService parse(String xml) throws Exception {
        DependencyRulesParser.ParseResult pr = DependencyRulesParser.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return DependencyRulesService.from(pr);
    }
}
