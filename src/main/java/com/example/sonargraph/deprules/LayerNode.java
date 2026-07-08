package com.example.sonargraph.deprules;

import java.util.List;

/**
 * Ein Knoten in der Layer-Hierarchie der {@code dependencyrules2.xml}.
 *
 * <p>Die Hierarchie spiegelt die XML-Struktur {@code <layer>} wider. Jeder
 * Knoten enthält die in ihm direkt definierten Bundles sowie alle
 * darunterliegenden Sub-Layer.</p>
 *
 * @param name         Name des Layers (z. B. "DEV-FRAMEWORK")
 * @param fullPath     Voll qualifizierter Pfad (z. B. "DEV-FRAMEWORK/API/CORE-API")
 * @param depth        Tiefe (0 = Top-Layer, 1 = erstes Sub-Layer, …)
 * @param parent       Übergeordneter Layer (kann {@code null} sein)
 * @param subLayers    Direkt enthaltene Sub-Layer in Dokumentreihenfolge
 * @param bundles      Direkt im Layer enthaltene Bundles in Dokumentreihenfolge
 */
public record LayerNode(
        String name,
        String fullPath,
        int depth,
        LayerNode parent,
        List<LayerNode> subLayers,
        List<BundleRuleEntry> bundles
) {
    public LayerNode {
        subLayers = subLayers == null ? List.of() : List.copyOf(subLayers);
        bundles   = bundles   == null ? List.of() : List.copyOf(bundles);
    }

    /** Liefert true, wenn dieser Layer Bundles oder Sub-Layer mit Bundles enthält. */
    public boolean hasBundlesRecursively() {
        if (!bundles.isEmpty()) return true;
        for (LayerNode child : subLayers) {
            if (child.hasBundlesRecursively()) return true;
        }
        return false;
    }
}
