package com.google.ar.core.examples.java.nav.navigation;

import android.opengl.Matrix;

import com.google.ar.core.Pose;
import com.google.ar.core.examples.java.nav.api.model.Node;
import com.google.ar.core.examples.java.nav.managers.InteractionConfigManager;
import com.google.ar.core.examples.java.nav.managers.NavigationEngine;
import com.google.ar.core.examples.java.nav.utils.NavUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 构建“我在哪”状态播报文本。
 *
 * <p>本类只依赖导航状态快照和文本解析器，不持有 Activity、View 或 ARCore
 * Session。附近节点、剩余距离和下一转弯的计算都集中在这里。</p>
 */
public final class NavigationReportBuilder {

    public interface TextResolver {
        String resolve(String key, Object... args);
    }

    private final TextResolver textResolver;

    public NavigationReportBuilder(TextResolver textResolver) {
        this.textResolver = textResolver;
    }

    public String build(ReportData data) {
        if (data == null || !data.isLocalized || data.currentPose == null) {
            return textResolver.resolve("report_not_localized");
        }

        float camX = data.currentPose.tx();
        float camZ = data.currentPose.tz();
        StringBuilder report = new StringBuilder();

        List<NearbyNodeInfo> nearbyDoorplates = getNearestDoorplateInfos(
                camX,
                camZ,
                data.viewMatrix,
                data.doorplateNodes,
                data.nearbyNodeCount
        );

        if (!nearbyDoorplates.isEmpty()) {
            report.append(textResolver.resolve("report_nearby_intro"));
            for (int i = 0; i < nearbyDoorplates.size(); i++) {
                NearbyNodeInfo info = nearbyDoorplates.get(i);
                if (i > 0) {
                    report.append(textResolver.resolve("report_separator"));
                }
                report.append(textResolver.resolve(
                        "report_nearby_item",
                        info.node.id,
                        info.directionText,
                        formatMeters(info.distanceMeters, data.appLanguage)
                ));
            }
            report.append(textResolver.resolve("report_end"));
        } else {
            report.append(textResolver.resolve("report_no_nearby"));
        }

        if (data.currentTargetNode != null) {
            report.append(" ").append(textResolver.resolve(
                    "report_current_target", data.currentTargetNode.id));

            List<float[]> reportPathPoints = buildReportPathPoints(data);
            if (reportPathPoints.size() >= 2) {
                float remaining = computePolylineDistanceMeters(reportPathPoints);
                report.append(" ").append(textResolver.resolve(
                        "report_remaining_distance",
                        formatMeters(remaining, data.appLanguage)
                ));

                NextTurnInfo nextTurn = computeNextTurnInfo(
                        reportPathPoints, data.turnAngleThresholdDegrees);
                if (nextTurn != null) {
                    String turnText = nextTurn.direction == NavigationEngine.DIRECTION_LEFT
                            ? textResolver.resolve("report_turn_left")
                            : textResolver.resolve("report_turn_right");
                    report.append(" ").append(textResolver.resolve(
                            "report_next_turn",
                            formatMeters(nextTurn.distanceMeters, data.appLanguage),
                            turnText
                    ));
                } else {
                    report.append(" ").append(textResolver.resolve("report_keep_straight"));
                }
            } else {
                report.append(" ").append(textResolver.resolve("report_path_unavailable"));
            }
        }

        return report.toString();
    }

    private List<NearbyNodeInfo> getNearestDoorplateInfos(float camX,
                                                           float camZ,
                                                           float[] viewMatrix,
                                                           List<Node> doorplateNodes,
                                                           int maxCount) {
        List<NearbyNodeInfo> result = new ArrayList<>();
        if (doorplateNodes == null || doorplateNodes.isEmpty() || maxCount <= 0) {
            return result;
        }

        for (Node node : doorplateNodes) {
            if (node == null || node.id == null) {
                continue;
            }
            float distance = distance2d(camX, camZ, node.arX, node.arZ);
            String direction = classifyRelativeDirection(
                    node.arX, node.arY, node.arZ, viewMatrix);
            result.add(new NearbyNodeInfo(node, distance, direction));
        }

        Collections.sort(result, (a, b) -> Float.compare(a.distanceMeters, b.distanceMeters));
        if (result.size() > maxCount) {
            return new ArrayList<>(result.subList(0, maxCount));
        }
        return result;
    }

    private List<float[]> buildReportPathPoints(ReportData data) {
        List<float[]> points = new ArrayList<>();
        if (data.currentPose == null || data.currentTargetNode == null) {
            return points;
        }

        float groundY = data.groundY;
        points.add(new float[]{data.currentPose.tx(), groundY, data.currentPose.tz()});

        List<String> pathIds = new ArrayList<>();
        if (data.isNavigating
                && data.currentPathNodeIds != null
                && !data.currentPathNodeIds.isEmpty()) {
            pathIds.addAll(data.currentPathNodeIds);
        } else {
            Node startNode = NavUtils.findNearestPathNode(
                    data.currentPose.tx(),
                    data.currentPose.ty(),
                    data.currentPose.tz(),
                    data.pathNodes
            );
            if (startNode != null) {
                List<Node> computed = NavUtils.computeOrderedPath(
                        startNode,
                        data.currentTargetNode,
                        data.pathNodes,
                        data.doorplateNodes
                );
                appendDistinctNodeIds(pathIds, computed);
            }
        }

        String lastId = null;
        for (String id : pathIds) {
            if (id == null || id.equals(lastId)) {
                continue;
            }
            Node node = findNodeById(id, data.allNodes);
            if (node != null) {
                points.add(new float[]{node.arX, groundY, node.arZ});
            }
            lastId = id;
        }
        return points;
    }

    private void appendDistinctNodeIds(List<String> target, List<Node> nodes) {
        if (target == null || nodes == null) {
            return;
        }
        String lastId = null;
        for (Node node : nodes) {
            if (node == null || node.id == null || node.id.equals(lastId)) {
                continue;
            }
            target.add(node.id);
            lastId = node.id;
        }
    }

    private Node findNodeById(String id, List<Node> nodes) {
        if (id == null || nodes == null) {
            return null;
        }
        for (Node node : nodes) {
            if (node != null && id.equals(node.id)) {
                return node;
            }
        }
        return null;
    }

    private NextTurnInfo computeNextTurnInfo(List<float[]> points, float turnAngleThresholdDegrees) {
        if (points == null || points.size() < 3) {
            return null;
        }

        float accumulated = 0f;
        for (int i = 1; i < points.size() - 1; i++) {
            float[] a = points.get(i - 1);
            float[] b = points.get(i);
            float[] c = points.get(i + 1);
            if (a == null || b == null || c == null) {
                continue;
            }

            accumulated += distance2d(a[0], a[2], b[0], b[2]);

            float ax = b[0] - a[0];
            float az = b[2] - a[2];
            float bx = c[0] - b[0];
            float bz = c[2] - b[2];
            float lenA = (float) Math.sqrt(ax * ax + az * az);
            float lenB = (float) Math.sqrt(bx * bx + bz * bz);
            if (lenA < 1e-4f || lenB < 1e-4f) {
                continue;
            }

            float dot = (ax * bx + az * bz) / (lenA * lenB);
            dot = Math.max(-1f, Math.min(1f, dot));
            float angleDegrees = (float) Math.toDegrees(Math.acos(dot));
            if (angleDegrees < turnAngleThresholdDegrees) {
                continue;
            }

            float cross = ax * bz - az * bx;
            if (Math.abs(cross) < 1e-4f) {
                continue;
            }

            int direction = cross > 0f
                    ? NavigationEngine.DIRECTION_RIGHT
                    : NavigationEngine.DIRECTION_LEFT;
            return new NextTurnInfo(direction, accumulated);
        }
        return null;
    }

    private String classifyRelativeDirection(float worldX,
                                              float worldY,
                                              float worldZ,
                                              float[] viewMatrix) {
        if (viewMatrix == null || viewMatrix.length < 16) {
            return textResolver.resolve("direction_nearby");
        }

        float[] point = {worldX, worldY, worldZ, 1.0f};
        float[] cameraSpace = new float[4];
        Matrix.multiplyMV(cameraSpace, 0, viewMatrix, 0, point, 0);

        float x = cameraSpace[0];
        float z = cameraSpace[2];
        if (z > 0f) {
            if (Math.abs(x) < 0.35f) {
                return textResolver.resolve("direction_back");
            }
            return x < 0f
                    ? textResolver.resolve("direction_left_back")
                    : textResolver.resolve("direction_right_back");
        }

        if (Math.abs(x) < 0.35f) {
            return textResolver.resolve("direction_front");
        }
        return x < 0f
                ? textResolver.resolve("direction_left_front")
                : textResolver.resolve("direction_right_front");
    }

    private float computePolylineDistanceMeters(List<float[]> points) {
        if (points == null || points.size() < 2) {
            return 0f;
        }
        float total = 0f;
        for (int i = 1; i < points.size(); i++) {
            float[] a = points.get(i - 1);
            float[] b = points.get(i);
            if (a != null && b != null) {
                total += distance2d(a[0], a[2], b[0], b[2]);
            }
        }
        return total;
    }

    private float distance2d(float x1, float z1, float x2, float z2) {
        float dx = x1 - x2;
        float dz = z1 - z2;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private String formatMeters(float meters, InteractionConfigManager.AppLanguage language) {
        if (meters < 0f) {
            return textResolver.resolve("meters_unknown");
        }
        InteractionConfigManager.AppLanguage safeLanguage =
                language == null ? InteractionConfigManager.AppLanguage.ZH : language;
        Locale locale = safeLanguage == InteractionConfigManager.AppLanguage.EN
                ? Locale.US
                : Locale.CHINA;
        String unit = safeLanguage == InteractionConfigManager.AppLanguage.EN ? " m" : "米";
        if (meters < 10f) {
            return String.format(locale, "%.1f%s", meters, unit);
        }
        return String.format(locale, "%.0f%s", meters, unit);
    }

    public static final class ReportData {
        public final boolean isLocalized;
        public final boolean isNavigating;
        public final Pose currentPose;
        public final float[] viewMatrix;
        public final List<Node> allNodes;
        public final List<Node> doorplateNodes;
        public final List<Node> pathNodes;
        public final Node currentTargetNode;
        public final List<String> currentPathNodeIds;
        public final float groundY;
        public final int nearbyNodeCount;
        public final float turnAngleThresholdDegrees;
        public final InteractionConfigManager.AppLanguage appLanguage;

        public ReportData(boolean isLocalized,
                          boolean isNavigating,
                          Pose currentPose,
                          float[] viewMatrix,
                          List<Node> allNodes,
                          List<Node> doorplateNodes,
                          List<Node> pathNodes,
                          Node currentTargetNode,
                          List<String> currentPathNodeIds,
                          float groundY,
                          int nearbyNodeCount,
                          float turnAngleThresholdDegrees,
                          InteractionConfigManager.AppLanguage appLanguage) {
            this.isLocalized = isLocalized;
            this.isNavigating = isNavigating;
            this.currentPose = currentPose;
            this.viewMatrix = viewMatrix;
            this.allNodes = allNodes;
            this.doorplateNodes = doorplateNodes;
            this.pathNodes = pathNodes;
            this.currentTargetNode = currentTargetNode;
            this.currentPathNodeIds = currentPathNodeIds;
            this.groundY = groundY;
            this.nearbyNodeCount = nearbyNodeCount;
            this.turnAngleThresholdDegrees = turnAngleThresholdDegrees;
            this.appLanguage = appLanguage;
        }
    }

    private static final class NearbyNodeInfo {
        final Node node;
        final float distanceMeters;
        final String directionText;

        NearbyNodeInfo(Node node, float distanceMeters, String directionText) {
            this.node = node;
            this.distanceMeters = distanceMeters;
            this.directionText = directionText;
        }
    }

    private static final class NextTurnInfo {
        final int direction;
        final float distanceMeters;

        NextTurnInfo(int direction, float distanceMeters) {
            this.direction = direction;
            this.distanceMeters = distanceMeters;
        }
    }
}
