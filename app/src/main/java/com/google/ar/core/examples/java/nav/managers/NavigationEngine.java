package com.google.ar.core.examples.java.nav.managers;

import android.opengl.Matrix;
import android.util.Log;

import java.util.List;

/**
 * 核心路径引导与偏航纠正引擎
 *
 * 本版修复：
 * 1. 过滤楼道中的小折点/地图瑕疵，避免误报转弯；
 * 2. 将“预播报”和“正式播报”拆成两个独立事件；
 * 3. 正式播报与朝向引导分离，避免互相打架；
 * 4. 正式播报触发后，短时间抑制 heading guidance，避免“右转后马上左转”；
 * 5. 转弯提示只基于服务器路径节点前后夹角触发，不使用“当前人位置 -> 下一个节点”的夹角；
 * 6. 增大转弯提前量。
 */
public class NavigationEngine {
    private static final String TAG = "NavigationEngine";

    public static final int DIRECTION_STRAIGHT = 0;
    public static final int DIRECTION_LEFT = 1;
    public static final int DIRECTION_RIGHT = 2;

    private static final float DEFAULT_VEER_THRESHOLD_METERS = 1.2f;
    private static final float DEFAULT_VEER_RECOVERY_THRESHOLD_METERS = 0.65f;
    private static final float WAYPOINT_REACHED_THRESHOLD_METERS = 1.2f;
    private static final float DESTINATION_REACHED_THRESHOLD_METERS = 1.5f;

    // 转弯提前量继续增大
    private static final float TURN_PREANNOUNCE_DISTANCE_METERS = 8.0f;
    private static final float TURN_NOW_DISTANCE_METERS = 1.2f;

    // 小折点过滤
    private static final float SIGNIFICANT_TURN_ANGLE_THRESHOLD_DEGREES = 42f;
    private static final float MIN_INCOMING_SEGMENT_LENGTH_METERS = 1.0f;
    private static final float MIN_OUTGOING_VECTOR_LENGTH_METERS = 1.35f;
    private static final float MIN_TURN_LATERAL_OFFSET_METERS = 0.85f;
    private static final int MAX_TURN_LOOKAHEAD_POINTS = 3;

    private static final float CAMERA_STRAIGHT_DEADZONE = 0.55f;

    private static final float PARALLEL_VEER_SUPPRESSION_DISTANCE_METERS = 2.6f;
    private static final float PARALLEL_HEADING_ANGLE_THRESHOLD_DEGREES = 10f;
    private static final float CORRIDOR_SEVERE_VEER_THRESHOLD_METERS = 2.0f;

    private static final long PRE_TURN_SPEECH_COOLDOWN_MS = 3500L;
    private static final long TURN_NOW_SPEECH_COOLDOWN_MS = 2500L;
    private static final long HEADING_GUIDANCE_COOLDOWN_MS = 4500L;
    private static final long STRAIGHT_GUIDANCE_COOLDOWN_MS = 12000L;
    private static final long VEER_SPEECH_COOLDOWN_MS = 1500L;
    private static final long VEER_RECOVERED_COOLDOWN_MS = 1200L;

    // 正式转弯后，临时压住朝向播报
    private static final long POST_TURN_HEADING_SUPPRESSION_MS = 2600L;

    private int currentPathIndex = 1;
    private float veerThresholdMeters = DEFAULT_VEER_THRESHOLD_METERS;
    private float veerRecoveryThresholdMeters = DEFAULT_VEER_RECOVERY_THRESHOLD_METERS;
    private boolean corridorMode = false;

    private int lastHeadingGuidanceDirection = -1;
    private int lastVeerInstructionDirection = -1;

    private int lastPreAnnouncedTurnNodeIndex = -1;
    private int lastTurnNowNodeIndex = -1;

    private long lastPreTurnSpeechTime = 0L;
    private long lastTurnNowSpeechTime = 0L;
    private long lastHeadingSpeechTime = 0L;
    private long lastVeerSpeechTime = 0L;
    private long lastVeerRecoveredTime = 0L;
    private long suppressHeadingUntilTime = 0L;

    private static final float RESUME_NEAREST_NODE_MAX_DISTANCE_METERS = 4.2f;
    private static final float RESUME_NODE_ADVANTAGE_METERS = 0.75f;
    private static final float RESUME_PASSED_ALONG_ROUTE_METERS = 0.30f;
    private static final int RESUME_LOOKAHEAD_POINTS = 5;

    private static final float ROUTE_CENTER_ENTRY_THRESHOLD_METERS = 0.12f;
    private static final float ROUTE_CENTER_EXIT_THRESHOLD_METERS = 0.42f;

    private boolean isInsideRouteCenterBand = true;
    private boolean hasPendingVeerRecoveryNotice = false;

    /**
     * 最近一次已通过的“服务器路径节点”。
     * 用它来保证转弯角度始终由服务器节点之间测得，
     * 即使 pathPoints[0] 始终是当前人位置、且旧路径已经被裁掉。
     */
    private float[] lastPassedServerNode = null;

    /**
     * 本次导航开始时的固定路线起点。在尚未通过第一个服务器节点前，
     * pathPoints[0] 可能是随相机实时更新的绘制点，不能用它作为偏移基准。
     */
    private float[] initialRouteReferencePoint = null;

    /**
     * 为兼容旧 Activity 暂时保留，但 update() 已不再被它阻塞。
     */
    public volatile boolean isSpeakingNavIntro = false;

    public interface NavigationCallback {
        void onArrived();
        void onPreTurnInstruction(int direction, float distanceMeters);
        void onTurnNow(int direction);
        void onHeadingGuidance(int direction);
        void onVeerDetected(int veerDirection, float lateralOffsetMeters);
        void onVeerRecovered();
    }

    private static class UpcomingTurn {
        int direction = DIRECTION_STRAIGHT;
        float angleDegrees = 0f;
        float lateralOffsetMeters = 0f;
        float incomingLengthMeters = 0f;
        float outgoingLengthMeters = 0f;
        int turnNodeIndex = -1;
        int lookaheadEndIndex = -1;
    }

    public void applyVeerSensitivity(InteractionConfigManager.HapticSensitivity sensitivity) {
        InteractionConfigManager.HapticSensitivity safe = sensitivity == null
                ? InteractionConfigManager.HapticSensitivity.MEDIUM
                : sensitivity;
        switch (safe) {
            case HIGH:
                veerThresholdMeters = 0.95f;
                veerRecoveryThresholdMeters = 0.55f;
                break;
            case LOW:
                veerThresholdMeters = 1.55f;
                veerRecoveryThresholdMeters = 0.90f;
                break;
            case MEDIUM:
            default:
                veerThresholdMeters = DEFAULT_VEER_THRESHOLD_METERS;
                veerRecoveryThresholdMeters = DEFAULT_VEER_RECOVERY_THRESHOLD_METERS;
                break;
        }
        Log.d(TAG, "applyVeerSensitivity: level=" + safe.name()
                + ", veerThreshold=" + veerThresholdMeters
                + ", recoveryThreshold=" + veerRecoveryThresholdMeters);
    }

    public void startNewNavigation() {
        currentPathIndex = 1;
        corridorMode = false;

        lastHeadingGuidanceDirection = -1;
        lastVeerInstructionDirection = -1;

        lastPreAnnouncedTurnNodeIndex = -1;
        lastTurnNowNodeIndex = -1;

        lastPreTurnSpeechTime = 0L;
        lastTurnNowSpeechTime = 0L;
        lastHeadingSpeechTime = 0L;
        lastVeerSpeechTime = 0L;
        lastVeerRecoveredTime = 0L;
        suppressHeadingUntilTime = 0L;

        lastPassedServerNode = null;
        initialRouteReferencePoint = null;

        isInsideRouteCenterBand = true;
        hasPendingVeerRecoveryNotice = false;

        isSpeakingNavIntro = false;
        Log.d(TAG, "startNewNavigation: state reset");
    }

    /**
     * 设置楼道低提醒模式。楼道模式只降低普通方向和轻微偏航提示，
     * 转弯、到达和严重偏航仍由 update() 正常处理。
     */
    public void setCorridorMode(boolean enabled) {
        if (corridorMode == enabled) return;

        corridorMode = enabled;
        lastHeadingGuidanceDirection = -1;
        lastVeerInstructionDirection = -1;
        hasPendingVeerRecoveryNotice = false;
        isInsideRouteCenterBand = true;

        if (!enabled) {
            lastHeadingSpeechTime = 0L;
        }

        Log.d(TAG, "corridor_mode=" + enabled);
    }

    public boolean isCorridorMode() {
        return corridorMode;
    }

    /**
     * 设置本次导航的固定初始路线基准。调用方应在 startNewNavigation() 后设置。
     */
    public void setInitialRouteReferencePoint(float[] point) {
        initialRouteReferencePoint = clonePoint(point);
    }

    /**
     * 返回当前路线段的固定起点。为空时表示尚未通过任何服务器路径节点，
     * 调用方应使用当前路径首点作为初始参考。
     */
    public float[] getRouteReferencePoint() {
        if (lastPassedServerNode != null) {
            return clonePoint(lastPassedServerNode);
        }
        return clonePoint(initialRouteReferencePoint);
    }

    public void update(float currentX, float currentY, float currentZ,
                       List<float[]> pathPoints, float[] viewMatrix, NavigationCallback callback) {
        if (callback == null || pathPoints == null || pathPoints.size() < 2) {
            return;
        }

        long now = System.currentTimeMillis();
        currentPathIndex = Math.max(1, Math.min(currentPathIndex, pathPoints.size() - 1));

        if (currentPathIndex >= pathPoints.size()) {
            callback.onArrived();
            return;
        }

        // A. 先在“推进已到达节点”之前检查一次正式转弯，
        // 防止拐点在 1.2m 阈值里被先消费掉，导致 turn_now 永远不触发。
        float[] target = pathPoints.get(currentPathIndex);
        float distanceToTarget = distance2d(currentX, currentZ, target[0], target[2]);
        UpcomingTurn upcomingTurn = analyzeUpcomingTurn(pathPoints, currentPathIndex);

        if (upcomingTurn.direction != DIRECTION_STRAIGHT
                && distanceToTarget <= TURN_NOW_DISTANCE_METERS
                && (lastTurnNowNodeIndex != upcomingTurn.turnNodeIndex
                || now - lastTurnNowSpeechTime >= TURN_NOW_SPEECH_COOLDOWN_MS)) {

            lastTurnNowNodeIndex = upcomingTurn.turnNodeIndex;
            lastPreAnnouncedTurnNodeIndex = upcomingTurn.turnNodeIndex;
            lastTurnNowSpeechTime = now;
            suppressHeadingUntilTime = now + POST_TURN_HEADING_SUPPRESSION_MS;
            lastHeadingGuidanceDirection = -1;

            Log.d(TAG, "turn_now(pre-consume): direction=" + directionToName(upcomingTurn.direction)
                    + ", distanceToTurn=" + distanceToTarget
                    + ", turnNodeIndex=" + upcomingTurn.turnNodeIndex);

            callback.onTurnNow(upcomingTurn.direction);
            return;
        }

        // B. 推进真正已经到达的节点
        while (currentPathIndex < pathPoints.size()) {
            float[] currentTarget = pathPoints.get(currentPathIndex);
            float threshold = currentPathIndex == pathPoints.size() - 1
                    ? DESTINATION_REACHED_THRESHOLD_METERS
                    : WAYPOINT_REACHED_THRESHOLD_METERS;

            if (distance2d(currentX, currentZ, currentTarget[0], currentTarget[2]) > threshold) {
                break;
            }

            lastPassedServerNode = clonePoint(currentTarget);

            if (currentPathIndex == lastTurnNowNodeIndex) {
                suppressHeadingUntilTime = Math.max(
                        suppressHeadingUntilTime,
                        now + POST_TURN_HEADING_SUPPRESSION_MS
                );
            }

            currentPathIndex++;
            lastVeerInstructionDirection = -1;
            hasPendingVeerRecoveryNotice = false;
            isInsideRouteCenterBand = true;
        }

        if (currentPathIndex >= pathPoints.size()) {
            Log.d(TAG, "arrived: reached final waypoint");
            callback.onArrived();
            return;
        }

        target = pathPoints.get(currentPathIndex);
        distanceToTarget = distance2d(currentX, currentZ, target[0], target[2]);
        upcomingTurn = analyzeUpcomingTurn(pathPoints, currentPathIndex);

        // C. 当前目标如果本身就是受保护的转弯节点，则禁止 resume 逻辑把它跳过去
        if (!isProtectedTurnTarget(upcomingTurn, distanceToTarget)) {
            boolean resumedToLaterNode = maybeResumeToNearestUpcomingNode(
                    currentX, currentZ, pathPoints, viewMatrix
            );

            if (resumedToLaterNode) {
                Log.d(TAG, "resume_to_nearest_upcoming_node: currentPathIndex=" + currentPathIndex);
            }

            if (currentPathIndex >= pathPoints.size()) {
                Log.d(TAG, "arrived after resume");
                callback.onArrived();
                return;
            }

            target = pathPoints.get(currentPathIndex);
            distanceToTarget = distance2d(currentX, currentZ, target[0], target[2]);
            upcomingTurn = analyzeUpcomingTurn(pathPoints, currentPathIndex);
        }

        float[] segmentStart = getCurrentSegmentStart(pathPoints);

        // D. 正式转弯优先级最高
        if (upcomingTurn.direction != DIRECTION_STRAIGHT
                && distanceToTarget <= TURN_NOW_DISTANCE_METERS
                && (lastTurnNowNodeIndex != upcomingTurn.turnNodeIndex
                || now - lastTurnNowSpeechTime >= TURN_NOW_SPEECH_COOLDOWN_MS)) {

            lastTurnNowNodeIndex = upcomingTurn.turnNodeIndex;
            lastPreAnnouncedTurnNodeIndex = upcomingTurn.turnNodeIndex;
            lastTurnNowSpeechTime = now;
            suppressHeadingUntilTime = now + POST_TURN_HEADING_SUPPRESSION_MS;
            lastHeadingGuidanceDirection = -1;

            Log.d(TAG, "turn_now: direction=" + directionToName(upcomingTurn.direction)
                    + ", distanceToTurn=" + distanceToTarget
                    + ", turnNodeIndex=" + upcomingTurn.turnNodeIndex);

            callback.onTurnNow(upcomingTurn.direction);
            return;
        }

        float signedLateralOffset = computeSignedLateralOffsetMeters(currentX, currentZ, segmentStart, target);
        float lateralOffset = Math.abs(signedLateralOffset);

        boolean suppressVeerForParallelPath = shouldSuppressVeerForParallelPath(
                lateralOffset,
                segmentStart,
                target,
                viewMatrix
        );
        boolean severeVeer = lateralOffset >= CORRIDOR_SEVERE_VEER_THRESHOLD_METERS;
        boolean suppressVeerForCorridor = corridorMode && !severeVeer;

        // 中心带状态更新：只有明显偏离了才算离开“路线中央”
        if (isInsideRouteCenterBand) {
            if (lateralOffset >= ROUTE_CENTER_EXIT_THRESHOLD_METERS) {
                isInsideRouteCenterBand = false;
            }
        }

        // 偏航提示：真正发出“向左/向右调整”后，才允许后续恢复提示
        if (suppressVeerForCorridor) {
            lastVeerInstructionDirection = -1;
            hasPendingVeerRecoveryNotice = false;
        }

        if (!suppressVeerForCorridor
                && (!suppressVeerForParallelPath || severeVeer)
                && lateralOffset >= veerThresholdMeters) {
            isInsideRouteCenterBand = false;

            int veerDirection = signedLateralOffset > 0 ? DIRECTION_RIGHT : DIRECTION_LEFT;
            if (veerDirection != lastVeerInstructionDirection
                    || now - lastVeerSpeechTime >= VEER_SPEECH_COOLDOWN_MS) {
                lastVeerInstructionDirection = veerDirection;
                lastVeerSpeechTime = now;
                hasPendingVeerRecoveryNotice = true;

                Log.d(TAG, "veer_detected: direction=" + directionToName(veerDirection)
                        + ", offset=" + lateralOffset + "m, segmentIndex=" + currentPathIndex);

                callback.onVeerDetected(veerDirection, lateralOffset);
                return;
            }
        }

        // 恢复提示：必须满足两个条件
        // 1) 前面真的播报过偏航纠正
        // 2) 现在真的回到更严格的中心带内
        if (!suppressVeerForCorridor
                && !suppressVeerForParallelPath
                && hasPendingVeerRecoveryNotice
                && lateralOffset <= ROUTE_CENTER_ENTRY_THRESHOLD_METERS
                && now - lastVeerRecoveredTime >= VEER_RECOVERED_COOLDOWN_MS) {

            isInsideRouteCenterBand = true;
            hasPendingVeerRecoveryNotice = false;
            lastVeerInstructionDirection = -1;
            lastVeerRecoveredTime = now;

            Log.d(TAG, "route_centered: offset=" + lateralOffset
                    + "m, segmentIndex=" + currentPathIndex);

            callback.onVeerRecovered();
            return;
        }

        // 只清偏航状态，不播报恢复
        if (lastVeerInstructionDirection != -1
                && (suppressVeerForParallelPath || lateralOffset <= veerRecoveryThresholdMeters)) {
            lastVeerInstructionDirection = -1;
            Log.d(TAG, "veer_state_cleared: offset=" + lateralOffset
                    + "m, parallelSuppressed=" + suppressVeerForParallelPath
                    + ", segmentIndex=" + currentPathIndex);
        }

        // 预播报
        if (upcomingTurn.direction != DIRECTION_STRAIGHT
                && distanceToTarget <= TURN_PREANNOUNCE_DISTANCE_METERS
                && lastTurnNowNodeIndex != upcomingTurn.turnNodeIndex
                && (lastPreAnnouncedTurnNodeIndex != upcomingTurn.turnNodeIndex
                || now - lastPreTurnSpeechTime >= PRE_TURN_SPEECH_COOLDOWN_MS)) {

            lastPreAnnouncedTurnNodeIndex = upcomingTurn.turnNodeIndex;
            lastPreTurnSpeechTime = now;

            Log.d(TAG, "pre_turn: direction=" + directionToName(upcomingTurn.direction)
                    + ", distanceToTurn=" + distanceToTarget
                    + ", angle=" + upcomingTurn.angleDegrees
                    + ", lateral=" + upcomingTurn.lateralOffsetMeters
                    + ", turnNodeIndex=" + upcomingTurn.turnNodeIndex);

            callback.onPreTurnInstruction(upcomingTurn.direction, distanceToTarget);
            return;
        }

        // 朝向引导最低优先级
        if (corridorMode || now < suppressHeadingUntilTime) {
            return;
        }

        int headingDirection = calculateHeadingDirection(target, viewMatrix);
        boolean shouldAnnounceHeading = false;

        if (headingDirection != DIRECTION_STRAIGHT) {
            if (lastHeadingGuidanceDirection == -1
                    || headingDirection != lastHeadingGuidanceDirection) {
                if (lastHeadingSpeechTime == 0L
                        || now - lastHeadingSpeechTime >= HEADING_GUIDANCE_COOLDOWN_MS) {
                    shouldAnnounceHeading = true;
                }
            } else if (now - lastHeadingSpeechTime >= HEADING_GUIDANCE_COOLDOWN_MS) {
                shouldAnnounceHeading = true;
            }
        } else {
            if (lastHeadingGuidanceDirection != DIRECTION_STRAIGHT) {
                if (lastHeadingSpeechTime == 0L
                        || now - lastHeadingSpeechTime >= STRAIGHT_GUIDANCE_COOLDOWN_MS) {
                    shouldAnnounceHeading = true;
                }
            } else if (lastHeadingSpeechTime == 0L) {
                shouldAnnounceHeading = true;
            }
        }

        if (shouldAnnounceHeading) {
            lastHeadingGuidanceDirection = headingDirection;
            lastHeadingSpeechTime = now;

            Log.d(TAG, "heading_guidance: direction=" + directionToName(headingDirection)
                    + ", distanceToTarget=" + distanceToTarget + "m, targetIndex=" + currentPathIndex);

            callback.onHeadingGuidance(headingDirection);
        }
    }

    /**
     * 只使用服务器路径节点来分析 upcoming turn：
     * - turnNodeIndex 对应当前目标服务器节点
     * - 它的前一个点必须也是服务器节点，不能用当前人位置代替
     * - 若当前 turnNodeIndex == 1，则必须依赖 lastPassedServerNode 作为前一个服务器节点
     */
    private UpcomingTurn analyzeUpcomingTurn(List<float[]> pathPoints, int turnNodeIndex) {
        UpcomingTurn result = new UpcomingTurn();
        result.turnNodeIndex = turnNodeIndex;

        if (pathPoints == null
                || turnNodeIndex <= 0
                || turnNodeIndex >= pathPoints.size() - 1) {
            return result;
        }

        float[] prevServerNode;
        if (turnNodeIndex >= 2) {
            prevServerNode = pathPoints.get(turnNodeIndex - 1);
        } else {
            // turnNodeIndex == 1 时，pathPoints[0] 是当前人位置，不能用于转弯分析
            if (lastPassedServerNode == null) {
                return result;
            }
            prevServerNode = lastPassedServerNode;
        }

        float[] turn = pathPoints.get(turnNodeIndex);

        float ax = turn[0] - prevServerNode[0];
        float az = turn[2] - prevServerNode[2];
        float lenA = (float) Math.sqrt(ax * ax + az * az);
        result.incomingLengthMeters = lenA;

        if (lenA < MIN_INCOMING_SEGMENT_LENGTH_METERS) {
            return result;
        }

        int lookaheadEnd = turnNodeIndex + 1;
        float accumulatedOutgoingLength = 0f;
        float[] walkPrev = turn;

        for (int i = turnNodeIndex + 1;
             i < pathPoints.size() && i <= turnNodeIndex + MAX_TURN_LOOKAHEAD_POINTS;
             i++) {
            float[] p = pathPoints.get(i);
            accumulatedOutgoingLength += distance2d(walkPrev[0], walkPrev[2], p[0], p[2]);
            lookaheadEnd = i;
            walkPrev = p;

            if (accumulatedOutgoingLength >= MIN_OUTGOING_VECTOR_LENGTH_METERS) {
                break;
            }
        }

        float[] outEnd = pathPoints.get(lookaheadEnd);
        float bx = outEnd[0] - turn[0];
        float bz = outEnd[2] - turn[2];
        float lenB = (float) Math.sqrt(bx * bx + bz * bz);
        result.outgoingLengthMeters = lenB;
        result.lookaheadEndIndex = lookaheadEnd;

        if (lenB < MIN_OUTGOING_VECTOR_LENGTH_METERS) {
            return result;
        }

        float dot = (ax * bx + az * bz) / (lenA * lenB);
        dot = Math.max(-1f, Math.min(1f, dot));
        float angleDegrees = (float) Math.toDegrees(Math.acos(dot));
        result.angleDegrees = angleDegrees;

        float cross = ax * bz - az * bx;
        if (Math.abs(cross) < 1e-4f) {
            return result;
        }

        float lateralOffset = Math.abs(cross) / lenA;
        result.lateralOffsetMeters = lateralOffset;

        if (angleDegrees < SIGNIFICANT_TURN_ANGLE_THRESHOLD_DEGREES) {
            return result;
        }

        if (lateralOffset < MIN_TURN_LATERAL_OFFSET_METERS) {
            return result;
        }

        result.direction = cross > 0 ? DIRECTION_RIGHT : DIRECTION_LEFT;
        return result;
    }

    private boolean isProtectedTurnTarget(UpcomingTurn turn, float distanceToTarget) {
        if (turn == null || turn.direction == DIRECTION_STRAIGHT) {
            return false;
        }
        // 进入预播报窗口后，禁止 resume 逻辑把这个转弯节点跳过去
        return distanceToTarget <= (TURN_PREANNOUNCE_DISTANCE_METERS + 1.0f);
    }

    private boolean maybeResumeToNearestUpcomingNode(float currentX, float currentZ,
                                                     List<float[]> pathPoints,
                                                     float[] viewMatrix) {
        if (pathPoints == null || pathPoints.size() < 3) {
            return false;
        }
        if (currentPathIndex >= pathPoints.size() - 1) {
            return false; // 终点前不再向后跳
        }

        float[] currentTarget = pathPoints.get(currentPathIndex);
        float[] nextTarget = currentPathIndex + 1 < pathPoints.size()
                ? pathPoints.get(currentPathIndex + 1)
                : null;

        float currentTargetDistance = distance2d(currentX, currentZ, currentTarget[0], currentTarget[2]);

        int bestIndex = findNearestUpcomingNodeIndex(currentX, currentZ, pathPoints, currentPathIndex + 1);
        if (bestIndex <= currentPathIndex) {
            return false;
        }

        float[] bestNode = pathPoints.get(bestIndex);
        float bestDistance = distance2d(currentX, currentZ, bestNode[0], bestNode[2]);

        if (bestDistance > RESUME_NEAREST_NODE_MAX_DISTANCE_METERS) {
            return false;
        }

        boolean currentTargetBehind = isPointBehindCamera(currentTarget, viewMatrix);
        boolean passedTowardNext = hasPassedCurrentTargetTowardNext(currentX, currentZ, currentTarget, nextTarget);

        boolean significantlyBetter = bestDistance + RESUME_NODE_ADVANTAGE_METERS < currentTargetDistance;

        if (!(currentTargetBehind || passedTowardNext)) {
            return false;
        }
        if (!significantlyBetter) {
            return false;
        }

        Log.d(TAG, "resume_candidate_accepted: oldIndex=" + currentPathIndex
                + ", newIndex=" + bestIndex
                + ", currentTargetDistance=" + currentTargetDistance
                + ", bestDistance=" + bestDistance
                + ", targetBehind=" + currentTargetBehind
                + ", passedTowardNext=" + passedTowardNext);

// 关键修复：跳接到后续节点时，同时补上“它前一个服务器节点”
        if (bestIndex - 1 >= 1 && bestIndex - 1 < pathPoints.size()) {
            lastPassedServerNode = clonePoint(pathPoints.get(bestIndex - 1));
        } else {
            lastPassedServerNode = clonePoint(currentTarget);
        }

        currentPathIndex = bestIndex;

// 目标切换后，把旧的瞬时状态清掉，避免延续对旧节点的判断
        lastVeerInstructionDirection = -1;
        lastHeadingGuidanceDirection = -1;
        lastPreAnnouncedTurnNodeIndex = -1;
        lastTurnNowNodeIndex = -1;
        suppressHeadingUntilTime = 0L;

        return true;
    }

    private int findNearestUpcomingNodeIndex(float currentX, float currentZ,
                                             List<float[]> pathPoints,
                                             int fromIndex) {
        if (pathPoints == null || fromIndex >= pathPoints.size()) {
            return -1;
        }

        int end = Math.min(pathPoints.size() - 1, fromIndex + RESUME_LOOKAHEAD_POINTS - 1);
        int bestIndex = -1;
        float bestDistance = Float.MAX_VALUE;

        for (int i = fromIndex; i <= end; i++) {
            float[] p = pathPoints.get(i);
            if (p == null || p.length < 3) continue;

            float d = distance2d(currentX, currentZ, p[0], p[2]);
            if (d < bestDistance) {
                bestDistance = d;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private boolean hasPassedCurrentTargetTowardNext(float currentX, float currentZ,
                                                     float[] currentTarget,
                                                     float[] nextTarget) {
        if (currentTarget == null || nextTarget == null) {
            return false;
        }

        float vx = nextTarget[0] - currentTarget[0];
        float vz = nextTarget[2] - currentTarget[2];
        float len = (float) Math.sqrt(vx * vx + vz * vz);
        if (len < 1e-4f) {
            return false;
        }

        float ux = currentX - currentTarget[0];
        float uz = currentZ - currentTarget[2];

        // 沿着“当前目标 -> 下一节点”的方向，判断是否已经越过当前目标
        float alongMeters = (ux * vx + uz * vz) / len;
        return alongMeters > RESUME_PASSED_ALONG_ROUTE_METERS;
    }

    private boolean isPointBehindCamera(float[] worldPoint, float[] viewMatrix) {
        if (worldPoint == null || viewMatrix == null || viewMatrix.length < 16) {
            return false;
        }

        float[] pointVec = {worldPoint[0], worldPoint[1], worldPoint[2], 1.0f};
        float[] pointInCameraSpace = new float[4];
        Matrix.multiplyMV(pointInCameraSpace, 0, viewMatrix, 0, pointVec, 0);

        // OpenGL 相机空间里 z > 0 表示点在相机后方
        return pointInCameraSpace[2] > 0f;
    }

    private int calculateHeadingDirection(float[] targetPointWorld, float[] viewMatrix) {
        if (targetPointWorld == null || viewMatrix == null || viewMatrix.length < 16) {
            return DIRECTION_STRAIGHT;
        }

        float[] targetVec = {targetPointWorld[0], targetPointWorld[1], targetPointWorld[2], 1.0f};
        float[] targetInCameraSpace = new float[4];
        Matrix.multiplyMV(targetInCameraSpace, 0, viewMatrix, 0, targetVec, 0);

        float x = targetInCameraSpace[0];
        float z = targetInCameraSpace[2];

        if (z > 0 || Math.abs(x) < CAMERA_STRAIGHT_DEADZONE) {
            return DIRECTION_STRAIGHT;
        }

        return x < 0 ? DIRECTION_LEFT : DIRECTION_RIGHT;
    }

    private boolean shouldSuppressVeerForParallelPath(float lateralOffset,
                                                      float[] segmentStart,
                                                      float[] segmentEnd,
                                                      float[] viewMatrix) {
        if (lateralOffset > PARALLEL_VEER_SUPPRESSION_DISTANCE_METERS) {
            return false;
        }
        if (segmentStart == null || segmentEnd == null || viewMatrix == null || viewMatrix.length < 16) {
            return false;
        }

        float pathDx = segmentEnd[0] - segmentStart[0];
        float pathDz = segmentEnd[2] - segmentStart[2];
        float pathLen = (float) Math.sqrt(pathDx * pathDx + pathDz * pathDz);
        if (pathLen < 1e-4f) {
            return false;
        }

        float[] cameraForwardXZ = getCameraForwardXZ(viewMatrix);
        if (cameraForwardXZ == null) {
            return false;
        }

        float pathDirX = pathDx / pathLen;
        float pathDirZ = pathDz / pathLen;

        float dot = pathDirX * cameraForwardXZ[0] + pathDirZ * cameraForwardXZ[1];
        dot = Math.max(-1f, Math.min(1f, dot));

        // 绝对值表示“同向平行”或“反向平行”都算近乎平行
        float parallelCos = Math.abs(dot);
        float angleDegrees = (float) Math.toDegrees(Math.acos(parallelCos));

        boolean isParallel = angleDegrees <= PARALLEL_HEADING_ANGLE_THRESHOLD_DEGREES;
        if (isParallel) {
            Log.d(TAG, "veer_suppressed_parallel: lateral=" + lateralOffset
                    + "m, angle=" + angleDegrees
                    + "deg, threshold=" + PARALLEL_VEER_SUPPRESSION_DISTANCE_METERS + "m");
        }
        return isParallel;
    }

    private float[] getCameraForwardXZ(float[] viewMatrix) {
        if (viewMatrix == null || viewMatrix.length < 16) return null;

        // viewMatrix 是刚体变换；不需要求逆即可得到相机在世界坐标中的水平前向量。
        float dx = -viewMatrix[2];
        float dz = -viewMatrix[10];
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f) return null;

        return new float[]{dx / len, dz / len};
    }

    private float computeSignedLateralOffsetMeters(float currentX, float currentZ,
                                                   float[] segmentStart, float[] segmentEnd) {
        if (segmentStart == null || segmentEnd == null) return 0f;

        float sx = segmentStart[0];
        float sz = segmentStart[2];
        float ex = segmentEnd[0];
        float ez = segmentEnd[2];

        float dx = ex - sx;
        float dz = ez - sz;
        float lengthSq = dx * dx + dz * dz;
        if (lengthSq < 1e-4f) return 0f;

        float px = currentX - sx;
        float pz = currentZ - sz;
        float projection = (px * dx + pz * dz) / lengthSq;
        float clampedProjection = Math.max(0f, Math.min(1f, projection));

        float nearestX = sx + clampedProjection * dx;
        float nearestZ = sz + clampedProjection * dz;
        float distance = distance2d(currentX, currentZ, nearestX, nearestZ);

        float cross = dx * pz - dz * px;
        if (Math.abs(cross) < 1e-4f) return 0f;

        return cross > 0 ? distance : -distance;
    }

    private float[] getCurrentSegmentStart(List<float[]> pathPoints) {
        if (currentPathIndex == 1 && lastPassedServerNode != null) {
            return lastPassedServerNode;
        }
        if (currentPathIndex == 1 && initialRouteReferencePoint != null) {
            return initialRouteReferencePoint;
        }
        return pathPoints.get(Math.max(0, currentPathIndex - 1));
    }

    private float distance2d(float x1, float z1, float x2, float z2) {
        float dx = x1 - x2;
        float dz = z1 - z2;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private float[] clonePoint(float[] src) {
        if (src == null) return null;
        if (src.length >= 3) {
            return new float[]{src[0], src[1], src[2]};
        }
        float[] copy = new float[src.length];
        System.arraycopy(src, 0, copy, 0, src.length);
        return copy;
    }

    private String directionToName(int direction) {
        switch (direction) {
            case DIRECTION_LEFT:
                return "LEFT";
            case DIRECTION_RIGHT:
                return "RIGHT";
            case DIRECTION_STRAIGHT:
            default:
                return "STRAIGHT";
        }
    }

    public int consumePassedPathPoints() {
        int passed = Math.max(0, currentPathIndex - 1);
        currentPathIndex = 1;
        return passed;
    }

    public void resetProgressCursor() {
        currentPathIndex = 1;
    }
}
