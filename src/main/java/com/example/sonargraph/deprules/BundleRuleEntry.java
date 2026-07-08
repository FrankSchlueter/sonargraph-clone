package com.example.sonargraph.deprules;

/**
 * Repräsentiert ein Bundle, wie es in {@code dependencyrules2.xml} definiert ist.
 *
 * <p>Im Unterschied zu {@code com.example.sonargraph.bundle.Bundle} (das aus
 * {@code bundles.csv} kommt) enthält dieser Record zusätzlich die Information,
 * an welcher Stelle der XML-Datei das Bundle auftaucht (Layer, Reihenfolge,
 * Zeilennummer). Diese Metadaten werden für die Berechnung des erlaubten
 * Klassenpfads benötigt.</p>
 *
 * @param name             Bundle-Name (aus dem {@code name}-Attribut)
 * @param groupID          Optionaler GroupID (kann {@code null} sein)
 * @param classification   Vollständiger Klassifikationspfad, z. B.
 *                         {@code "DEV-FRAMEWORK/API/CORE-API"}
 * @param repo             Optionales Repo-Attribut
 * @param produkt          Optionales Produkt-Attribut
 * @param category         Optionale Kategorie
 * @param layerIndex       Reihenfolge des enthaltenden Top-Layers
 *                         (0 = EXTERNAL, 1 = HOTSWAP, 2 = DEV-FRAMEWORK, …)
 * @param layerOrdinal     Position des Bundles innerhalb seines Layers
 *                         (0 = erstes Bundle im Layer)
 * @param lineNumber       1-basierte Zeilennummer in {@code dependencyrules2.xml}
 */
public record BundleRuleEntry(
        String name,
        String groupID,
        String classification,
        String repo,
        String produkt,
        String category,
        int layerIndex,
        int layerOrdinal,
        int lineNumber
) {
    /** Voll qualifizierte Pfad-Form: "DEV-FRAMEWORK/API" → "DEV-FRAMEWORK/API". */
    public String classificationPath() {
        return classification == null ? "" : classification;
    }
}
