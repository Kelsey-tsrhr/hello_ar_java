package com.google.ar.core.examples.java.nav.navigation;

import android.media.Image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 使用 ARCore 16-bit Depth 图像提取楼道的几何证据。
 *
 * <p>本类不判断图像语义，只检查画面左右是否存在稳定的近距离边界，
 * 以及中央前方是否仍有足够的连续空间。Image 的生命周期由调用方管理。</p>
 */
public final class DepthCorridorAnalyzer {
    private static final float MIN_VALID_DEPTH_METERS = 0.30f;
    private static final float MAX_VALID_DEPTH_METERS = 6.0f;
    private static final float MAX_SIDE_WALL_DISTANCE_METERS = 3.5f;
    private static final float MIN_FORWARD_SPACE_METERS = 1.5f;
    private static final float MAX_SIDE_STANDARD_DEVIATION_METERS = 0.75f;

    private static final int REGION_ROWS = 3;
    private static final int REGION_COLUMNS = 5;
    private static final int MIN_SIDE_VALID_SAMPLES = 6;
    private static final int MIN_CENTER_VALID_SAMPLES = 6;

    private static final float LEFT_REGION_START = 0.08f;
    private static final float LEFT_REGION_END = 0.30f;
    private static final float RIGHT_REGION_START = 0.70f;
    private static final float RIGHT_REGION_END = 0.92f;
    private static final float CENTER_REGION_START = 0.38f;
    private static final float CENTER_REGION_END = 0.62f;
    private static final float REGION_TOP = 0.35f;
    private static final float REGION_BOTTOM = 0.75f;

    public DepthEvidence analyze(Image depthImage, long sampleTimeMs) {
        if (depthImage == null
                || depthImage.getPlanes() == null
                || depthImage.getPlanes().length == 0) {
            return DepthEvidence.unavailable(sampleTimeMs);
        }

        Image.Plane plane = depthImage.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        return analyzeBuffer(
                buffer,
                depthImage.getWidth(),
                depthImage.getHeight(),
                plane.getRowStride(),
                plane.getPixelStride(),
                sampleTimeMs
        );
    }

    static DepthEvidence analyzeBuffer(ByteBuffer buffer,
                                       int width,
                                       int height,
                                       int rowStride,
                                       int pixelStride,
                                       long sampleTimeMs) {
        if (buffer == null || width <= 0 || height <= 0 || rowStride <= 0) {
            return DepthEvidence.unavailable(sampleTimeMs);
        }

        int safePixelStride = pixelStride > 0 ? pixelStride : 2;
        RegionStats left = sampleRegion(
                buffer, width, height, rowStride, safePixelStride,
                LEFT_REGION_START, LEFT_REGION_END
        );
        RegionStats right = sampleRegion(
                buffer, width, height, rowStride, safePixelStride,
                RIGHT_REGION_START, RIGHT_REGION_END
        );
        RegionStats center = sampleRegion(
                buffer, width, height, rowStride, safePixelStride,
                CENTER_REGION_START, CENTER_REGION_END
        );

        boolean qualitySufficient = left.validCount >= MIN_SIDE_VALID_SAMPLES
                && right.validCount >= MIN_SIDE_VALID_SAMPLES
                && center.validCount >= MIN_CENTER_VALID_SAMPLES;
        boolean sideWallsValid = left.isSideWallCandidate() && right.isSideWallCandidate();
        boolean forwardSpaceValid = center.validCount >= MIN_CENTER_VALID_SAMPLES
                && center.medianMeters >= MIN_FORWARD_SPACE_METERS;

        return new DepthEvidence(
                true,
                qualitySufficient,
                sideWallsValid && forwardSpaceValid,
                left.medianMeters,
                right.medianMeters,
                center.medianMeters,
                left.validCount + right.validCount + center.validCount,
                sampleTimeMs
        );
    }

    private static RegionStats sampleRegion(ByteBuffer buffer,
                                            int width,
                                            int height,
                                            int rowStride,
                                            int pixelStride,
                                            float startX,
                                            float endX) {
        List<Float> distances = new ArrayList<>();

        for (int row = 0; row < REGION_ROWS; row++) {
            float normalizedY = REGION_TOP
                    + (row + 0.5f) * ((REGION_BOTTOM - REGION_TOP) / REGION_ROWS);
            int y = clamp(Math.round(normalizedY * (height - 1)), 0, height - 1);

            for (int column = 0; column < REGION_COLUMNS; column++) {
                float normalizedX = startX
                        + (column + 0.5f) * ((endX - startX) / REGION_COLUMNS);
                int x = clamp(Math.round(normalizedX * (width - 1)), 0, width - 1);
                int offset = y * rowStride + x * pixelStride;
                if (offset < 0 || offset + 1 >= buffer.limit()) continue;

                int low = buffer.get(offset) & 0xFF;
                int high = buffer.get(offset + 1) & 0xFF;
                int depthMillimeters = (high << 8) | low;
                float meters = depthMillimeters / 1000f;
                if (meters >= MIN_VALID_DEPTH_METERS && meters <= MAX_VALID_DEPTH_METERS) {
                    distances.add(meters);
                }
            }
        }

        if (distances.isEmpty()) {
            return new RegionStats(0, -1f, Float.MAX_VALUE);
        }

        Collections.sort(distances);
        float median = distances.get(distances.size() / 2);
        float mean = 0f;
        for (float value : distances) mean += value;
        mean /= distances.size();

        float variance = 0f;
        for (float value : distances) {
            float delta = value - mean;
            variance += delta * delta;
        }
        variance /= distances.size();

        return new RegionStats(distances.size(), median, (float) Math.sqrt(variance));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class RegionStats {
        final int validCount;
        final float medianMeters;
        final float standardDeviationMeters;

        RegionStats(int validCount, float medianMeters, float standardDeviationMeters) {
            this.validCount = validCount;
            this.medianMeters = medianMeters;
            this.standardDeviationMeters = standardDeviationMeters;
        }

        boolean isSideWallCandidate() {
            return validCount >= MIN_SIDE_VALID_SAMPLES
                    && medianMeters <= MAX_SIDE_WALL_DISTANCE_METERS
                    && standardDeviationMeters <= MAX_SIDE_STANDARD_DEVIATION_METERS;
        }
    }

    public static final class DepthEvidence {
        public final boolean available;
        public final boolean qualitySufficient;
        public final boolean corridorLike;
        public final float leftDistanceMeters;
        public final float rightDistanceMeters;
        public final float centerDistanceMeters;
        public final int validSampleCount;
        public final long sampleTimeMs;

        public DepthEvidence(boolean available,
                             boolean qualitySufficient,
                             boolean corridorLike,
                             float leftDistanceMeters,
                             float rightDistanceMeters,
                             float centerDistanceMeters,
                             int validSampleCount,
                             long sampleTimeMs) {
            this.available = available;
            this.qualitySufficient = qualitySufficient;
            this.corridorLike = corridorLike;
            this.leftDistanceMeters = leftDistanceMeters;
            this.rightDistanceMeters = rightDistanceMeters;
            this.centerDistanceMeters = centerDistanceMeters;
            this.validSampleCount = validSampleCount;
            this.sampleTimeMs = sampleTimeMs;
        }

        /**
         * 兼容直接构造证据的调用方；available 为 true 时默认认为采样质量足够。
         */
        public DepthEvidence(boolean available,
                             boolean corridorLike,
                             float leftDistanceMeters,
                             float rightDistanceMeters,
                             float centerDistanceMeters,
                             int validSampleCount,
                             long sampleTimeMs) {
            this(
                    available,
                    available,
                    corridorLike,
                    leftDistanceMeters,
                    rightDistanceMeters,
                    centerDistanceMeters,
                    validSampleCount,
                    sampleTimeMs
            );
        }

        public static DepthEvidence unavailable(long sampleTimeMs) {
            return new DepthEvidence(false, false, false, -1f, -1f, -1f, 0, sampleTimeMs);
        }
    }
}
