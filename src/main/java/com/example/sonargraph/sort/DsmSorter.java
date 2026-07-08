package com.example.sonargraph.sort;

import com.example.sonargraph.model.Artifact;
import com.example.sonargraph.model.ArtifactModel;
import com.example.sonargraph.model.Dependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DSM-Sortierung (Dependency Structure Matrix).
 *
 * <p>Ziel: Artefakte so anordnen, dass zusammengehörige Einheiten
 * (Module, Packages, Klassen) visuell benachbart liegen. Konvention:
 * höher liegende Schichten oben, tiefer liegende unten.
 *
 * <p>Vorgehen:
 * <ol>
 *   <li><b>Tarjan's SCC</b> &mdash; stark zusammenhängende Komponenten
 *       (Zyklen) werden als Blöcke zusammengehalten.</li>
 *   <li><b>Topologische Sortierung des SCC-DAG</b> &mdash; Komponenten,
 *       von denen nichts abhängt, landen unten; Komponenten, die nur
 *       konsumiert werden, oben. Innerhalb gleicher Tiefe wird nach
 *       Coupling sortiert.</li>
 *   <li><b>Innerhalb-SCC Sortierung</b> &mdash; Kopplungsgrad
 *       (Summe der Kantengewichte rein + raus) absteigend.</li>
 *   <li><b>Layer-Tiefe</b> &mdash; als finaler Tie-Break, damit die
 *       hierarchische Schichtung visuell sauber bleibt.</li>
 * </ol>
 *
 * <p>Komplexität: O(V + E) für SCC + Topo, O(V log V) für die
 * Innen-Sortierung. Läuft auf 100k+ Knoten problemlos.
 */
public final class DsmSorter {

    private final ArtifactModel model;

    public DsmSorter(ArtifactModel model) {
        this.model = model;
    }

    /** Liefert die sortierte ID-Reihenfolge. Schreibt sie auch in das Modell. */
    public List<String> sort() {
        List<Artifact> artifacts = model.artifacts();
        int n = artifacts.size();
        Map<String, Integer> index = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) index.put(artifacts.get(i).id(), i);

        // Adjazenzlisten — wir nehmen die aggregierten "out"-Maps aus
        // dem Modell, da doppelte Kanten dort bereits per weight summiert sind.
        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) {
            java.util.List<Integer> neighbours = new java.util.ArrayList<>();
            Artifact a = artifacts.get(i);
            for (String toId : model.outgoingOf(a.id()).keySet()) {
                Integer v = index.get(toId);
                if (v != null) neighbours.add(v);
            }
            int[] arr = new int[neighbours.size()];
            for (int k = 0; k < neighbours.size(); k++) arr[k] = neighbours.get(k);
            out[i] = arr;
        }

        // 1) Tarjan SCC
        List<List<Integer>> sccs = tarjan(out, n);

        // 2) Coupling-Score pro SCC (Summe eingehender + ausgehender Gewichte)
        Map<Integer, Double> coupling = couplingScores(sccs, index, n);

        // 3) Topologische Sortierung des SCC-DAG mit Coupling als Tie-Break
        List<Integer> sccOrder = topoSortSccs(sccs, out, coupling);

        // 4) IDs expandieren, innerhalb jeder SCC nach Coupling sortieren
        List<String> ordered = new ArrayList<>(n);
        for (int sccId : sccOrder) {
            List<Integer> members = new ArrayList<>(sccs.get(sccId));
            members.sort((a, b) -> {
                double ca = coupling.getOrDefault(a, 0d);
                double cb = coupling.getOrDefault(b, 0d);
                if (ca != cb) return Double.compare(cb, ca); // high coupling first
                int la = model.byId(artifacts.get(a).id()).layer();
                int lb = model.byId(artifacts.get(b).id()).layer();
                if (la != lb) return Integer.compare(la, lb);
                return artifacts.get(a).id().compareTo(artifacts.get(b).id());
            });
            for (int m : members) ordered.add(artifacts.get(m).id());
        }

        model.setOrder(ordered);
        return ordered;
    }

    // ---------------------------------------------------------------------
    // Tarjan's strongly connected components
    // ---------------------------------------------------------------------

    private static List<List<Integer>> tarjan(int[][] g, int n) {
        int[] idx = new int[n];
        int[] low = new int[n];
        boolean[] onStack = new boolean[n];
        Deque<Integer> stack = new ArrayDeque<>();
        List<List<Integer>> sccs = new ArrayList<>();
        java.util.Arrays.fill(idx, -1);
        int[] counter = { 0 };

        for (int v = 0; v < n; v++) {
            if (idx[v] == -1) tarjanDfs(v, g, idx, low, onStack, stack, counter, sccs);
        }
        return sccs;
    }

    private static void tarjanDfs(int v, int[][] g,
                                  int[] idx, int[] low, boolean[] onStack,
                                  Deque<Integer> stack, int[] counter,
                                  List<List<Integer>> sccs) {
        idx[v] = low[v] = counter[0]++;
        stack.push(v);
        onStack[v] = true;
        for (int w : g[v]) {
            if (idx[w] == -1) {
                tarjanDfs(w, g, idx, low, onStack, stack, counter, sccs);
                low[v] = Math.min(low[v], low[w]);
            } else if (onStack[w]) {
                low[v] = Math.min(low[v], idx[w]);
            }
        }
        if (low[v] == idx[v]) {
            List<Integer> scc = new ArrayList<>();
            while (true) {
                int w = stack.pop();
                onStack[w] = false;
                scc.add(w);
                if (w == v) break;
            }
            sccs.add(scc);
        }
    }

    // ---------------------------------------------------------------------
    // Coupling score per SCC (sum of weights in + out)
    // ---------------------------------------------------------------------

    private Map<Integer, Double> couplingScores(List<List<Integer>> sccs,
                                                Map<String, Integer> index,
                                                int n) {
        // Summe pro Knoten (rein + raus)
        double[] nodeScore = new double[n];
        for (Dependency d : model.dependencies()) {
            int u = index.get(d.fromId());
            int v = index.get(d.toId());
            nodeScore[u] += d.weight();
            nodeScore[v] += d.weight();
        }
        // Auf SCCs aggregieren
        Map<Integer, Double> sccScore = new HashMap<>();
        for (int i = 0; i < sccs.size(); i++) {
            double s = 0;
            for (int m : sccs.get(i)) s += nodeScore[m];
            sccScore.put(i, s);
        }
        return sccScore;
    }

    // ---------------------------------------------------------------------
    // Topologische Sortierung des SCC-DAG (Kahn-Algorithmus)
    // ---------------------------------------------------------------------

    private List<Integer> topoSortSccs(List<List<Integer>> sccs,
                                       int[][] out,
                                       Map<Integer, Double> coupling) {
        int m = sccs.size();
        int[] indeg = new int[m];
        @SuppressWarnings("unchecked")
        List<Integer>[] dag = new ArrayList[m];
        for (int i = 0; i < m; i++) dag[i] = new ArrayList<>();

        // SCC-Zugehörigkeit jedes Knotens
        int[] nodeToScc = new int[out.length];
        for (int i = 0; i < m; i++) {
            for (int v : sccs.get(i)) nodeToScc[v] = i;
        }

        List<Artifact> artifacts = model.artifacts();

        // SCC-DAG aus den rohen Out-Listen ableiten, dedupliziert per edge-Set.
        // Wir ignorieren Verletzungen der Schichtreihenfolge (u.layer > v.layer),
        // damit Rückkopplungen die topologische Schichtung nicht auf den Kopf stellen.
        Map<Integer, java.util.LinkedHashSet<Integer>> dagEdges = new HashMap<>();
        for (int i = 0; i < m; i++) dagEdges.put(i, new java.util.LinkedHashSet<>());
        for (int u = 0; u < out.length; u++) {
            int sccU = nodeToScc[u];
            for (int v : out[u]) {
                int sccV = nodeToScc[v];
                if (sccU != sccV) {
                    int layerU = model.byId(artifacts.get(u).id()).layer();
                    int layerV = model.byId(artifacts.get(v).id()).layer();
                    if (layerU <= layerV) {
                        dagEdges.get(sccU).add(sccV);
                    }
                }
            }
        }
        for (int u = 0; u < m; u++) {
            for (int v : dagEdges.get(u)) {
                dag[u].add(v);
                indeg[v]++;
            }
        }

        // Ready-Set: alle mit indeg==0;
        // Wir wählen bevorzugt die SCCs aus höheren Schichten (kleinerer layer-Wert)
        // und als Tie-Break nach Coupling.
        Deque<Integer> ready = new ArrayDeque<>();
        for (int i = 0; i < m; i++) if (indeg[i] == 0) ready.add(i);

        List<Integer> order = new ArrayList<>(m);
        while (!ready.isEmpty()) {
            int pick = -1;
            int bestLayer = Integer.MAX_VALUE;
            double bestCoupling = Double.NEGATIVE_INFINITY;
            for (int s : ready) {
                int sccLayer = Integer.MAX_VALUE;
                for (int member : sccs.get(s)) {
                    sccLayer = Math.min(sccLayer, model.byId(artifacts.get(member).id()).layer());
                }
                double c = coupling.getOrDefault(s, 0d);
                if (sccLayer < bestLayer) {
                    bestLayer = sccLayer;
                    bestCoupling = c;
                    pick = s;
                } else if (sccLayer == bestLayer) {
                    if (c > bestCoupling) {
                        bestCoupling = c;
                        pick = s;
                    }
                }
            }
            ready.removeFirstOccurrence(pick);
            order.add(pick);
            for (int v : dag[pick]) {
                if (--indeg[v] == 0) ready.add(v);
            }
        }
        if (order.size() != m) {
            for (int i = 0; i < m; i++) if (!order.contains(i)) order.add(i);
        }
        return order;
    }
}