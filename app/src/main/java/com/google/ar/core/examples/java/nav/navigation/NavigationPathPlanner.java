package com.google.ar.core.examples.java.nav.navigation;

import com.google.ar.core.examples.java.nav.api.model.Node;
import com.google.ar.core.examples.java.nav.utils.NavUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 导航路径的应用层编排器。
 *
 * <p>{@link NavUtils} 负责图和 A* 算法；本类负责把“当前位姿 + 目标”整理成
 * Activity 可以直接消费的路径计划，避免 Activity 了解图算法细节。</p>
 */
public final class NavigationPathPlanner {

    public PathPlan plan(float currentX,
                         float currentY,
                         float currentZ,
                         Node targetNode,
                         List<Node> pathNodes,
                         List<Node> doorplateNodes) {
        if (targetNode == null || pathNodes == null || pathNodes.isEmpty()) {
            return PathPlan.empty();
        }

        Node startNode = NavUtils.findNearestPathNode(
                currentX, currentY, currentZ, pathNodes);
        if (startNode == null) {
            return PathPlan.empty();
        }

        List<Node> computedPath = NavUtils.computeOrderedPath(
                startNode,
                targetNode,
                pathNodes,
                doorplateNodes
        );
        String debugInfo = NavUtils.buildPathDebugInfo(
                startNode,
                targetNode,
                pathNodes,
                doorplateNodes,
                computedPath
        );

        return new PathPlan(startNode, targetNode, computedPath, debugInfo);
    }

    public static final class PathPlan {
        public final Node startNode;
        public final Node targetNode;
        public final List<Node> nodes;
        public final List<String> nodeIds;
        public final String debugInfo;

        private PathPlan(Node startNode,
                         Node targetNode,
                         List<Node> nodes,
                         String debugInfo) {
            this.startNode = startNode;
            this.targetNode = targetNode;
            this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
            this.nodeIds = Collections.unmodifiableList(toDistinctIds(nodes));
            this.debugInfo = debugInfo;
        }

        private static PathPlan empty() {
            return new PathPlan(null, null, Collections.emptyList(), "");
        }

        private static List<String> toDistinctIds(List<Node> nodes) {
            List<String> ids = new ArrayList<>();
            String lastId = null;
            if (nodes == null) {
                return ids;
            }

            for (Node node : nodes) {
                if (node == null || node.id == null || node.id.equals(lastId)) {
                    continue;
                }
                ids.add(node.id);
                lastId = node.id;
            }
            return ids;
        }
    }
}
