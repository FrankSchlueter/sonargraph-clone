package com.example.sonargraph.bundle;

import com.example.sonargraph.deprules.BundleRuleEntry;
import com.example.sonargraph.deprules.DependencyRulesParser;
import com.example.sonargraph.deprules.DependencyRulesService;
import com.example.sonargraph.deprules.LayerNode;
import com.example.sonargraph.model.Artifact;
import com.example.sonargraph.model.ArtifactModel;
import com.example.sonargraph.model.Dependency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Baut aus den Allowed-Dependencies (Klassenpfad aus
 * {@code dependencyrules2.xml}) und der Liste der tatsächlichen
 * Code-Dependencies (aus {@code bundleDependencies.csv} oder künftig
 * einer Cypher-Query) ein {@link BundleModel} auf, das die
 * hierarchische Struktur der Bundles widerspiegelt.
 *
 * <p>Die Hierarchie wird vollständig aus der {@code <layer>}-Schachtelung
 * der XML-Datei abgeleitet. Pro Layer wird ein Folder-Artifact
 * angelegt, pro {@code <bundle>} ein Leaf-Artifact, dessen
 * {@code parentId} auf den umgebenden Layer zeigt.</p>
 *
 * <p>Die Reihenfolge der Bundles ergibt sich aus
 * {@code layerIndex * 1000 + layerOrdinal}, sodass Layer weiter oben
 * (kleinerer Index) und Bundles innerhalb des Layers in der
 * dokumentierten Reihenfolge erscheinen.</p>
 */
public final class BundleModelBuilder {

    private final DependencyRulesService rules;
    private final List<BundleDependencyRecord> deps;
    private final Map<String, BundleModel.BundleInfo> info = new LinkedHashMap<>();
    private final Set<String> knownBundleIds = new HashSet<>();
    private final Map<String, String> folderIdByPath = new LinkedHashMap<>();

    public BundleModelBuilder(DependencyRulesService rules, List<BundleDependencyRecord> deps) {
        this.rules = rules;
        this.deps = deps;
    }

    public BundleModel build() {
        ArtifactModel model = new ArtifactModel();
        // Root-Folder, an den alles ohne Präfix gehängt wird.
        model.addArtifact(new Artifact("bundle:root", "(root)", Integer.MIN_VALUE, null));

        if (rules == null) {
            return new BundleModel(model, info);
        }

        DependencyRulesParser.ParseResult pr = rules.parseResult();

        // Depth-first: pro Top-Layer rekursiv zuerst den Folder, dann
        // seine direkten Bundles, dann seine Sub-Layers. Dadurch
        // landet jedes Bundle DIREKT nach seinem Parent-Folder im
        // ArtifactModel — vorher wurden alle Folders zuerst und alle
        // Bundles danach eingefügt, wodurch die Bundles im Tree am
        // Ende erschienen statt unter ihrem Parent.
        int topIndex = 0;
        for (LayerNode top : pr.topLayers()) {
            addLayerNode(model, top, topIndex);
            topIndex++;
        }

        // Dependencies (aus der Record-Liste) hinzufügen
        int skipped = 0;
        for (BundleDependencyRecord d : deps) {
            String fromId = leafId(d.fromBundle());
            String toId   = leafId(d.toBundle());
            if (!knownBundleIds.contains(fromId) || !knownBundleIds.contains(toId)) {
                skipped++;
                continue;
            }
            model.addDependency(new Dependency(fromId, toId, d.cardinality()));
        }
        if (skipped > 0) {
            System.err.printf(Locale.ROOT,
                    "WARN %d Dependencies mit unbekannten Endpunkten ignoriert%n", skipped);
        }

        // Sortierreihenfolge: entspricht der depth-first-Einfügereihenfolge
        // im Model (Folder → Bundles → Sub-Folder → Bundles → …).
        List<String> ordered = new ArrayList<>();
        for (Artifact a : model.artifacts()) {
            ordered.add(a.id());
        }
        if (ordered.isEmpty()) ordered.add("bundle:root");
        model.setOrder(ordered);

        return new BundleModel(model, info);
    }

    /**
     * Verarbeitet einen Layer rekursiv depth-first:
     * <ol>
     *   <li>Den Folder dieses Layers anlegen (falls noch nicht angelegt).</li>
     *   <li>Die direkten Bundles dieses Layers anlegen — sie erscheinen
     *       damit direkt unter ihrem Parent-Folder im Model.</li>
     *   <li>Die Sub-Layers rekursiv verarbeiten.</li>
     * </ol>
     * Vorher wurden in {@code build()} zwei separate Passes gefahren
     * (erst alle Folders, dann alle Bundles). Dadurch landeten die
     * Bundles am Ende des Modells und wurden im Center-Tree weit entfernt
     * von ihrem Parent-Folder gerendert.
     */
    private void addLayerNode(ArtifactModel model, LayerNode layer, int topLayerOrdinal) {
        // 1) Folder anlegen (idempotent — addLayerFolder prüft bereits)
        if (!folderIdByPath.containsKey(layer.fullPath())) {
            int ordinal = layer.depth() == 0 ? topLayerOrdinal : topLayerOrdinal;
            String parentId = layer.parent() == null
                    ? "bundle:root"
                    : folderIdByPath.get(layer.parent().fullPath());
            if (parentId == null) parentId = "bundle:root";
            model.addArtifact(new Artifact("bundle:fold:" + layer.fullPath(),
                    layer.name(), ordinal, parentId));
            folderIdByPath.put(layer.fullPath(), "bundle:fold:" + layer.fullPath());
        }
        // 2) Direkte Bundles dieses Layers — direkt nach dem Parent-Folder
        for (BundleRuleEntry b : layer.bundles()) {
            String leaf = leafId(b.name());
            String parentId = folderIdByPath.get(layer.fullPath());
            if (parentId == null) parentId = "bundle:root";
            int ordinal = b.layerIndex() * 1000 + b.layerOrdinal();
            model.addArtifact(new Artifact(leaf, b.name(), ordinal, parentId));
            knownBundleIds.add(leaf);
            info.put(leaf, new BundleModel.BundleInfo(b.produkt(), b.category()));
        }
        // 3) Sub-Layers rekursiv
        for (LayerNode sub : layer.subLayers()) {
            addLayerNode(model, sub, topLayerOrdinal);
        }
    }

    private static String leafId(String bundleName) {
        return "bundle:leaf:" + bundleName;
    }
}
