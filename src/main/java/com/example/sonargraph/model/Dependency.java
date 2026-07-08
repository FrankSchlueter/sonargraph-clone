package com.example.sonargraph.model;

import java.util.Objects;

/**
 * Gerichtete Abhängigkeit zwischen zwei Artefakten.
 *
 * <p>Eine Abhängigkeit {@code from -> to} bedeutet: das Quell-Artefakt
 * <em>verwendet</em> das Ziel-Artefakt. Konvention (siehe Sonargraph):
 *
 * <ul>
 *   <li>Allowed: {@code from.layer > to.layer} (oben &rarr; unten)</li>
 *   <li>Violation: {@code from.layer <= to.layer} (unten &rarr; oben
 *       oder seitlich; gleich &rarr; zyklische Kopplung)</li>
 * </ul>
 *
 * <p>{@code weight} erlaubt mehrere Beziehungen zwischen dem gleichen
 * Paar (Import + TypeUse + MethodCall + ...) zu aggregieren.
 */
public final class Dependency {

    private final String fromId;
    private final String toId;
    private final int weight;

    public Dependency(String fromId, String toId, int weight) {
        this.fromId = Objects.requireNonNull(fromId, "fromId");
        this.toId = Objects.requireNonNull(toId, "toId");
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("self-edge not allowed: " + fromId);
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be > 0");
        }
        this.weight = weight;
    }

    public Dependency(String fromId, String toId) {
        this(fromId, toId, 1);
    }

    public String fromId() { return fromId; }
    public String toId()   { return toId;   }
    public int weight()    { return weight;  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dependency d)) return false;
        return fromId.equals(d.fromId) && toId.equals(d.toId) && weight == d.weight;
    }

    @Override
    public int hashCode() { return Objects.hash(fromId, toId, weight); }

    @Override
    public String toString() {
        return "Dependency[" + fromId + " -> " + toId + " w=" + weight + "]";
    }
}