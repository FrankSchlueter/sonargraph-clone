package com.example.sonargraph.bundle;

import com.example.sonargraph.model.ArtifactModel;

import java.util.Collections;
import java.util.Map;

/**
 * Erweitert ein {@link ArtifactModel} um die Bundle-spezifischen
 * Metadaten (Product, Category), die nicht in {@code Artifact} abgelegt
 * sind. Die Daten stammen aus {@code dependencyrules2.xml}
 * (Attribute {@code produkt} und {@code category} der {@code <bundle>}-Elemente).
 */
public final class BundleModel {

    private final ArtifactModel artifactModel;
    private final Map<String, BundleInfo> info;

    public BundleModel(ArtifactModel artifactModel, Map<String, BundleInfo> info) {
        this.artifactModel = artifactModel;
        this.info = Map.copyOf(info);
    }

    public ArtifactModel artifactModel() { return artifactModel; }

    public BundleInfo infoOf(String bundleId) { return info.get(bundleId); }

    public Map<String, BundleInfo> allInfos() { return Collections.unmodifiableMap(info); }

    /** Zusatz-Info für ein Bundle: Produktname und Sichtbarkeits-Kategorie. */
    public record BundleInfo(String product, String category) {
        public static final BundleInfo EMPTY = new BundleInfo("", "api");

        public boolean isEmpty() {
            return (product == null || product.isEmpty())
                && (category == null || category.isEmpty() || "api".equalsIgnoreCase(category));
        }

        public boolean isImpl() {
            return "impl".equalsIgnoreCase(category);
        }
    }
}
