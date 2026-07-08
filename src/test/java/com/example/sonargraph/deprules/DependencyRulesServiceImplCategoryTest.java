package com.example.sonargraph.deprules;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests für die Impl-Category-Filterung in {@link DependencyRulesService}.
 *
 * <p>Prüft insbesondere, dass Impl-Category-Verstöße auch dann markiert
 * werden, wenn das anfragende Bundle selbst {@code api} ist (nicht nur
 * bei impl-Source-Bundles).</p>
 */
class DependencyRulesServiceImplCategoryTest {

    @Test
    void apiSourceWithImplDepFromOtherProductIsMarkedAsViolation() throws Exception {
        // api-Bundle A in Produkt PROD_A erlaubt impl-Bundle I in Produkt PROD_B
        // → aus Sicht von A (PROD_A) ist I ein Impl-Violation
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="API">
                      <bundle name="bundle.A" category="api" produkt="PROD_A" classification="API"/>
                    </layer>
                    <layer name="INTERNAL">
                      <bundle name="bundle.I" category="impl" produkt="PROD_B" classification="INTERNAL"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="API"/>
                        <include layer="INTERNAL"/>
                      </filter>
                      <bundle name="bundle.A2" category="api" produkt="PROD_A" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService svc = parse(xml);

        // Ohne requestingProduct: keine Impl-Markierung
        List<AllowedDependency> base = svc.allowedDepsFor("bundle.A2");
        assertNotNull(base);
        assertFalse(base.isEmpty(), "A2 must have allowed deps");
        assertTrue(base.stream().allMatch(d -> !d.isImplViolation()),
                "without requestingProduct, no impl-violation must be set");

        // Mit requestingProduct=PROD_A: bundle.I ist impl aus PROD_B → violation
        List<AllowedDependency> filtered = svc.allowedDepsFor("bundle.A2", "PROD_A");
        AllowedDependency iEntry = filtered.stream()
                .filter(d -> d.bundleName().equals("bundle.I"))
                .findFirst().orElse(null);
        assertNotNull(iEntry, "bundle.I must be in A2's allowed deps");
        assertTrue(iEntry.isImplViolation(),
                "bundle.I is impl from PROD_B → must be marked as impl-violation for A2 (PROD_A)");

        // Mit requestingProduct=PROD_B: bundle.I ist impl aus PROD_B → KEINE violation
        List<AllowedDependency> sameProduct = svc.allowedDepsFor("bundle.A2", "PROD_B");
        AllowedDependency iSame = sameProduct.stream()
                .filter(d -> d.bundleName().equals("bundle.I"))
                .findFirst().orElse(null);
        assertNotNull(iSame);
        assertFalse(iSame.isImplViolation(),
                "bundle.I is impl from PROD_B → NOT a violation for PROD_B");
    }

    @Test
    void implSourceWithImplDepFromOtherProductIsAlsoMarkedAsViolation() throws Exception {
        // Beide Bundles sind impl, aber aus verschiedenen Produkten
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="A">
                      <bundle name="impl.A" category="impl" produkt="PROD_A" classification="A"/>
                    </layer>
                    <layer name="B">
                      <bundle name="impl.B" category="impl" produkt="PROD_B" classification="B"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="A"/>
                        <include layer="B"/>
                      </filter>
                      <bundle name="impl.A2" category="impl" produkt="PROD_A" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService svc = parse(xml);

        List<AllowedDependency> filtered = svc.allowedDepsFor("impl.A2", "PROD_A");
        AllowedDependency bEntry = filtered.stream()
                .filter(d -> d.bundleName().equals("impl.B"))
                .findFirst().orElse(null);
        assertNotNull(bEntry);
        assertTrue(bEntry.isImplViolation(),
                "impl.B from PROD_B is impl-violation for impl.A2 from PROD_A");
    }

    @Test
    void implDepFromSameProductIsNotMarkedAsViolation() throws Exception {
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="A">
                      <bundle name="impl.A" category="impl" produkt="PROD_X" classification="A"/>
                    </layer>
                    <layer name="B">
                      <bundle name="impl.B" category="impl" produkt="PROD_X" classification="B"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="A"/>
                        <include layer="B"/>
                      </filter>
                      <bundle name="impl.A2" category="impl" produkt="PROD_X" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService svc = parse(xml);

        List<AllowedDependency> filtered = svc.allowedDepsFor("impl.A2", "PROD_X");
        AllowedDependency bEntry = filtered.stream()
                .filter(d -> d.bundleName().equals("impl.B"))
                .findFirst().orElse(null);
        assertNotNull(bEntry);
        assertFalse(bEntry.isImplViolation(),
                "impl.B from same product must NOT be marked as violation");
    }

    @Test
    void implDepWithoutProductIsNotMarkedAsViolation() throws Exception {
        // Impl-Bundle ohne produkt-Attribut: wird wie api behandelt
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="API">
                      <bundle name="api.A" category="api" produkt="PROD_A" classification="API"/>
                    </layer>
                    <layer name="NP">
                      <bundle name="impl.NoProduct" category="impl" classification="NP"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="API"/>
                        <include layer="NP"/>
                      </filter>
                      <bundle name="api.A2" category="api" produkt="PROD_A" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService svc = parse(xml);

        List<AllowedDependency> filtered = svc.allowedDepsFor("api.A2", "PROD_A");
        AllowedDependency noProduct = filtered.stream()
                .filter(d -> d.bundleName().equals("impl.NoProduct"))
                .findFirst().orElse(null);
        assertNotNull(noProduct);
        assertFalse(noProduct.isImplViolation(),
                "impl bundle without produkt must NOT be marked as violation");
    }

    @Test
    void nullRequestingProductSkipsImplFiltering() throws Exception {
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="API">
                      <bundle name="api.A" category="api" produkt="PROD_A" classification="API"/>
                    </layer>
                    <layer name="B">
                      <bundle name="impl.B" category="impl" produkt="PROD_B" classification="B"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="API"/>
                        <include layer="B"/>
                      </filter>
                      <bundle name="api.A2" category="api" produkt="PROD_A" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService svc = parse(xml);

        List<AllowedDependency> noProduct = svc.allowedDepsFor("api.A2", null);
        // Ohne requestingProduct keine Filterung
        assertTrue(noProduct.stream().allMatch(d -> !d.isImplViolation()));
    }

    @Test
    void allowedConsumersAlsoAppliesImplFiltering() throws Exception {
        // Reverse-Sicht: Welche Bundles dürfen impl.I nutzen?
        // Drei Consumer in APP: api.Api (PROD_A), impl.Ii (PROD_B), impl.Cc (PROD_C).
        // Aus Sicht von impl.I (PROD_B):
        // - api.Api ist api → OK
        // - impl.Ii ist impl aus PROD_B → OK (gleiches Produkt)
        // - impl.Cc ist impl aus PROD_C → Impl-Violation
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="API">
                      <bundle name="api.A" category="api" produkt="PROD_A" classification="API"/>
                    </layer>
                    <layer name="I">
                      <bundle name="impl.I" category="impl" produkt="PROD_B" classification="I"/>
                    </layer>
                    <layer name="C">
                      <bundle name="impl.C" category="impl" produkt="PROD_C" classification="C"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="API"/>
                        <include layer="I"/>
                        <include layer="C"/>
                      </filter>
                      <bundle name="api.Api" category="api" produkt="PROD_A" classification="APP"/>
                      <bundle name="impl.Ii" category="impl" produkt="PROD_B" classification="APP"/>
                      <bundle name="impl.Cc" category="impl" produkt="PROD_C" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService svc = parse(xml);

        // Consumer von impl.I aus Sicht von PROD_B
        List<AllowedDependency> consumers = svc.allowedConsumersFor("impl.I", "PROD_B");
        AllowedDependency apiConsumer = consumers.stream()
                .filter(d -> d.bundleName().equals("api.Api"))
                .findFirst().orElse(null);
        AllowedDependency iiConsumer = consumers.stream()
                .filter(d -> d.bundleName().equals("impl.Ii"))
                .findFirst().orElse(null);
        AllowedDependency ccConsumer = consumers.stream()
                .filter(d -> d.bundleName().equals("impl.Cc"))
                .findFirst().orElse(null);
        assertNotNull(apiConsumer, "api.Api must be a consumer of impl.I");
        assertNotNull(iiConsumer, "impl.Ii must be a consumer of impl.I");
        assertNotNull(ccConsumer, "impl.Cc must be a consumer of impl.I");
        assertFalse(apiConsumer.isImplViolation(), "api consumer from other product is OK");
        assertFalse(iiConsumer.isImplViolation(), "impl consumer from same product is OK");
        assertTrue(ccConsumer.isImplViolation(),
                "impl consumer from PROD_C is impl-violation for PROD_B");
    }

    private static DependencyRulesService parse(String xml) throws Exception {
        DependencyRulesParser.ParseResult pr = DependencyRulesParser.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return DependencyRulesService.from(pr);
    }
}
