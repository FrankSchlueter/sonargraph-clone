package com.example.sonargraph.bundle;

import com.example.sonargraph.deprules.DependencyRulesParser;
import com.example.sonargraph.deprules.DependencyRulesService;
import com.example.sonargraph.model.Artifact;
import com.example.sonargraph.model.ArtifactModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleModelBuilderTest {

    @Test
    void buildsHierarchyFromXmlLayers() throws Exception {
        String xml = """
                <DependencyRules>
                  <product name="DEMO">
                    <layer name="ROOT">
                      <layer name="API">
                        <bundle name="CORE-API" category="api" produkt="DEMO"/>
                        <bundle name="INTERNAL-API" category="impl" produkt="DEMO"/>
                      </layer>
                      <layer name="INFRA">
                        <bundle name="DB" category="impl" produkt="DEMO"/>
                      </layer>
                    </layer>
                  </product>
                </DependencyRules>
                """;
        DependencyRulesService rules = parse(xml);
        List<BundleDependencyRecord> deps = List.of();

        BundleModel model = new BundleModelBuilder(rules, deps).build();
        ArtifactModel am = model.artifactModel();

        BundleModel.BundleInfo info = model.infoOf("bundle:leaf:CORE-API");
        assertNotNull(info);
        assertEquals("DEMO", info.product());
        assertEquals("api", info.category());
        assertTrue(!info.isImpl());

        BundleModel.BundleInfo internal = model.infoOf("bundle:leaf:INTERNAL-API");
        assertNotNull(internal);
        assertTrue(internal.isImpl());
    }

    @Test
    void includesDependenciesForKnownBundles() throws Exception {
        String xml = """
                <DependencyRules>
                  <product name="DEMO">
                    <layer name="API">
                      <bundle name="A" category="api" produkt="DEMO"/>
                      <bundle name="B" category="api" produkt="DEMO"/>
                    </layer>
                  </product>
                </DependencyRules>
                """;
        DependencyRulesService rules = parse(xml);
        List<BundleDependencyRecord> deps = List.of(
                new BundleDependencyRecord("A", 1, "B"),
                new BundleDependencyRecord("A", 1, "B"),
                new BundleDependencyRecord("A", 1, "B")
        );

        BundleModel model = new BundleModelBuilder(rules, deps).build();
        ArtifactModel am = model.artifactModel();

        var aDeps = am.outgoingOf("bundle:leaf:A");
        assertEquals(1, aDeps.size(), "duplicates must be merged");
        assertTrue(aDeps.containsKey("bundle:leaf:B"));
    }

    @Test
    void skipsDependenciesWithUnknownEndpoints() throws Exception {
        String xml = """
                <DependencyRules>
                  <product name="DEMO">
                    <layer name="API">
                      <bundle name="A" category="api" produkt="DEMO"/>
                    </layer>
                  </product>
                </DependencyRules>
                """;
        DependencyRulesService rules = parse(xml);
        List<BundleDependencyRecord> deps = List.of(
                new BundleDependencyRecord("A", 1, "UNKNOWN-BUNDLE"));

        BundleModel model = new BundleModelBuilder(rules, deps).build();
        assertEquals(0, model.artifactModel().outgoingOf("bundle:leaf:A").size());
    }

    @Test
    void emptyRulesProducesEmptyModel() {
        List<BundleDependencyRecord> deps = List.of();
        BundleModel model = new BundleModelBuilder(null, deps).build();
        ArtifactModel am = model.artifactModel();
        assertNotNull(am.byId("bundle:root"));
        assertEquals(0, am.outgoingOf("bundle:root").size());
    }

    @Test
    void loaderReadsBundleDependenciesCsv(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("bundleDependencies.csv");
        Files.writeString(csv,
                "fromBundle,cardinality,toBundle\n" +
                "A,1,B\n" +
                "\"A\",\"1\",\"B\"\n" +
                "C,1,D\n",
                StandardCharsets.UTF_8);

        List<BundleDependencyRecord> records =
                BundleDependencyRecordCsvReader.read(csv);

        assertEquals(2, records.size(), "duplicates must be removed");
        assertTrue(records.stream().anyMatch(r -> r.fromBundle().equals("A") && r.toBundle().equals("B")));
        assertTrue(records.stream().anyMatch(r -> r.fromBundle().equals("C") && r.toBundle().equals("D")));
    }

    @Test
    void loaderToleratesBom(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("bundleDependencies.csv");
        Files.writeString(csv, "\uFEFFfromBundle,cardinality,toBundle\nA,1,B\n",
                StandardCharsets.UTF_8);
        List<BundleDependencyRecord> records =
                BundleDependencyRecordCsvReader.read(csv);
        assertEquals(1, records.size());
        assertEquals("A", records.get(0).fromBundle());
        assertEquals("B", records.get(0).toBundle());
    }

    private static DependencyRulesService parse(String xml) throws Exception {
        DependencyRulesParser.ParseResult pr = DependencyRulesParser.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return DependencyRulesService.from(pr);
    }
}
