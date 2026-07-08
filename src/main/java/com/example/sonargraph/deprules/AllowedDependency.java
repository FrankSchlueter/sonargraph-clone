package com.example.sonargraph.deprules;

/**
 * Eine einzelne Abhängigkeit, die für ein Bundle laut
 * {@code dependencyrules2.xml} erlaubt (oder explizit verboten) ist.
 *
 * <p>Wird vom Frontend für die Anzeige im Allowed-Dependencies-Side-Tree
 * verwendet. Verbundene Bundles tragen die Information, durch welche
 * Exclude-Regel sie verboten sind, einschließlich der Zeilennummer in
 * der XML-Datei.</p>
 *
 * <p>Zusätzlich kennt ein Eintrag das Flag {@code isImplViolation}:
 * ein {@code impl}-Bundle darf gemäß seiner {@code category} nur
 * Bundles des gleichen {@code produkt} sehen &mdash; enthält der
 * Klassenpfad jedoch ein {@code impl}-Bundle eines anderen Produkts,
 * so wird dieses mit {@code isExcluded=true, isImplViolation=true}
 * markiert. Das Frontend stellt solche Einträge lila und kursiv dar und
 * ergänzt den Tooltip um die Zeile "Verboten wegen Impl Category".</p>
 *
 * @param bundleName         Name des referenzierten Bundles
 * @param classification     Voll qualifizierter Klassifikationspfad
 * @param layerPath          Pfad des Top-Layers (z. B. "DEV-FRAMEWORK")
 * @param isExcluded         true, wenn derzeit durch eine Exclude-Regel
 *                           aus dem Klassenpfad entfernt
 * @param isImplViolation    true, wenn der Verbotsgrund eine
 *                           Impl-Category-Verletzung ist (impl-Bundle
 *                           eines anderen Produkts); impliziert
 *                           {@code isExcluded=true}
 * @param excludedRuleText   Vollständiger Text der Exclude-Regel, z. B.
 *                           {@code "<exclude layer=\"DEV-FRAMEWORK/API\"/>"}
 *                           (kann {@code null} sein, wenn nicht ausgeschlossen)
 * @param excludedRuleLine   1-basierte Zeilennummer der Exclude-Regel
 *                           (0, wenn nicht ausgeschlossen)
 */
public record AllowedDependency(
        String bundleName,
        String classification,
        String layerPath,
        boolean isExcluded,
        boolean isImplViolation,
        String excludedRuleText,
        int excludedRuleLine
) {
    /** Erzeugt einen erlaubten (nicht ausgeschlossenen) Eintrag. */
    public static AllowedDependency allowed(String bundleName, String classification, String layerPath) {
        return new AllowedDependency(bundleName, classification, layerPath, false, false, null, 0);
    }

    /** Erzeugt einen durch {@code <exclude>} verbotenen Eintrag. */
    public static AllowedDependency excluded(String bundleName, String classification, String layerPath,
                                             String ruleText, int ruleLine) {
        return new AllowedDependency(bundleName, classification, layerPath, true, false, ruleText, ruleLine);
    }

    /**
     * Erzeugt einen durch Impl-Category verbotenen Eintrag.
     *
     * <p>Wichtig: {@code isExcluded} ist hier {@code false}, weil eine
     * Impl-Category-Verletzung keine XML-Exclude-Regel ist. Die Dep
     * wäre ohne die Produkt-Sichtbarkeit erlaubt, ist aber für dieses
     * Produkt nicht sichtbar. Das Flag {@code isImplViolation}
     * unterscheidet diesen Fall von einer normalen erlaubten Dep.</p>
     */
    public static AllowedDependency implViolated(String bundleName, String classification, String layerPath) {
        return new AllowedDependency(bundleName, classification, layerPath, false, true, null, 0);
    }
}
