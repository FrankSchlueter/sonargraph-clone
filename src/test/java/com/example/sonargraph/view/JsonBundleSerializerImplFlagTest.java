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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests für die korrekte Impl-Category-Markierung im serialisierten
 * JSON (Compact-Format).
 *
 * <p>Prüft, dass {@code b.iv} (= Impl-Violation) im JSON korrekt als
 * {@code 1} erscheint, wenn ein api-Source-Bundle eine impl-Dep aus
 * einem anderen Produkt hat, und als {@code 0}, wenn die impl-Dep zum
 * selben Produkt gehört.</p>
 */
class JsonBundleSerializerImplFlagTest {

    @Test
    void allowedDepsForApiSourceHasImplViolationFlagForImplDepFromOtherProduct() throws Exception {
        // api-Bundle A2 in PROD_A erlaubt impl-Bundle I in PROD_B.
        // Im JSON-allowedDeps für A2 muss der I-Eintrag mit "|...|0|1" enden
        // (nicht excluded, aber impl-violation).
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="API">
                      <bundle name="api.A" category="api" produkt="PROD_A" classification="API"/>
                    </layer>
                    <layer name="I">
                      <bundle name="impl.I" category="impl" produkt="PROD_B" classification="I"/>
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
        String json = JsonBundleSerializer.toJson(model, rules, List.<BundleDependencyRecord>of());

        assertTrue(json.contains("\"allowedDeps\":"));
        int aDepsStart = json.indexOf("\"bundle.A2\":[");
        assertTrue(aDepsStart > 0, "allowedDeps for bundle.A2 must exist");
        int aDepsEnd = json.indexOf(']', aDepsStart);
        String aDeps = json.substring(aDepsStart, aDepsEnd);
        assertTrue(aDeps.contains("impl.I"),
                "bundle.A2's deps must contain impl.I: " + aDeps);
        // Format für impl-violation (nicht excluded): "name|class|layer|0|1"
        assertTrue(aDeps.matches(".*\"impl\\.I\\|[^|]*\\|[^|]*\\|0\\|1\".*"),
                "impl.I entry must end with |0|1 (impl-violation): " + aDeps);
    }

    @Test
    void allowedDepsForImplSourceHasImplViolationFlagForImplDepFromOtherProduct() throws Exception {
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
        DependencyRulesService rules = parse(xml);
        BundleModel model = new BundleModelBuilder(rules, List.<BundleDependencyRecord>of()).build();
        String json = JsonBundleSerializer.toJson(model, rules, List.<BundleDependencyRecord>of());

        int aDepsStart = json.indexOf("\"impl.A2\":[");
        assertTrue(aDepsStart > 0);
        int aDepsEnd = json.indexOf(']', aDepsStart);
        String aDeps = json.substring(aDepsStart, aDepsEnd);
        assertTrue(aDeps.matches(".*\"impl\\.B\\|[^|]*\\|[^|]*\\|0\\|1\".*"),
                "impl.B entry must end with |0|1: " + aDeps);
    }

    @Test
    void allowedDepsForApiSourceHasNoImplFlagForImplDepFromSameProduct() throws Exception {
        String xml = """
                <system version="2">
                  <layers>
                    <layer name="A">
                      <bundle name="api.A" category="api" produkt="PROD_X" classification="A"/>
                    </layer>
                    <layer name="I">
                      <bundle name="impl.I" category="impl" produkt="PROD_X" classification="I"/>
                    </layer>
                    <layer name="APP">
                      <filter>
                        <include layer="A"/>
                        <include layer="I"/>
                      </filter>
                      <bundle name="api.A2" category="api" produkt="PROD_X" classification="APP"/>
                    </layer>
                  </layers>
                </system>
                """;
        DependencyRulesService rules = parse(xml);
        BundleModel model = new BundleModelBuilder(rules, List.<BundleDependencyRecord>of()).build();
        String json = JsonBundleSerializer.toJson(model, rules, List.<BundleDependencyRecord>of());

        int aDepsStart = json.indexOf("\"api.A2\":[");
        assertTrue(aDepsStart > 0);
        int aDepsEnd = json.indexOf(']', aDepsStart);
        String aDeps = json.substring(aDepsStart, aDepsEnd);
        // impl.I aus gleichem Produkt → implFlag = 0
        assertTrue(aDeps.matches(".*\"impl\\.I\\|[^|]*\\|[^|]*\\|0\\|0\".*"),
                "impl.I from same product must end with |0|0: " + aDeps);
    }

    private static DependencyRulesService parse(String xml) throws Exception {
        DependencyRulesParser.ParseResult pr = DependencyRulesParser.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return DependencyRulesService.from(pr);
    }
}
