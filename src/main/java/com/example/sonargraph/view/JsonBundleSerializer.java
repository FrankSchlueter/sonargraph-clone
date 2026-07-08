package com.example.sonargraph.view;

import com.example.sonargraph.bundle.BundleDependencyRecord;
import com.example.sonargraph.bundle.BundleModel;
import com.example.sonargraph.deprules.AllowedDependency;
import com.example.sonargraph.deprules.DependencyRulesService;
import com.example.sonargraph.model.Artifact;
import com.example.sonargraph.model.ArtifactModel;
import com.example.sonargraph.model.Dependency;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * JSON-Serialisierung des Bundle-Modells für die JavaScript-Visualisierung.
 *
 * <p>Layout:
 * <pre>
 * {
 *   "artifacts": [
 *     { "id": "...", "name": "...", "layer": 1234,
 *       "parentId": "...", "isPackage": true|false,
 *       "product": "...", "category": "api" },
 *     ...
 *   ],
 *   "edges": [
 *     { "from": "...", "to": "...", "weight": 8 },
 *     ...
 *   ],
 *   "order": [ "bundle:leaf:...", ... ],
 *   "stats": { "bundles": ..., "folders": ..., "edges": ... },
 *   "allowedDeps": { "tk.util.base": [ "name|class|layer|0|", "name2|...|1|rule|line|implViolationFlag", ... ], ... },
 *   "rulesLayerOrder": [ "EXTERNAL", "HOTSWAP", ... ]
 * }
 * </pre>
 *
 * <p>Die Kanten-Liste ({@code edges}) wird aus der übergebenen Liste
 * von {@link BundleDependencyRecord} abgeleitet (Duplikate werden
 * bei der Übergabe bereits aggregiert).</p>
 */
public final class JsonBundleSerializer {

    private JsonBundleSerializer() {}

    public static String toJson(BundleModel model) {
        return toJson(model, null, List.of());
    }

    public static String toJson(BundleModel model, DependencyRulesService rules) {
        return toJson(model, rules, List.of());
    }

    public static String toJson(BundleModel model,
                                DependencyRulesService rules,
                                List<BundleDependencyRecord> deps) {
        ArtifactModel am = model.artifactModel();
        StringBuilder sb = new StringBuilder(1024 * 64);
        sb.append('{');

        int folderCount = 0;
        int leafCount = 0;

        sb.append("\"artifacts\":[");
        boolean firstA = true;
        for (Artifact a : am.artifacts()) {
            if (!firstA) sb.append(',');
            firstA = false;
            boolean isPackage = a.parentId() != null
                    && !a.id().equals("bundle:root")
                    && (a.id().startsWith("bundle:fold:") || hasChildArtifacts(am, a.id()));
            if (isPackage) folderCount++; else leafCount++;
            sb.append("{\"id\":");
            appendString(sb, a.id());
            sb.append(",\"name\":");
            appendString(sb, a.name());
            sb.append(",\"layer\":").append(a.layer());
            sb.append(",\"parentId\":");
            appendString(sb, a.parentId() == null ? "" : a.parentId());
            sb.append(",\"isPackage\":").append(isPackage);
            if (!isPackage) {
                BundleModel.BundleInfo info = model.infoOf(a.id());
                sb.append(",\"product\":");
                appendString(sb, info == null || info.product() == null ? "" : info.product());
                sb.append(",\"category\":");
                appendString(sb, info == null || info.category() == null ? "api" : info.category());
            }
            sb.append('}');
        }
        sb.append(']');

        sb.append(",\"edges\":[");
        boolean firstE = true;
        for (Dependency d : am.dependencies()) {
            if (!firstE) sb.append(',');
            firstE = false;
            sb.append("{\"from\":");
            appendString(sb, d.fromId());
            sb.append(",\"to\":");
            appendString(sb, d.toId());
            sb.append(",\"weight\":").append(d.weight());
            sb.append('}');
        }
        sb.append(']');

        sb.append(",\"order\":[");
        boolean firstO = true;
        for (String id : am.orderedIds()) {
            if (!firstO) sb.append(',');
            firstO = false;
            appendString(sb, id);
        }
        sb.append(']');

        sb.append(",\"stats\":{");
        sb.append("\"bundles\":").append(leafCount);
        sb.append(",\"folders\":").append(folderCount);
        sb.append(",\"edges\":").append(am.dependencyCount());
        sb.append('}');

        if (rules != null) {
            Set<String> knownLeafNames = new HashSet<>();
            for (Artifact a : am.artifacts()) {
                String id = a.id();
                if (id == null || !id.startsWith("bundle:leaf:")) continue;
                knownLeafNames.add(a.name());
            }
            appendAllowedDeps(sb, model, rules, knownLeafNames);
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * Schreibt die {@code allowedDeps} und {@code allowedConsumers}
     * Maps in den JSON-Output. Keys sind Bundle-Namen, Values sind
     * Listen von kompakten Strings. Beide Maps werden eingebettet, damit
     * das Frontend die Daten synchron aus dem Modell-JSON lesen kann
     * (ohne BrowserFunction / Async-Mechanismus).
     *
     * <p>Für jedes Bundle wird dessen eigenes {@code produkt} als
     * {@code requestingProduct} an {@code allowedDepsFor} übergeben,
     * damit Impl-Category-Verstöße (Dep ist impl, aber gehört zu
     * einem anderen Produkt) korrekt als {@code b.iv = 1} markiert
     * werden.</p>
     */
    private static void appendAllowedDeps(StringBuilder sb,
                                          BundleModel model,
                                          DependencyRulesService rules,
                                          Set<String> knownLeafNames) {
        sb.append(",\"allowedDeps\":{");
        boolean first = true;
        for (String bundleName : knownLeafNames) {
            String product = productOf(model, bundleName);
            List<AllowedDependency> deps = rules.allowedDepsFor(bundleName, product);
            List<AllowedDependency> filtered = filterToModel(deps, knownLeafNames);
            if (filtered.isEmpty()) continue;
            if (!first) sb.append(',');
            first = false;
            appendString(sb, bundleName);
            sb.append(':');
            appendAllowedDepArrayCompact(sb, filtered);
        }
        sb.append('}');

        // Consumers (Reverse-Sicht) — für die linke Spalte im Live-Modus
        sb.append(",\"allowedConsumers\":{");
        first = true;
        for (String bundleName : knownLeafNames) {
            String product = productOf(model, bundleName);
            List<AllowedDependency> consumers = rules.allowedConsumersFor(bundleName, product);
            List<AllowedDependency> filtered = filterToModel(consumers, knownLeafNames);
            if (filtered.isEmpty()) continue;
            if (!first) sb.append(',');
            first = false;
            appendString(sb, bundleName);
            sb.append(':');
            appendAllowedDepArrayCompact(sb, filtered);
        }
        sb.append('}');

        // Layer-Order für konsistente Darstellung
        sb.append(",\"rulesLayerOrder\":[");
        boolean firstL = true;
        for (String layer : rules.layerOrder()) {
            if (!firstL) sb.append(',');
            firstL = false;
            appendString(sb, layer);
        }
        sb.append(']');
    }

    /**
     * Kompakte Darstellung einer Liste von {@link AllowedDependency}:
     * ein flaches Array von Strings im Format
     * {@code "<name>|<classification>|<layerPath>|<0|1>[|<ruleText>|<ruleLine>|<implFlag>]"}.
     * Die Sonderzeichen werden JSON-konform escaped.
     */
    private static void appendAllowedDepArrayCompact(StringBuilder sb, List<AllowedDependency> deps) {
        sb.append('[');
        boolean first = true;
        for (AllowedDependency d : deps) {
            if (!first) sb.append(',');
            first = false;
            StringBuilder entry = new StringBuilder(64);
            appendCompact(entry, d.bundleName());
            entry.append('|');
            appendCompact(entry, d.classification() == null ? "" : d.classification());
            entry.append('|');
            appendCompact(entry, d.layerPath() == null ? "" : d.layerPath());
            entry.append('|');
            entry.append(d.isExcluded() ? '1' : '0');
            if (d.isExcluded()) {
                entry.append('|');
                appendCompact(entry, d.excludedRuleText() == null ? "" : d.excludedRuleText());
                entry.append('|');
                entry.append(d.excludedRuleLine());
            }
            entry.append('|');
            entry.append(d.isImplViolation() ? '1' : '0');
            appendString(sb, entry.toString());
        }
        sb.append(']');
    }

    /**
     * Schreibt den String in kompakter Pipe-Form: {@code |} und
     * {@code \} werden durch {@code /} ersetzt (um Konflikte mit dem
     * Trennzeichen zu vermeiden). Newlines werden zu Leerzeichen.
     */
    private static void appendCompact(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '|') sb.append('/');
            else if (c == '\\') sb.append('/');
            else if (c == '\n') sb.append(' ');
            else if (c == '\r') sb.append(' ');
            else sb.append(c);
        }
    }

    private static List<AllowedDependency> filterToModel(List<AllowedDependency> deps,
                                                        Set<String> knownLeafNames) {
        List<AllowedDependency> result = new ArrayList<>(deps.size());
        for (AllowedDependency d : deps) {
            if (knownLeafNames.contains(d.bundleName())) {
                result.add(d);
            }
        }
        return result;
    }

    /**
     * Liefert das Produkt eines Bundles aus dem Modell, oder
     * {@code null} wenn das Bundle unbekannt ist oder kein Produkt
     * hat. Wird als {@code requestingProduct} an
     * {@code allowedDepsFor} übergeben, damit Impl-Category-
     * Verstöße korrekt markiert werden.
     */
    private static String productOf(BundleModel model, String bundleName) {
        BundleModel.BundleInfo info = model.infoOf("bundle:leaf:" + bundleName);
        if (info == null || info.product() == null || info.product().isEmpty()) {
            return null;
        }
        return info.product();
    }

    private static boolean hasChildArtifacts(ArtifactModel m, String parentId) {
        for (Artifact a : m.artifacts()) {
            if (parentId.equals(a.parentId())) return true;
        }
        return false;
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
