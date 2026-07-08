package com.example.sonargraph.view;

import com.example.sonargraph.bundle.BundleDependencyRecord;
import com.example.sonargraph.bundle.BundleModel;
import com.example.sonargraph.bundle.BundleModelBuilder;
import com.example.sonargraph.deprules.DependencyRulesParser;
import com.example.sonargraph.deprules.DependencyRulesService;
import com.example.sonargraph.model.ArtifactModel;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonBundleSerializerNestedLayerTest {

    @Test
    void nestedLayerHierarchyProducesCorrectParentIdsAndCategories() throws Exception {
        String xml = """
                <DependencyRules>
                  <product name="DEMO">
                    <layer name="BIZ">
                      <layer name="VERZEICHNIS">
                        <bundle name="verzeichnis.biz" category="api" produkt="DEMO"/>
                        <bundle name="verzeichnis.biz.impl" category="impl" produkt="DEMO"/>
                      </layer>
                      <bundle name="biz.api" category="api" produkt="DEMO"/>
                    </layer>
                  </product>
                </DependencyRules>
                """;
        DependencyRulesService rules = parse(xml);
        List<BundleDependencyRecord> deps = List.of();

        BundleModel model = new BundleModelBuilder(rules, deps).build();
        ArtifactModel am = model.artifactModel();

        // BIZ-Folder existiert mit parentId=bundle:root
        var biz = am.byId("bundle:fold:BIZ");
        assertNotNull(biz, "BIZ folder must exist");
        assertEquals("bundle:root", biz.parentId(), "BIZ is a top-layer");

        // VERZEICHNIS-Folder existiert mit parentId=bundle:fold:BIZ
        var verz = am.byId("bundle:fold:BIZ/VERZEICHNIS");
        assertNotNull(verz, "VERZEICHNIS folder must exist");
        assertEquals("bundle:fold:BIZ", verz.parentId(),
                "VERZEICHNIS must be nested under BIZ");

        // Bundles unter VERZEICHNIS haben parentId=bundle:fold:BIZ/VERZEICHNIS
        var implBundle = am.byId("bundle:leaf:verzeichnis.biz.impl");
        assertNotNull(implBundle);
        assertEquals("bundle:fold:BIZ/VERZEICHNIS", implBundle.parentId());

        // BundleInfo fuer Impl-Bundle: category=impl
        var info = model.infoOf("bundle:leaf:verzeichnis.biz.impl");
        assertNotNull(info);
        assertEquals("impl", info.category());
        assertTrue(info.isImpl());
    }

    @Test
    void nestedLayerHierarchySerializedJsonHasCategoryAndParentId() throws Exception {
        String xml = """
                <DependencyRules>
                  <product name="DEMO">
                    <layer name="BIZ">
                      <layer name="VERZEICHNIS">
                        <bundle name="x" category="impl" produkt="DEMO"/>
                      </layer>
                    </layer>
                  </product>
                </DependencyRules>
                """;
        DependencyRulesService rules = parse(xml);
        List<BundleDependencyRecord> deps = List.of();
        BundleModel model = new BundleModelBuilder(rules, deps).build();

        String json = JsonBundleSerializer.toJson(model, rules, deps);

        // Der Impl-Bundle-Eintrag muss category="impl" haben
        assertTrue(json.contains("\"category\":\"impl\""),
                "JSON must contain category=impl: " + json);
        // VERZEICHNIS-Folder muss als parentId das BIZ-Folder referenzieren
        assertTrue(json.contains("\"id\":\"bundle:fold:BIZ/VERZEICHNIS\""),
                "VERZEICHNIS folder must be in JSON");
        assertTrue(json.contains("\"parentId\":\"bundle:fold:BIZ\""),
                "VERZEICHNIS must have BIZ as parentId");
    }

    private static DependencyRulesService parse(String xml) throws Exception {
        DependencyRulesParser.ParseResult pr = DependencyRulesParser.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return DependencyRulesService.from(pr);
    }
}
