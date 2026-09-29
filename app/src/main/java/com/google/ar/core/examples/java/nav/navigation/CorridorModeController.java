package com.google.ar.core.examples.java.nav.navigation;

import java.util.List;

/**
 * 根据路线几何和 Depth 证据管理楼道低提醒状态。
 *
 * <p>“楼道”在这里是导航语义：用户沿当前通道大致前进，且空间几何表现为左右有边界、
 * 中央仍有连续空间。进入和退出都带滞后，避免单帧噪声引起状态抖动。</p>
 */
public final class CorridorModeController {
    public enum Mode {
        NORMAL,
        CORRIDOR
    }

    private static final float ENTRY_HEADING_DOT = 0.25f;
    private static final float REVERSE_HEADING_DOT = -0.10f;
    private static final float ENTRY_LATERAL_OFFSET_METERS = 1.20f;
    private static final float EXIT_LATERAL_OFFSET_METERS = 2.00f;
    private static final float MIN_ROUTE_TARGET_DISTANCE_METERS = 1.50f;

    private static final float TURN_ANGLE_THRESHOLD_DEGREES = 42f;
    private static final float MIN_TURN_LATERAL_OFFSET_METERS = 0.85f;
    private static final float MIN_OUTGOING_VECTOR_LENGTH_METERS = 1.35f;
    private static final float TURN_PREANNOUNCE_DISTANCE_METERS = 8.0f;
    private static final int MAX_TURN_LOOKAHEAD_POINTS = 3;

    private static final long ENTRY_CONFIRMATION_MS = 1500L;
    private static final long EXIT_CONFIRMATION_MS = 1000L;
    private static final int MIN_ENTRY_SAMPLES = 3;
    private static final long MAX_DEPTH_EVIDENCE_AGE_MS = 1200L;

    private Mode mode = Mode.NORMAL;
    private long candidateSinceMs = -1L;
    private int candidateSampleCount = 0;
    private long invalidSinceMs = -1L;

    public Mode getMode() {
        return mode;
    }

    public boolean isCorridorMode() {
        return mode == Mode.CORRIDOR;
    }

    public void reset() {
        mode = Mode.NORMAL;
        candidateSinceMs = -1L;
        candidateSampleCount = 0;
        invalidSinceMs = -1L;
    }

    public Decision update(float currentX,
                           float currentZ,
                           List<float[]> pathPoints,
                           float[] routeReferenceStart,
                           float[] viewMatrix,
                           DepthCorridorAnalyzer.DepthEvidence depthEvidence,
                           boolean depthSupported,
                           long nowMs) {
        RouteEvidence route = evaluateRoute(
                currentX,
                currentZ,
                pathPoints,
                routeReferenceStart,
                viewMatrix
        );

        boolean depthEvidenceUsable = depthSupported
                && depthEvidence != null
                && depthEvidence.available
                && depthEvidence.qualitySufficient
                && nowMs >= depthEvidence.sampleTimeMs
                && nowMs - depthEvidence.sampleTimeMs <= MAX_DEPTH_EVIDENCE_AGE_MS;

        // 用户选择的降级策略：深度不支持或暂时没有有效深度时，仅使用路线条件。
        boolean spatiallyEligible = !depthEvidenceUsable || depthEvidence.corridorLike;
        boolean entryEligible = route.forward
                && !route.nearTurn
                && route.lateralOffsetMeters <= ENTRY_LATERAL_OFFSET_METERS
                && route.distanceToTargetMeters > MIN_ROUTE_TARGET_DISTANCE_METERS
                && spatiallyEligible;

        boolean hardExit = route.reverse
                || route.nearTurn
                || route.lateralOffsetMeters >= EXIT_LATERAL_OFFSET_METERS;
        boolean stayEligible = route.valid
                && route.forward
                && !hardExit
                && spatiallyEligible;

        boolean changed = false;
        if (mode == Mode.NORMAL) {
            invalidSinceMs = -1L;
            if (entryEligible) {
                if (candidateSinceMs < 0L || nowMs < candidateSinceMs) {
                    candidateSinceMs = nowMs;
                    candidateSampleCount = 1;
                } else {
                    candidateSampleCount++;
                }

                if (candidateSampleCount >= MIN_ENTRY_SAMPLES
                        && nowMs - candidateSinceMs >= ENTRY_CONFIRMATION_MS) {
                    mode = Mode.CORRIDOR;
                    changed = true;
                    candidateSinceMs = -1L;
                    candidateSampleCount = 0;
                }
            } else {
                candidateSinceMs = -1L;
                candidateSampleCount = 0;
            }
        } else {
            candidateSinceMs = -1L;
            candidateSampleCount = 0;

            if (hardExit) {
                mode = Mode.NORMAL;
                changed = true;
                invalidSinceMs = -1L;
            } else if (!stayEligible) {
                if (invalidSinceMs < 0L || nowMs < invalidSinceMs) {
                    invalidSinceMs = nowMs;
                }
                if (nowMs - invalidSinceMs >= EXIT_CONFIRMATION_MS) {
                    mode = Mode.NORMAL;
                    changed = true;
                    invalidSinceMs = -1L;
                }
            } else {
                invalidSinceMs = -1L;
            }
        }

        return new Decision(
                mode,
                changed,
                route.forward,
                route.reverse,
                route.nearTurn,
                route.headingDot,
                route.lateralOffsetMeters,
                depthEvidenceUsable,
                spatiallyEligible
        );
    }

    private RouteEvidence evaluateRoute(float currentX,
                                        float currentZ,
                                        List<float[]> pathPoints,
                                        float[] routeReferenceStart,
                                        float[] viewMatrix) {
        if (pathPoints == null || pathPoints.size() < 2) {
            return RouteEvidence.invalid();
        }

        float[] current = pathPoints.get(0);
        float[] target = pathPoints.get(1);
        if (!isPoint(current) || !isPoint(target)) {
            return RouteEvidence.invalid();
        }

        float[] segmentStart = isPoint(routeReferenceStart) ? routeReferenceStart : current;
        int targetIndex = 1;
        if (distance2d(segmentStart, target) < 0.20f && pathPoints.size() >= 3) {
            // 规划刚开始时，最近 path node 可能与当前相机几乎重合。
            // 用下一段作为方向参考，避免零长度线段导致无法判断方向。
            segmentStart = target;
            targetIndex = 2;
            target = pathPoints.get(targetIndex);
            if (!isPoint(target)) return RouteEvidence.invalid();
        }

        float routeDx = target[0] - segmentStart[0];
        float routeDz = target[2] - segmentStart[2];
        float routeLength = (float) Math.sqrt(routeDx * routeDx + routeDz * routeDz);
        if (routeLength < 1e-4f) {
            return RouteEvidence.invalid();
        }

        float[] cameraForward = getCameraForwardXZ(viewMatrix);
        if (cameraForward == null) {
            return new RouteEvidence(
                    false,
                    false,
                    false,
                    Float.NaN,
                    0f,
                    distance2d(currentX, currentZ, target[0], target[2]),
                    false
            );
        }

        float headingDot = (routeDx / routeLength) * cameraForward[0]
                + (routeDz / routeLength) * cameraForward[1];
        float lateralOffset = computeLateralOffset(
                currentX, currentZ, segmentStart, target
        );
        float distanceToTarget = distance2d(currentX, currentZ, target[0], target[2]);

        return new RouteEvidence(
                headingDot >= ENTRY_HEADING_DOT,
                headingDot <= REVERSE_HEADING_DOT,
                isNearTurn(currentX, currentZ, segmentStart, targetIndex, pathPoints),
                headingDot,
                lateralOffset,
                distanceToTarget,
                true
        );
    }

    private boolean isNearTurn(float currentX,
                               float currentZ,
                               float[] routeStart,
                               int turnIndex,
                               List<float[]> pathPoints) {
        if (pathPoints == null || turnIndex <= 0 || turnIndex >= pathPoints.size() - 1) {
            return false;
        }

        float[] turn = pathPoints.get(turnIndex);
        if (!isPoint(turn)) return false;
        float incomingDx = turn[0] - routeStart[0];
        float incomingDz = turn[2] - routeStart[2];
        float incomingLength = (float) Math.sqrt(
                incomingDx * incomingDx + incomingDz * incomingDz
        );
        if (incomingLength < 1e-4f) return false;

        float[] outgoingEnd = null;
        float outgoingLength = 0f;
        float[] previous = turn;
        for (int i = turnIndex + 1;
             i < pathPoints.size() && i <= turnIndex + MAX_TURN_LOOKAHEAD_POINTS;
             i++) {
            float[] point = pathPoints.get(i);
            if (!isPoint(point)) continue;
            outgoingLength += distance2d(previous, point);
            outgoingEnd = point;
            previous = point;
            if (outgoingLength >= MIN_OUTGOING_VECTOR_LENGTH_METERS) break;
        }

        if (outgoingEnd == null || outgoingLength < MIN_OUTGOING_VECTOR_LENGTH_METERS) {
            return false;
        }

        float outgoingDx = outgoingEnd[0] - turn[0];
        float outgoingDz = outgoingEnd[2] - turn[2];
        float dot = (incomingDx * outgoingDx + incomingDz * outgoingDz)
                / (incomingLength * outgoingLength);
        dot = Math.max(-1f, Math.min(1f, dot));
        float angleDegrees = (float) Math.toDegrees(Math.acos(dot));
        float cross = incomingDx * outgoingDz - incomingDz * outgoingDx;
        float turnLateralOffset = Math.abs(cross) / incomingLength;
        float distanceToTurn = distance2d(currentX, currentZ, turn[0], turn[2]);

        return angleDegrees >= TURN_ANGLE_THRESHOLD_DEGREES
                && turnLateralOffset >= MIN_TURN_LATERAL_OFFSET_METERS
                && distanceToTurn <= TURN_PREANNOUNCE_DISTANCE_METERS;
    }

    private float computeLateralOffset(float currentX,
                                       float currentZ,
                                       float[] start,
                                       float[] end) {
        float dx = end[0] - start[0];
        float dz = end[2] - start[2];
        float lengthSq = dx * dx + dz * dz;
        if (lengthSq < 1e-4f) return Float.MAX_VALUE;

        float px = currentX - start[0];
        float pz = currentZ - start[2];
        float projection = (px * dx + pz * dz) / lengthSq;
        projection = Math.max(0f, Math.min(1f, projection));
        float nearestX = start[0] + projection * dx;
        float nearestZ = start[2] + projection * dz;
        return distance2d(currentX, currentZ, nearestX, nearestZ);
    }

    private float[] getCameraForwardXZ(float[] viewMatrix) {
        if (viewMatrix == null || viewMatrix.length < 16) return null;

        // ARCore view matrix 是刚体变换，水平前向量可以直接由旋转矩阵第三行得到。
        // 这样楼道状态判断不需要在每次采样时创建/求逆矩阵。
        float dx = -viewMatrix[2];
        float dz = -viewMatrix[10];
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-4f) return null;
        return new float[]{dx / length, dz / length};
    }

    private boolean isPoint(float[] point) {
        return point != null && point.length >= 3;
    }

    private float distance2d(float[] a, float[] b) {
        return distance2d(a[0], a[2], b[0], b[2]);
    }

    private float distance2d(float x1, float z1, float x2, float z2) {
        float dx = x1 - x2;
        float dz = z1 - z2;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static final class RouteEvidence {
        final boolean forward;
        final boolean reverse;
        final boolean nearTurn;
        final float headingDot;
        final float lateralOffsetMeters;
        final float distanceToTargetMeters;
        final boolean valid;

        RouteEvidence(boolean forward,
                      boolean reverse,
                      boolean nearTurn,
                      float headingDot,
                      float lateralOffsetMeters,
                      float distanceToTargetMeters,
                      boolean valid) {
            this.forward = forward;
            this.reverse = reverse;
            this.nearTurn = nearTurn;
            this.headingDot = headingDot;
            this.lateralOffsetMeters = lateralOffsetMeters;
            this.distanceToTargetMeters = distanceToTargetMeters;
            this.valid = valid;
        }

        static RouteEvidence invalid() {
            return new RouteEvidence(false, false, false, Float.NaN, Float.MAX_VALUE, 0f, false);
        }
    }

    public static final class Decision {
        public final Mode mode;
        public final boolean modeChanged;
        public final boolean routeDirectionForward;
        public final boolean routeDirectionReverse;
        public final boolean nearTurn;
        public final float headingDot;
        public final float lateralOffsetMeters;
        public final boolean depthEvidenceUsable;
        public final boolean spatiallyEligible;

        Decision(Mode mode,
                 boolean modeChanged,
                 boolean routeDirectionForward,
                 boolean routeDirectionReverse,
                 boolean nearTurn,
                 float headingDot,
                 float lateralOffsetMeters,
                 boolean depthEvidenceUsable,
                 boolean spatiallyEligible) {
            this.mode = mode;
            this.modeChanged = modeChanged;
            this.routeDirectionForward = routeDirectionForward;
            this.routeDirectionReverse = routeDirectionReverse;
            this.nearTurn = nearTurn;
            this.headingDot = headingDot;
            this.lateralOffsetMeters = lateralOffsetMeters;
            this.depthEvidenceUsable = depthEvidenceUsable;
            this.spatiallyEligible = spatiallyEligible;
        }
    }
}
