package com.example.sonargraph.deprules;

import java.util.List;

/**
 * Eine einzelne Filter-Regel aus {@code dependencyrules2.xml}.
 *
 * <p>Ein Filter enthält eine Liste von Include- und Exclude-Regeln. Beim
 * Anwenden gilt: zuerst werden alle Excludes ausgeführt, danach alle
 * Includes. Ein Filter, der nur Excludes enthält, ist ein reiner
 * Blacklist-Filter; einer mit nur Includes eine Whitelist (in diesem Fall
 * werden alle nicht explizit inkludierten Bundles ausgeschlossen).</p>
 *
 * @param includedBundles Namen der zu inkludierenden Bundles
 * @param includedLayers  Pfade der zu inkludierenden Layer
 * @param excludedBundles Namen der zu exkludierenden Bundles
 * @param excludedLayers  Pfade der zu exkludierenden Layer
 * @param lineNumber      1-basierte Zeilennummer des {@code <filter>}-Elements
 */
public record Filter(
        List<String> includedBundles,
        List<String> includedLayers,
        List<String> excludedBundles,
        List<String> excludedLayers,
        int lineNumber
) {
    public Filter {
        includedBundles = includedBundles == null ? List.of() : List.copyOf(includedBundles);
        includedLayers  = includedLayers  == null ? List.of() : List.copyOf(includedLayers);
        excludedBundles = excludedBundles == null ? List.of() : List.copyOf(excludedBundles);
        excludedLayers  = excludedLayers  == null ? List.of() : List.copyOf(excludedLayers);
    }

    /** Liefert true, wenn der Filter keine Include-Regeln enthält. */
    public boolean isBlacklistOnly() {
        return includedBundles.isEmpty() && includedLayers.isEmpty();
    }
}
