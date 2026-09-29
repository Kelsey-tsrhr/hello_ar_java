package com.google.ar.core.examples.java.nav.navigation;

import com.google.ar.core.Pose;
import com.google.ar.core.examples.java.nav.api.model.Node;
import com.google.ar.core.examples.java.nav.api.model.RelativeLocalizationResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 把服务器返回的相对地图投影到当前 ARCore 世界坐标。
 *
 * <p>服务器只负责返回相对相机坐标；Anchor 和 base pose 的生命周期仍由
 * {@code NavigationActivity} 管理。本类只做无副作用的数据转换。</p>
 */
public final class NavigationMapBuilder {

    private NavigationMapBuilder() {
    }

    public static Result build(
            List<RelativeLocalizationResponse.RelativeNode> relativeNodes,
            Pose basePose,
            float groundY) {
        if (basePose == null || relativeNodes == null || relativeNodes.isEmpty()) {
            return Result.empty();
        }

        List<Node> allNodes = new ArrayList<>();
        List<Node> doorplateNodes = new ArrayList<>();
        List<Node> pathNodes = new ArrayList<>();
        List<float[]> doorplatePointsAr = new ArrayList<>();

        for (RelativeLocalizationResponse.RelativeNode relativeNode : relativeNodes) {
            if (relativeNode == null
                    || relativeNode.position == null
                    || relativeNode.position.length < 3) {
                continue;
            }

            Pose arWorldPose = basePose.compose(Pose.makeTranslation(
                    relativeNode.position[0],
                    relativeNode.position[1],
                    relativeNode.position[2]
            ));

            Node node = new Node();
            node.id = relativeNode.name != null
                    ? relativeNode.name
                    : "Node_" + allNodes.size();
            node.type = relativeNode.type;
            node.arX = arWorldPose.tx();
            node.arY = groundY;
            node.arZ = arWorldPose.tz();

            allNodes.add(node);
            if ("doorplate".equals(relativeNode.type)) {
                doorplateNodes.add(node);
                doorplatePointsAr.add(new float[]{node.arX, node.arY, node.arZ});
            } else {
                pathNodes.add(node);
            }
        }

        return new Result(allNodes, doorplateNodes, pathNodes, doorplatePointsAr);
    }

    public static final class Result {
        public final List<Node> allNodes;
        public final List<Node> doorplateNodes;
        public final List<Node> pathNodes;
        public final List<float[]> doorplatePointsAr;

        private Result(List<Node> allNodes,
                       List<Node> doorplateNodes,
                       List<Node> pathNodes,
                       List<float[]> doorplatePointsAr) {
            this.allNodes = Collections.unmodifiableList(new ArrayList<>(allNodes));
            this.doorplateNodes = Collections.unmodifiableList(new ArrayList<>(doorplateNodes));
            this.pathNodes = Collections.unmodifiableList(new ArrayList<>(pathNodes));
            this.doorplatePointsAr = Collections.unmodifiableList(new ArrayList<>(doorplatePointsAr));
        }

        private static Result empty() {
            return new Result(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }
    }
}
