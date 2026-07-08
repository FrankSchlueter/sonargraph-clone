package com.example.sonargraph.bundle;

/**
 * Repräsentiert eine einzelne Abhängigkeit zwischen zwei Bundles
 * &mdash; in der Regel gelesen aus {@code bundleDependencies.csv}
 * (heute) oder per Cypher-Query aus einem Neo4j-Graphen (zukünftig).
 *
 * <p>Jede Zeile der CSV-Datei entspricht genau einem Record. Mehrere
 * Records mit demselben {@code (fromBundle, toBundle)}-Paar werden bei
 * der Verarbeitung zu einer einzelnen Dependency mit aufsummierter
 * Cardinality zusammengefasst.</p>
 *
 * @param fromBundle  Name des Quell-Bundles (verwendet &rarr;)
 * @param cardinality Anzahl der Code-Referenzen (Import, Type-Use, Method-Call, …)
 * @param toBundle    Name des Ziel-Bundles (&larr; wird verwendet)
 */
public record BundleDependencyRecord(
        String fromBundle,
        int cardinality,
        String toBundle
) {
}
