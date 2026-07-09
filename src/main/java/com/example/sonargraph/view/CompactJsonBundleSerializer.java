package com.example.sonargraph.view;

import com.example.sonargraph.bundle.BundleDependencyRecord;
import com.example.sonargraph.bundle.BundleModel;
import com.example.sonargraph.deprules.AllowedDependency;
import com.example.sonargraph.deprules.DependencyRulesService;
import com.example.sonargraph.model.Artifact;
import com.example.sonargraph.model.ArtifactModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compact JSON-Serialisierung des {@link BundleModel} (Version 2).
 *
 * <p>Ziel: massive Reduktion der JSON-Größe gegenüber dem Legacy-Format
 * ({@link JsonBundleSerializer}) durch:</p>
 * <ul>
 *   <li>Sequentielle int-IDs statt String-IDs für alle Artefakte</li>
 *   <li>Compact-Keys (i, n, p, c, cl, l, pkg, pi)</li>
 *   <li>Dependencies als {@code [srcId, cardinality, targetId]}-Tupel</li>
 *   <li>Allowed-Deps als {@code Map<intId, List<[targetId, excluded, implViolation, rule?, line?]>>}</li>
 *   <li>Kein {@code allowedConsumers}-Feld (wird im Browser aus {@code allowedDeps} berechnet)</li>
 * </ul>
 *
 * <p>Output-Format:</p>
 * <pre>
 * {
 *   "v": 2,
 *   "bundles": [
 *     { "i": 0, "n": "bundle.A", "p": "PROD_A", "c": "api", "cl": "API", "l": "API", "pkg": false, "pi": 5 },
 *     { "i": 5, "n": "API",      "p": "",        "c": "",    "cl": "API", "l": "API", "pkg": true,  "pi": -1 }
 *   ],
 *   "deps": [[0, 5, 1]],
 *   "allowedDeps": {
 *     "0": [[1, 0, 0], [2, 1, 0, "<rule>", 42]]
 *   },
 *   "layerOrder": ["EXTERNAL", "HOTSWAP", "DEV-FRAMEWORK"]
 * }
 * </pre>
 */
public final class CompactJsonBundleSerializer {

    private CompactJsonBundleSerializer() {}

    public static String toJson(BundleModel model,
                                DependencyRulesService rules,
                                List<BundleDependencyRecord> deps) {
        ArtifactModel am = model.artifactModel();
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append('{');

        // Version
        sb.append("\"v\":2");

        // Bundles
        appendBundles(sb, am, model);

        // Deps (used dependencies from CSV)
        appendDeps(sb, am, deps);

        // Allowed-Deps (computed from rules)
        if (rules != null) {
            appendAllowedDeps(sb, rules, am, model);
        }

        // Layer-Order
        if (rules != null) {
            appendLayerOrder(sb, rules);
        }

        sb.append('}');
        return sb.toString();
    }

    private static void appendBundles(StringBuilder sb, ArtifactModel am, BundleModel model) {
        sb.append(",\"bundles\":[");
        boolean first = true;
        for (Artifact a : am.artifacts()) {
            if (!first) sb.append(',');
            first = false;
            int intId = am.intIdOf(a.id());
            sb.append("{\"i\":").append(intId);
            sb.append(",\"n\":");
            appendString(sb, a.name());
            sb.append(",\"cl\":");
            // Für Folder: Klassifikations-Pfad = alles hinter "bundle:fold:"
            // (entspricht der Folder-ID, die der BundleModelBuilder vergibt).
            // Für Leaves: leer, da der Pfad hier nicht relevant ist.
            String classification = a.id().startsWith("bundle:fold:")
                    ? a.id().substring("bundle:fold:".length())
                    : "";
            appendString(sb, classification);
            sb.append(",\"l\":");
            appendString(sb, a.name());
            sb.append(",\"pkg\":").append(isPackage(a, am));
            // parentId: -1 für root, sonst int-ID des Parents
            int parentIntId = a.parentId() == null ? -1 : am.intIdOf(a.parentId());
            sb.append(",\"pi\":").append(parentIntId);
            // Für non-package Artefakte: product + category
            if (!isPackage(a, am)) {
                com.example.sonargraph.bundle.BundleModel.BundleInfo info =
                        model.infoOf(a.id());
                String product = info == null || info.product() == null ? "" : info.product();
                String category = info == null || info.category() == null ? "api" : info.category();
                if (!product.isEmpty()) {
                    sb.append(",\"p\":");
                    appendString(sb, product);
                }
                sb.append(",\"c\":");
                appendString(sb, category);
            }
            sb.append('}');
        }
        sb.append(']');
    }

    private static boolean isPackage(Artifact a, ArtifactModel am) {
        if (a.parentId() == null) return false;
        if (a.id().equals("bundle:root")) return false;
        if (a.id().startsWith("bundle:fold:")) return true;
        // Hat es Kinder?
        for (Artifact other : am.artifacts()) {
            if (a.id().equals(other.parentId())) return true;
        }
        return false;
    }

    private static void appendDeps(StringBuilder sb, ArtifactModel am,
                                   List<BundleDependencyRecord> deps) {
        sb.append(",\"deps\":[");
        boolean first = true;
        if (deps != null) {
            for (BundleDependencyRecord d : deps) {
                int srcId = am.intIdOf(leafId(d.fromBundle()));
                int targetId = am.intIdOf(leafId(d.toBundle()));
                if (srcId < 0 || targetId < 0) continue;  // unbekannte Endpunkte überspringen
                if (!first) sb.append(',');
                first = false;
                sb.append('[').append(srcId).append(',').append(d.cardinality())
                        .append(',').append(targetId).append(']');
            }
        }
        sb.append(']');
    }

    private static void appendAllowedDeps(StringBuilder sb,
                                          DependencyRulesService rules,
                                          ArtifactModel am,
                                          BundleModel model) {
        Set<String> knownLeafNames = new HashSet<>();
        for (Artifact a : am.artifacts()) {
            String id = a.id();
            if (id == null || !id.startsWith("bundle:leaf:")) continue;
            String name = id.substring("bundle:leaf:".length());
            knownLeafNames.add(name);
        }

        sb.append(",\"allowedDeps\":{");
        boolean first = true;
        for (Artifact a : am.artifacts()) {
            String id = a.id();
            if (id == null || !id.startsWith("bundle:leaf:")) continue;
            String bundleName = id.substring("bundle:leaf:".length());
            int srcId = am.intIdOf(id);
            String product = productOf(model, bundleName);
            List<AllowedDependency> deps = rules.allowedDepsFor(bundleName, product);
            List<AllowedDependency> filtered = filterToModel(deps, knownLeafNames);
            if (filtered.isEmpty()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(srcId).append("\":[");
            boolean firstEntry = true;
            for (AllowedDependency d : filtered) {
                int targetId = am.intIdOf(leafId(d.bundleName()));
                if (targetId < 0) continue;
                if (!firstEntry) sb.append(',');
                firstEntry = false;
                // [targetId, excluded, implViolation, ruleText?, ruleLine?]
                sb.append('[').append(targetId)
                        .append(',').append(d.isExcluded() ? 1 : 0)
                        .append(',').append(d.isImplViolation() ? 1 : 0);
                if (d.isExcluded()) {
                    sb.append(',');
                    appendString(sb, d.excludedRuleText() == null ? "" : d.excludedRuleText());
                    sb.append(',').append(d.excludedRuleLine());
                }
                sb.append(']');
            }
            sb.append(']');
        }
        sb.append('}');
    }

    private static void appendLayerOrder(StringBuilder sb, DependencyRulesService rules) {
        sb.append(",\"layerOrder\":[");
        List<String> order = rules.layerOrder();
        if (order != null) {
            boolean first = true;
            for (String name : order) {
                if (!first) sb.append(',');
                first = false;
                appendString(sb, name);
            }
        }
        sb.append(']');
    }

    private static String productOf(BundleModel model, String bundleName) {
        var info = model.infoOf("bundle:leaf:" + bundleName);
        if (info == null || info.product() == null || info.product().isEmpty()) return null;
        return info.product();
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

    private static String leafId(String bundleName) {
        return "bundle:leaf:" + bundleName;
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
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
