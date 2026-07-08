package com.example.sonargraph.model;

import java.util.Objects;

/**
 * Ein Artefakt im Abhängigkeitsgraphen.
 *
 * Ein Artefakt steht stellvertretend für eine Software-Einheit
 * (Klasse, Package, Komponente, Modul...). Im Sinne von Sonargraph
 * Architecture View entspricht ein Artefakt einer Zeile / Spalte
 * der DSM-Matrix.
 *
 * <p>Die Identität erfolgt über {@link #id}. Der {@link #name} ist
 * der voll qualifizierte Anzeigename. {@link #layer} beschreibt
 * die hierarchische Schicht (0 = oberste, höher = tiefer) und wird
 * für die hierarchische Darstellung links verwendet.
 */
public final class Artifact {

    private final String id;
    private final String name;
    private final int layer;
    private final String parentId;

    public Artifact(String id, String name, int layer, String parentId) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.layer = layer;
        this.parentId = parentId; // may be null for top-level
    }

    public String id() { return id; }
    public String name() { return name; }
    public int layer() { return layer; }
    public String parentId() { return parentId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Artifact a)) return false;
        return id.equals(a.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "Artifact[" + id + " (" + name + ") layer=" + layer + "]";
    }
}