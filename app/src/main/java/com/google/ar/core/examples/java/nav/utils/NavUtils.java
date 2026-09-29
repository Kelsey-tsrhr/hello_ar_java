package com.google.ar.core.examples.java.nav.utils;

import com.google.ar.core.examples.java.nav.api.model.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 导航路径计算工具类。
 *
 * 新策略：
 * 1. 仅使用 pathNodes 构建骨架图；
 * 2. 骨架图使用 MST(最小生成树) 连接，避免门牌节点参与主路径拓扑；
 * 3. 每个 doorplate 节点挂到最近的 path 节点；
 * 4. 在该组合图上运行 A*。
 */
public class NavUtils {

    public static class NavigationGraph {
        public final Map<String, List<String>> adj = new HashMap<>();
        public final Map<String, Node> nodeLookup = new HashMap<>();
        public final Map<String, String> doorplateAnchors = new HashMap<>();
        public final List<Edge> mstEdges = new ArrayList<>();
    }

    public static class Edge {
        public final String a;
        public final String b;
        public final float distance;

        public Edge(String a, String b, float distance) {
            this.a = a;
            this.b = b;
            this.distance = distance;
        }
    }

    /**
     * 兼容旧调用：自动按 type 切分路径点与门牌点，再走新图构建逻辑。
     */
    public static List<Node> computePath(Node startNode, Node endNode, List<Node> allNodes) {
        List<Node> pathNodes = new ArrayList<>();
        List<Node> doorplateNodes = new ArrayList<>();
        if (allNodes != null) {
            for (Node n : allNodes) {
                if (n == null) continue;
                if ("doorplate".equalsIgnoreCase(n.type)) doorplateNodes.add(n);
                else pathNodes.add(n);
            }
        }
        return computeOrderedPath(startNode, endNode, pathNodes, doorplateNodes);
    }

    /**
     * 新版寻路入口：
     * - startNode 应为路径骨架节点（通常是相机最近 path node）
     * - endNode 可以是路径节点，也可以是门牌节点
     */
    public static List<Node> computeOrderedPath(Node startNode, Node endNode, List<Node> pathNodes, List<Node> doorplateNodes) {
        if (startNode == null || endNode == null || pathNodes == null || pathNodes.isEmpty()) {
            return new ArrayList<>();
        }
        if (sameNode(startNode, endNode)) {
            return Collections.singletonList(endNode);
        }

        NavigationGraph graph = buildNavigationGraph(pathNodes, doorplateNodes);
        if (!graph.nodeLookup.containsKey(startNode.id) || !graph.nodeLookup.containsKey(endNode.id)) {
            return new ArrayList<>();
        }
        return aStarSearch(startNode, endNode, graph.nodeLookup, graph.adj);
    }

    public static NavigationGraph buildNavigationGraph(List<Node> pathNodes, List<Node> doorplateNodes) {
        NavigationGraph graph = new NavigationGraph();
        if (pathNodes == null || pathNodes.isEmpty()) return graph;

        for (Node node : pathNodes) {
            if (node == null || node.id == null) continue;
            graph.nodeLookup.put(node.id, node);
            graph.adj.putIfAbsent(node.id, new ArrayList<>());
        }

        // 第一步：只对 path 节点建立 MST 骨架。
        if (pathNodes.size() >= 2) {
            List<Edge> allEdges = new ArrayList<>();
            for (int i = 0; i < pathNodes.size(); i++) {
                Node a = pathNodes.get(i);
                if (a == null || a.id == null) continue;
                for (int j = i + 1; j < pathNodes.size(); j++) {
                    Node b = pathNodes.get(j);
                    if (b == null || b.id == null) continue;
                    allEdges.add(new Edge(a.id, b.id, distance(a, b)));
                }
            }
            Collections.sort(allEdges, Comparator.comparingDouble(e -> e.distance));
            UnionFind uf = new UnionFind(pathNodes);
            for (Edge edge : allEdges) {
                if (uf.union(edge.a, edge.b)) {
                    link(graph.adj, edge.a, edge.b);
                    graph.mstEdges.add(edge);
                }
            }
        }

        // 第二步：把门牌号节点挂到最近的 path 节点上。
        if (doorplateNodes != null) {
            for (Node door : doorplateNodes) {
                if (door == null || door.id == null) continue;
                graph.nodeLookup.put(door.id, door);
                graph.adj.putIfAbsent(door.id, new ArrayList<>());
                Node anchor = findNearestPathNode(door, pathNodes);
                if (anchor != null && anchor.id != null) {
                    link(graph.adj, door.id, anchor.id);
                    graph.doorplateAnchors.put(door.id, anchor.id);
                }
            }
        }

        return graph;
    }

    public static Node findNearestPathNode(float x, float y, float z, List<Node> pathNodes) {
        if (pathNodes == null || pathNodes.isEmpty()) return null;
        Node best = null;
        float bestDist = Float.MAX_VALUE;
        for (Node node : pathNodes) {
            if (node == null) continue;
            float dx = node.arX - x;
            float dy = node.arY - y;
            float dz = node.arZ - z;
            float d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = node;
            }
        }
        return best;
    }

    public static Node findNearestPathNode(Node target, List<Node> pathNodes) {
        if (target == null) return null;
        return findNearestPathNode(target.arX, target.arY, target.arZ, pathNodes);
    }

    public static String buildPathDebugInfo(Node startNode,
                                            Node endNode,
                                            List<Node> pathNodes,
                                            List<Node> doorplateNodes,
                                            List<Node> path) {
        int pathNodeCount = pathNodes == null ? 0 : pathNodes.size();
        int doorplateCount = doorplateNodes == null ? 0 : doorplateNodes.size();
        int pathCount = path == null ? 0 : path.size();
        if (startNode == null || endNode == null || pathNodes == null || pathNodes.isEmpty()) {
            return "invalid_input=true,pathNodes=" + pathNodeCount + ",doorplates=" + doorplateCount + ",path=" + pathCount;
        }

        NavigationGraph graph = buildNavigationGraph(pathNodes, doorplateNodes);
        List<String> startNeighbors = graph.adj.get(startNode.id);
        List<String> endNeighbors = graph.adj.get(endNode.id);
        int startDegree = startNeighbors == null ? 0 : startNeighbors.size();
        int endDegree = endNeighbors == null ? 0 : endNeighbors.size();
        boolean connected = isReachable(startNode.id, endNode.id, graph.adj);
        String anchor = graph.doorplateAnchors.get(endNode.id);

        return String.format(
                "start=%s,end=%s,pathNodes=%d,doorplates=%d,path=%d,startDeg=%d,endDeg=%d,connected=%s,mstEdges=%d,targetAnchor=%s",
                safeId(startNode),
                safeId(endNode),
                pathNodeCount,
                doorplateCount,
                pathCount,
                startDegree,
                endDegree,
                connected,
                graph.mstEdges.size(),
                anchor == null ? "none" : anchor
        );
    }

    private static List<Node> aStarSearch(Node start,
                                          Node goal,
                                          Map<String, Node> nodeLookup,
                                          Map<String, List<String>> adj) {
        PriorityQueue<NodeWrapper> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Map<String, NodeWrapper> openSetMap = new HashMap<>();
        Set<String> closedSet = new HashSet<>();

        NodeWrapper startWrapper = new NodeWrapper(start, null, 0, distance(start, goal));
        openSet.add(startWrapper);
        openSetMap.put(start.id, startWrapper);

        while (!openSet.isEmpty()) {
            NodeWrapper current = openSet.poll();
            if (current == null) break;
            openSetMap.remove(current.node.id);

            if (sameNode(current.node, goal)) {
                return reconstructPath(current);
            }

            closedSet.add(current.node.id);
            List<String> neighbors = adj.get(current.node.id);
            if (neighbors == null) continue;

            for (String neighborId : neighbors) {
                if (closedSet.contains(neighborId)) continue;
                Node neighborNode = nodeLookup.get(neighborId);
                if (neighborNode == null) continue;

                float newGCost = current.gCost + distance(current.node, neighborNode);
                NodeWrapper known = openSetMap.get(neighborId);
                if (known == null) {
                    NodeWrapper nw = new NodeWrapper(neighborNode, current, newGCost, distance(neighborNode, goal));
                    openSet.add(nw);
                    openSetMap.put(neighborId, nw);
                } else if (newGCost < known.gCost) {
                    openSet.remove(known);
                    known.gCost = newGCost;
                    known.fCost = newGCost + known.hCost;
                    known.parent = current;
                    openSet.add(known);
                }
            }
        }
        return new ArrayList<>();
    }

    private static List<Node> reconstructPath(NodeWrapper target) {
        List<Node> path = new ArrayList<>();
        NodeWrapper curr = target;
        while (curr != null) {
            path.add(curr.node);
            curr = curr.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private static void link(Map<String, List<String>> adj, String a, String b) {
        adj.putIfAbsent(a, new ArrayList<>());
        adj.putIfAbsent(b, new ArrayList<>());
        if (!adj.get(a).contains(b)) adj.get(a).add(b);
        if (!adj.get(b).contains(a)) adj.get(b).add(a);
    }

    private static boolean sameNode(Node a, Node b) {
        return a != null && b != null && a.id != null && a.id.equals(b.id);
    }

    private static float distance(Node a, Node b) {
        float dx = a.arX - b.arX;
        float dy = a.arY - b.arY;
        float dz = a.arZ - b.arZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean isReachable(String startId, String endId, Map<String, List<String>> adj) {
        if (startId == null || endId == null) return false;
        if (startId.equals(endId)) return true;

        Set<String> visited = new HashSet<>();
        ArrayList<String> queue = new ArrayList<>();
        queue.add(startId);
        visited.add(startId);

        for (int idx = 0; idx < queue.size(); idx++) {
            String curr = queue.get(idx);
            List<String> neighbors = adj.get(curr);
            if (neighbors == null) continue;
            for (String nb : neighbors) {
                if (endId.equals(nb)) return true;
                if (visited.add(nb)) queue.add(nb);
            }
        }
        return false;
    }

    private static String safeId(Node node) {
        return (node == null || node.id == null) ? "null" : node.id;
    }

    private static class NodeWrapper {
        Node node;
        NodeWrapper parent;
        float gCost;
        float hCost;
        float fCost;

        NodeWrapper(Node node, NodeWrapper parent, float gCost, float hCost) {
            this.node = node;
            this.parent = parent;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
        }
    }

    private static class UnionFind {
        private final Map<String, String> parent = new HashMap<>();
        private final Map<String, Integer> rank = new HashMap<>();

        UnionFind(List<Node> nodes) {
            for (Node node : nodes) {
                if (node == null || node.id == null) continue;
                parent.put(node.id, node.id);
                rank.put(node.id, 0);
            }
        }

        private String find(String x) {
            String p = parent.get(x);
            if (p == null) return x;
            if (!p.equals(x)) parent.put(x, find(p));
            return parent.get(x);
        }

        boolean union(String a, String b) {
            String ra = find(a);
            String rb = find(b);
            if (ra.equals(rb)) return false;

            int rankA = rank.get(ra);
            int rankB = rank.get(rb);
            if (rankA < rankB) parent.put(ra, rb);
            else if (rankA > rankB) parent.put(rb, ra);
            else {
                parent.put(rb, ra);
                rank.put(ra, rankA + 1);
            }
            return true;
        }
    }
}
