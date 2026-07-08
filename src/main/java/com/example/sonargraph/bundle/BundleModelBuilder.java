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

        // 1) Layer-Hierarchie als Folder-Artifacts anlegen
        int topIndex = 0;
        for (LayerNode top : pr.topLayers()) {
            addLayerFolder(model, top, topIndex);
            topIndex++;
        }

        // 2) Pro <bundle> ein Leaf-Artifact hinzufügen
        for (LayerNode top : pr.topLayers()) {
            addLayerBundles(model, top);
        }

        // 3) Dependencies (aus der Record-Liste) hinzufügen
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

        // 4) Sortierreihenfolge: topologische Reihenfolge aus den Layern
        List<String> ordered = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (LayerNode top : pr.topLayers()) {
            emitLayerInOrder(model, top, ordered, emitted);
        }
        if (ordered.isEmpty()) ordered.add("bundle:root");
        model.setOrder(ordered);

        return new BundleModel(model, info);
    }

    private void addLayerFolder(ArtifactModel model, LayerNode layer, int topLayerOrdinal) {
        if (folderIdByPath.containsKey(layer.fullPath())) return;
        // Top-Layer bekommen den Top-Index als Ordinal (bestimmt vertikale
        // Position). Sub-Layer erben den Ordinal ihres übergeordneten
        // Top-Layers, sodass sie im selben Block erscheinen.
        int ordinal = layer.depth() == 0 ? topLayerOrdinal : topLayerOrdinal;
        // Für die Hierarchie-Anzeige interessiert uns nur die Reihenfolge
        // der Top-Layer. Sub-Layer erben den Ordinal-Wert ihrer Kinder.
        String parentId = layer.parent() == null
                ? "bundle:root"
                : folderIdByPath.get(layer.parent().fullPath());
        if (parentId == null) parentId = "bundle:root";
        model.addArtifact(new Artifact("bundle:fold:" + layer.fullPath(),
                layer.name(), ordinal, parentId));
        folderIdByPath.put(layer.fullPath(), "bundle:fold:" + layer.fullPath());
        for (LayerNode sub : layer.subLayers()) {
            addLayerFolder(model, sub, topLayerOrdinal);
        }
    }

    private void addLayerBundles(ArtifactModel model, LayerNode layer) {
        for (BundleRuleEntry b : layer.bundles()) {
            String leafId = leafId(b.name());
            String parentId = folderIdByPath.get(layer.fullPath());
            if (parentId == null) parentId = "bundle:root";
            int ordinal = b.layerIndex() * 1000 + b.layerOrdinal();
            model.addArtifact(new Artifact(leafId, b.name(), ordinal, parentId));
            knownBundleIds.add(leafId);
            info.put(leafId, new BundleModel.BundleInfo(b.produkt(), b.category()));
        }
        for (LayerNode sub : layer.subLayers()) {
            addLayerBundles(model, sub);
        }
    }

    private void emitLayerInOrder(ArtifactModel model, LayerNode layer,
                                    List<String> ordered, Set<String> emitted) {
        // 1) Folder dieses Layers (falls noch nicht emittiert)
        String folderId = "bundle:fold:" + layer.fullPath();
        if (emitted.add(folderId)) ordered.add(folderId);
        // 2) Sub-Layer (rekursiv)
        for (LayerNode sub : layer.subLayers()) {
            emitLayerInOrder(model, sub, ordered, emitted);
        }
        // 3) Bundles in diesem Layer
        for (BundleRuleEntry b : layer.bundles()) {
            String leafId = leafId(b.name());
            if (emitted.add(leafId)) ordered.add(leafId);
        }
    }

    private static String leafId(String bundleName) {
        return "bundle:leaf:" + bundleName;
    }
}
