package com.google.ar.core.examples.java.nav.navigation;

/**
 * 对窄门几何证据做连续帧确认，并限制同一扇门的重复播报。
 */
public final class NarrowDoorAlertController {
    private static final long CONFIRMATION_MS = 600L;
    private static final int MIN_CONFIRMATION_SAMPLES = 2;
    private static final long MAX_EVIDENCE_AGE_MS = 1200L;
    private static final float MAX_ALERT_DISTANCE_METERS = 4.5f;

    private long candidateSinceMs = -1L;
    private int candidateSampleCount = 0;
    private boolean alertLatched = false;

    public AlertDecision update(DepthDoorwayAnalyzer.DoorEvidence evidence, long nowMs) {
        boolean usable = evidence != null
                && evidence.available
                && evidence.qualitySufficient
                && evidence.narrowDoor
                && nowMs >= evidence.sampleTimeMs
                && nowMs - evidence.sampleTimeMs <= MAX_EVIDENCE_AGE_MS;

        if (!usable) {
            candidateSinceMs = -1L;
            candidateSampleCount = 0;
            alertLatched = false;
            return AlertDecision.none();
        }

        if (candidateSinceMs < 0L || nowMs < candidateSinceMs) {
            candidateSinceMs = nowMs;
            candidateSampleCount = 1;
        } else {
            candidateSampleCount++;
        }

        boolean confirmed = candidateSampleCount >= MIN_CONFIRMATION_SAMPLES
                && nowMs - candidateSinceMs >= CONFIRMATION_MS
                && evidence.distanceMeters > 0f
                && evidence.distanceMeters <= MAX_ALERT_DISTANCE_METERS;
        if (confirmed && !alertLatched) {
            alertLatched = true;
            return new AlertDecision(true, evidence.widthMeters, evidence.distanceMeters);
        }
        return AlertDecision.none();
    }

    public void reset() {
        candidateSinceMs = -1L;
        candidateSampleCount = 0;
        alertLatched = false;
    }

    public static final class AlertDecision {
        public final boolean shouldAlert;
        public final float widthMeters;
        public final float distanceMeters;

        private AlertDecision(boolean shouldAlert, float widthMeters, float distanceMeters) {
            this.shouldAlert = shouldAlert;
            this.widthMeters = widthMeters;
            this.distanceMeters = distanceMeters;
        }

        static AlertDecision none() {
            return new AlertDecision(false, -1f, -1f);
        }
    }
}
