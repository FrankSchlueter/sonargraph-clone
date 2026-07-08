package com.example.sonargraph.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-Memory-Modell eines Abhängigkeitsgraphen.
 *
 * <p>HalArtefakte und Abhängigkeiten; bietet schnelle Lookups
 * (id &rarr; Artifact) und einen Adjazenz-Index
 * (from &rarr; (to &rarr; weight)). Nach DSM-Sortierung wird
 * die Reihenfolge der Artefakte durch {@link #orderedIds} definiert.
 */
public final class ArtifactModel {

    private final Map<String, Artifact> artifacts = new LinkedHashMap<>();
    private final List<Dependency> dependencies = new ArrayList<>();
    // from-id -> to-id -> aggregated weight
    private final Map<String, Map<String, Integer>> outgoing = new HashMap<>();
    private final Map<String, Map<String, Integer>> incoming = new HashMap<>();
    private List<String> orderedIds = List.of();

    // Sequentielle int-ID-Vergabe (0, 1, 2, ...) beim Hinzufügen.
    // Wird für die Compact-JSON-Serialisierung verwendet, um Bundle-IDs
    // als kompakte Integer statt als lange String-IDs zu übertragen.
    private int nextIntId = 0;
    private final Map<String, Integer> intIdByStringId = new LinkedHashMap<>();

    public ArtifactModel addArtifact(Artifact a) {
        artifacts.put(a.id(), a);
        if (!intIdByStringId.containsKey(a.id())) {
            intIdByStringId.put(a.id(), nextIntId++);
        }
        return this;
    }

    public ArtifactModel addDependency(Dependency d) {
        dependencies.add(d);
        outgoing.computeIfAbsent(d.fromId(), k -> new HashMap<>())
                .merge(d.toId(), d.weight(), Integer::sum);
        incoming.computeIfAbsent(d.toId(), k -> new HashMap<>())
                .merge(d.fromId(), d.weight(), Integer::sum);
        return this;
    }

    public ArtifactModel setOrder(List<String> ids) {
        this.orderedIds = List.copyOf(ids);
        return this;
    }

    public List<Artifact> artifacts() {
        return List.copyOf(artifacts.values());
    }

    public List<Dependency> dependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    public List<String> orderedIds() { return orderedIds; }

    public Artifact byId(String id) { return artifacts.get(id); }

    /** Aggregierte ausgehende Kanten (Map ist nicht-null, evtl. leer). */
    public Map<String, Integer> outgoingOf(String id) {
        Map<String, Integer> direct = outgoing.get(id);
        List<String> childrenIds = getChildrenIds(id);
        if (childrenIds.isEmpty()) {
            return direct != null ? direct : Map.of();
        }
        Map<String, Integer> accumulated = new HashMap<>();
        if (direct != null) {
            accumulated.putAll(direct);
        }
        for (String childId : childrenIds) {
            Map<String, Integer> childOut = outgoing.get(childId);
            if (childOut != null) {
                childOut.forEach((toId, w) -> accumulated.merge(toId, w, Integer::sum));
            }
        }
        return accumulated;
    }

    /** Aggregierte eingehende Kanten. */
    public Map<String, Integer> incomingOf(String id) {
        Map<String, Integer> direct = incoming.get(id);
        List<String> childrenIds = getChildrenIds(id);
        if (childrenIds.isEmpty()) {
            return direct != null ? direct : Map.of();
        }
        Map<String, Integer> accumulated = new HashMap<>();
        if (direct != null) {
            accumulated.putAll(direct);
        }
        for (String childId : childrenIds) {
            Map<String, Integer> childIn = incoming.get(childId);
            if (childIn != null) {
                childIn.forEach((fromId, w) -> accumulated.merge(fromId, w, Integer::sum));
            }
        }
        return accumulated;
    }

    private List<String> getChildrenIds(String parentId) {
        List<String> list = new ArrayList<>();
        for (Artifact a : artifacts.values()) {
            if (parentId.equals(a.parentId())) {
                list.add(a.id());
            }
        }
        return list;
    }

    public int artifactCount() { return artifacts.size(); }
    public int dependencyCount() { return dependencies.size(); }

    /**
     * Liefert die sequentielle int-ID für eine Artifact-String-ID.
     * Die IDs werden in der Reihenfolge der {@code addArtifact}-Aufrufe
     * vergeben (beginnend bei 0).
     */
    public int intIdOf(String stringId) {
        Integer id = intIdByStringId.get(stringId);
        return id == null ? -1 : id;
    }

    /**
     * Liefert die String-ID für eine sequentielle int-ID.
     */
    public String stringIdOf(int intId) {
        for (Map.Entry<String, Integer> e : intIdByStringId.entrySet()) {
            if (e.getValue() == intId) return e.getKey();
        }
        return null;
    }

    /** Liefert die Map aller int-IDs (String-ID → int-ID). */
    public Map<String, Integer> intIds() {
        return Collections.unmodifiableMap(intIdByStringId);
    }
}