package com.example.sonargraph.view;

import com.example.sonargraph.model.Artifact;
import com.example.sonargraph.model.ArtifactModel;
import com.example.sonargraph.model.Dependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manuelle JSON-Serialisierung des Modells.
 *
 * <p>Wir verwenden bewusst keinen JSON-Builder, damit das Projekt ohne
 * externe Runtime-Dependencies (außer RAP selbst) auskommt. Das Format
 * ist auf das zugeschnitten, was die JS-Visualisierung erwartet.
 *
 * <p>Layout:
 * <pre>
 * {
 *   "artifacts": [
 *     { "id": "...", "name": "...", "layer": 0, "parentId": "..." },
 *     ...
 *   ],
 *   "edges": [
 *     { "from": "...", "to": "...", "weight": 3,
 *       "kind": "allowed" | "violation" },
 *     ...
 *   ],
 *   "stats": { "total": ..., "allowed": ..., "violations": ... }
 * }
 * </pre>
 */
public final class JsonModelSerializer {

    private JsonModelSerializer() {}

    public static String toJson(ArtifactModel model) {
        StringBuilder sb = new StringBuilder(1024 * 64);
        sb.append('{');

        sb.append("\"artifacts\":[");
        boolean firstA = true;
        for (Artifact a : model.artifacts()) {
            if (!firstA) sb.append(',');
            firstA = false;
            sb.append("{\"id\":");
            appendString(sb, a.id());
            sb.append(",\"name\":");
            appendString(sb, a.name());
            sb.append(",\"layer\":").append(a.layer());
            sb.append(",\"parentId\":");
            appendString(sb, a.parentId() == null ? "" : a.parentId());
            sb.append('}');
        }
        sb.append(']');

        sb.append(",\"edges\":[");
        boolean firstE = true;
        int allowed = 0, violations = 0;
        for (Dependency d : model.dependencies()) {
            Artifact from = model.byId(d.fromId());
            Artifact to = model.byId(d.toId());
            if (from == null || to == null) continue;
            boolean isViolation = from.layer() > to.layer();
            if (isViolation) violations++; else allowed++;
            if (!firstE) sb.append(',');
            firstE = false;
            sb.append("{\"from\":");
            appendString(sb, d.fromId());
            sb.append(",\"to\":");
            appendString(sb, d.toId());
            sb.append(",\"weight\":").append(d.weight());
            sb.append(",\"kind\":");
            appendString(sb, isViolation ? "violation" : "allowed");
            sb.append('}');
        }
        sb.append(']');

        sb.append(",\"order\":[");
        boolean firstO = true;
        for (String id : model.orderedIds()) {
            if (!firstO) sb.append(',');
            firstO = false;
            appendString(sb, id);
        }
        sb.append(']');

        sb.append(",\"stats\":{");
        sb.append("\"total\":").append(model.dependencyCount());
        sb.append(",\"allowed\":").append(allowed);
        sb.append(",\"violations\":").append(violations);
        sb.append('}');

        sb.append('}');
        return sb.toString();
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}