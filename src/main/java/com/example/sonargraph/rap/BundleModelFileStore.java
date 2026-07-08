package com.example.sonargraph.rap;

import com.example.sonargraph.bundle.BundleDependencyRecord;
import com.example.sonargraph.bundle.BundleModel;
import com.example.sonargraph.deprules.DependencyRulesService;
import com.example.sonargraph.view.CompactJsonBundleSerializer;
import com.example.sonargraph.view.JsonBundleSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Persistiert das Bundle-Modell als JSON in eine Datei, die vom
 * eingebetteten Jetty-Server über {@link BundleDependencyWidget#MODEL_URL}
 * ausgeliefert wird.
 *
 * <p>Es werden zwei Formate parallel unterstützt:</p>
 * <ul>
 *   <li>{@code bundle-model.json} &mdash; Legacy-Format (siehe
 *       {@link JsonBundleSerializer}), sehr groß (200+ MB bei realen
 *       Daten).</li>
 *   <li>{@code bundle-model-v2.json} &mdash; Compact-Format (siehe
 *       {@link CompactJsonBundleSerializer}), ~5x kleiner durch
 *       int-IDs und Array-Tupel.</li>
 * </ul>
 *
 * <p>{@link #writeModel} schreibt nur die v2-Datei (empfohlen).
 * {@link #writeLegacyModel} schreibt zusätzlich die Legacy-Datei
 * für Rückwärtskompatibilität.</p>
 */
public final class BundleModelFileStore {

    private static final ReentrantLock LOCK = new ReentrantLock();

    private BundleModelFileStore() {}

    /** Pfad zur v2-Datei (empfohlen). */
    public static Path modelFileV2() {
        return Paths.get("target", "tmp", "rap-rwt-context", "bundle-model-v2.json");
    }

    /** Pfad zur Legacy-Datei (für Rückwärtskompatibilität). */
    public static Path modelFile() {
        return Paths.get("target", "tmp", "rap-rwt-context", "bundle-model.json");
    }

    /** Schreibt das Modell im v2-Format (empfohlen). */
    public static void writeModel(BundleModel model) {
        writeModel(model, null, List.of());
    }

    /**
     * Schreibt das Modell im v2-Format (Compact). Wenn {@code rules}
     * und {@code deps} nicht null/leer sind, werden die
     * Allowed-Dependencies in das JSON eingebettet.
     */
    public static void writeModel(BundleModel model,
                                   DependencyRulesService rules,
                                   List<BundleDependencyRecord> deps) {
        LOCK.lock();
        try {
            Path file = modelFileV2();
            Files.createDirectories(file.getParent());
            String json = CompactJsonBundleSerializer.toJson(model, rules, deps);
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("[BundleModelFileStore] wrote " + json.length()
                    + " bytes (v2) to " + file.toAbsolutePath()
                    + (rules != null ? " (with allowedDeps)" : ""));
        } catch (IOException ex) {
            System.err.println("[BundleModelFileStore] could not write v2 model: " + ex);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Schreibt das Modell im Legacy-Format. Wird nur für
     * Rückwärtskompatibilität verwendet (z. B. wenn ein alter Client
     * die v1-Datei erwartet).
     */
    public static void writeLegacyModel(BundleModel model,
                                         DependencyRulesService rules,
                                         List<BundleDependencyRecord> deps) {
        LOCK.lock();
        try {
            Path file = modelFile();
            Files.createDirectories(file.getParent());
            String json = JsonBundleSerializer.toJson(model, rules, deps);
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("[BundleModelFileStore] wrote " + json.length()
                    + " bytes (legacy) to " + file.toAbsolutePath()
                    + (rules != null ? " (with allowedDeps)" : ""));
        } catch (IOException ex) {
            System.err.println("[BundleModelFileStore] could not write legacy model: " + ex);
        } finally {
            LOCK.unlock();
        }
    }
}
