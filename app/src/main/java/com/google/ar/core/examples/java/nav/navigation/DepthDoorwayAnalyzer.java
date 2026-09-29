package com.google.ar.core.examples.java.nav.navigation;

import android.media.Image;

import com.google.ar.core.CameraIntrinsics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 使用 ARCore Depth 图像检测正前方的窄门候选区域。
 *
 * <p>这不是门的语义识别模型，而是一个几何检测器：如果中部的水平扫描带
 * 同时看到左、右两个近距离的竖直门框，中央存在连续的远距离开口，并且
 * 开口的估算宽度落在窄门范围内，则返回候选证据。它不要求拍到门顶或门底，
 * 调用方应使用连续帧确认后再播报。</p>
 */
public final class DepthDoorwayAnalyzer {
    private static final float MIN_VALID_DEPTH_METERS = 0.30f;
    private static final float MAX_VALID_DEPTH_METERS = 8.0f;
    private static final float MAX_SIDE_STRUCTURE_DISTANCE_METERS = 3.2f;
    private static final float MIN_OPENING_DEPTH_METERS = 2.4f;
    private static final float MIN_OPENING_DELTA_METERS = 0.45f;

    private static final float MIN_NARROW_DOOR_WIDTH_METERS = 0.55f;
    private static final float MAX_NARROW_DOOR_WIDTH_METERS = 2.0f;
    private static final float MIN_DOOR_DISTANCE_METERS = 0.50f;
    private static final float MAX_DOOR_DISTANCE_METERS = 5.0f;

    private static final int SCAN_ROWS = 3;
    private static final int SCAN_COLUMNS = 31;
    private static final int MIN_SIDE_VALID_SAMPLES_PER_ROW = 4;
    private static final int MIN_CENTER_VALID_SAMPLES_PER_ROW = 5;
    private static final int MIN_QUALITY_ROWS = 2;
    private static final int MIN_CANDIDATE_ROWS = 2;
    private static final int MAX_OPENING_GAP_SAMPLES = 1;

    private static final float SCAN_LEFT = 0.10f;
    private static final float SCAN_RIGHT = 0.90f;
    private static final float SIDE_LEFT_END = 0.28f;
    private static final float SIDE_RIGHT_START = 0.72f;
    private static final float SCAN_TOP = 0.36f;
    private static final float SCAN_BOTTOM = 0.68f;
    private static final float DEFAULT_HORIZONTAL_FOV_DEGREES = 65.0f;

    public DoorEvidence analyze(Image depthImage,
                                CameraIntrinsics cameraIntrinsics,
                                long sampleTimeMs) {
        if (depthImage == null
                || depthImage.getPlanes() == null
                || depthImage.getPlanes().length == 0) {
            return DoorEvidence.unavailable(sampleTimeMs);
        }

        Image.Plane plane = depthImage.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);

        float focalX = Float.NaN;
        float principalX = Float.NaN;
        if (cameraIntrinsics != null) {
            try {
                float[] focalLength = cameraIntrinsics.getFocalLength();
                float[] principalPoint = cameraIntrinsics.getPrincipalPoint();
                int[] imageDimensions = cameraIntrinsics.getImageDimensions();
                if (focalLength != null && focalLength.length > 0
                        && principalPoint != null && principalPoint.length > 0
                        && imageDimensions != null && imageDimensions.length > 0
                        && focalLength[0] > 0f
                        && imageDimensions[0] > 0) {
                    float scaleX = depthImage.getWidth() / (float) imageDimensions[0];
                    focalX = focalLength[0] * scaleX;
                    principalX = principalPoint[0] * scaleX;
                }
            } catch (RuntimeException ignored) {
                // 某些设备可能暂时无法提供内参，下面使用水平视场角近似值。
            }
        }

        return analyzeBuffer(
                buffer,
                depthImage.getWidth(),
                depthImage.getHeight(),
                plane.getRowStride(),
                plane.getPixelStride(),
                focalX,
                principalX,
                sampleTimeMs
        );
    }

    static DoorEvidence analyzeBuffer(ByteBuffer buffer,
                                      int width,
                                      int height,
                                      int rowStride,
                                      int pixelStride,
                                      float focalX,
                                      float principalX,
                                      long sampleTimeMs) {
        if (buffer == null || width <= 0 || height <= 0 || rowStride <= 0) {
            return DoorEvidence.unavailable(sampleTimeMs);
        }

        float effectiveFocalX = focalX;
        float effectivePrincipalX = principalX;
        if (!(effectiveFocalX > 0f) || !Float.isFinite(effectiveFocalX)) {
            effectiveFocalX = (width * 0.5f)
                    / (float) Math.tan(Math.toRadians(DEFAULT_HORIZONTAL_FOV_DEGREES * 0.5f));
        }
        if (!Float.isFinite(effectivePrincipalX)) {
            effectivePrincipalX = width * 0.5f;
        }

        int safePixelStride = pixelStride > 0 ? pixelStride : 2;
        int qualityRows = 0;
        int candidateRows = 0;
        List<Float> candidateWidths = new ArrayList<>();
        List<Float> candidateDistances = new ArrayList<>();

        for (int row = 0; row < SCAN_ROWS; row++) {
            float normalizedY = SCAN_TOP
                    + (row + 0.5f) * ((SCAN_BOTTOM - SCAN_TOP) / SCAN_ROWS);
            int y = clamp(Math.round(normalizedY * (height - 1)), 0, height - 1);
            List<DepthSample> samples = readRow(
                    buffer,
                    width,
                    y,
                    rowStride,
                    safePixelStride
            );

            List<Float> leftSideDepths = new ArrayList<>();
            List<Float> rightSideDepths = new ArrayList<>();
            int centerValidCount = 0;
            for (DepthSample sample : samples) {
                if (!sample.valid) continue;
                if (sample.normalizedX < SIDE_LEFT_END) {
                    leftSideDepths.add(sample.depthMeters);
                } else if (sample.normalizedX > SIDE_RIGHT_START) {
                    rightSideDepths.add(sample.depthMeters);
                } else if (sample.normalizedX >= 0.38f && sample.normalizedX <= 0.62f) {
                    centerValidCount++;
                }
            }

            float leftSideDepth = median(leftSideDepths);
            float rightSideDepth = median(rightSideDepths);
            boolean leftFrameVisible = leftSideDepths.size() >= MIN_SIDE_VALID_SAMPLES_PER_ROW
                    && leftSideDepth <= MAX_SIDE_STRUCTURE_DISTANCE_METERS;
            boolean rightFrameVisible = rightSideDepths.size() >= MIN_SIDE_VALID_SAMPLES_PER_ROW
                    && rightSideDepth <= MAX_SIDE_STRUCTURE_DISTANCE_METERS;

            if (leftFrameVisible
                    && rightFrameVisible
                    && centerValidCount >= MIN_CENTER_VALID_SAMPLES_PER_ROW) {
                qualityRows++;
            }

            if (!leftFrameVisible
                    || !rightFrameVisible
                    || centerValidCount < MIN_CENTER_VALID_SAMPLES_PER_ROW) {
                continue;
            }

            float sideDepth = median(concat(leftSideDepths, rightSideDepths));
            if (sideDepth > MAX_SIDE_STRUCTURE_DISTANCE_METERS) continue;

            float openingThreshold = Math.max(
                    MIN_OPENING_DEPTH_METERS,
                    sideDepth + MIN_OPENING_DELTA_METERS
            );
            int centerIndex = findCenterIndex(
                    samples,
                    effectivePrincipalX / width
            );
            if (centerIndex < 0 || !samples.get(centerIndex).isOpen(openingThreshold)) {
                continue;
            }

            int leftIndex = expandOpeningLeft(samples, centerIndex, openingThreshold);
            int rightIndex = expandOpeningRight(samples, centerIndex, openingThreshold);
            if (rightIndex <= leftIndex) continue;

            float[] edgeDepths = new float[2];
            edgeDepths[0] = nearestValidDepth(samples, leftIndex - 1, leftIndex);
            edgeDepths[1] = nearestValidDepth(samples, rightIndex + 1, rightIndex);
            if (!(edgeDepths[0] >= MIN_DOOR_DISTANCE_METERS
                    && edgeDepths[0] <= MAX_SIDE_STRUCTURE_DISTANCE_METERS
                    && edgeDepths[1] >= MIN_DOOR_DISTANCE_METERS
                    && edgeDepths[1] <= MAX_SIDE_STRUCTURE_DISTANCE_METERS)) {
                continue;
            }
            float doorPlaneDepth = medianOfValid(edgeDepths);
            if (!(doorPlaneDepth >= MIN_DOOR_DISTANCE_METERS
                    && doorPlaneDepth <= MAX_DOOR_DISTANCE_METERS)) {
                continue;
            }

            float leftX = boundaryNormalizedX(samples, leftIndex);
            float rightX = boundaryNormalizedX(samples, rightIndex);
            float openingPixelWidth = (rightX - leftX) * width;
            float openingWidthMeters = openingPixelWidth / effectiveFocalX * doorPlaneDepth;
            if (openingWidthMeters < MIN_NARROW_DOOR_WIDTH_METERS
                    || openingWidthMeters > MAX_NARROW_DOOR_WIDTH_METERS) {
                continue;
            }

            candidateRows++;
            candidateWidths.add(openingWidthMeters);
            candidateDistances.add(doorPlaneDepth);
        }

        boolean qualitySufficient = qualityRows >= MIN_QUALITY_ROWS;
        boolean narrowDoor = qualitySufficient && candidateRows >= MIN_CANDIDATE_ROWS;
        return new DoorEvidence(
                true,
                qualitySufficient,
                narrowDoor,
                narrowDoor ? median(candidateWidths) : -1f,
                narrowDoor ? median(candidateDistances) : -1f,
                qualityRows,
                candidateRows,
                sampleTimeMs
        );
    }

    private static List<DepthSample> readRow(ByteBuffer buffer,
                                             int width,
                                             int y,
                                             int rowStride,
                                             int pixelStride) {
        List<DepthSample> samples = new ArrayList<>(SCAN_COLUMNS);
        for (int column = 0; column < SCAN_COLUMNS; column++) {
            float normalizedX = SCAN_LEFT
                    + column * ((SCAN_RIGHT - SCAN_LEFT) / (SCAN_COLUMNS - 1));
            int x = clamp(Math.round(normalizedX * (width - 1)), 0, width - 1);
            int offset = y * rowStride + x * pixelStride;
            if (offset < 0 || offset + 1 >= buffer.limit()) {
                samples.add(new DepthSample(normalizedX, -1f, false));
                continue;
            }

            int low = buffer.get(offset) & 0xFF;
            int high = buffer.get(offset + 1) & 0xFF;
            float depthMeters = ((high << 8) | low) / 1000f;
            boolean valid = depthMeters >= MIN_VALID_DEPTH_METERS
                    && depthMeters <= MAX_VALID_DEPTH_METERS;
            samples.add(new DepthSample(normalizedX, depthMeters, valid));
        }
        return samples;
    }

    private static int findCenterIndex(List<DepthSample> samples, float centerNormalizedX) {
        int bestIndex = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) {
            float distance = Math.abs(samples.get(i).normalizedX - centerNormalizedX);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int expandOpeningLeft(List<DepthSample> samples,
                                         int centerIndex,
                                         float openingThreshold) {
        int leftIndex = centerIndex;
        int closedSamples = 0;
        for (int i = centerIndex - 1; i >= 0; i--) {
            if (samples.get(i).isOpen(openingThreshold)) {
                leftIndex = i;
                closedSamples = 0;
            } else if (closedSamples < MAX_OPENING_GAP_SAMPLES) {
                closedSamples++;
            } else {
                break;
            }
        }
        return leftIndex;
    }

    private static int expandOpeningRight(List<DepthSample> samples,
                                          int centerIndex,
                                          float openingThreshold) {
        int rightIndex = centerIndex;
        int closedSamples = 0;
        for (int i = centerIndex + 1; i < samples.size(); i++) {
            if (samples.get(i).isOpen(openingThreshold)) {
                rightIndex = i;
                closedSamples = 0;
            } else if (closedSamples < MAX_OPENING_GAP_SAMPLES) {
                closedSamples++;
            } else {
                break;
            }
        }
        return rightIndex;
    }

    private static float nearestValidDepth(List<DepthSample> samples,
                                           int firstIndex,
                                           int fallbackIndex) {
        if (firstIndex >= 0 && firstIndex < samples.size() && samples.get(firstIndex).valid) {
            return samples.get(firstIndex).depthMeters;
        }
        if (fallbackIndex >= 0 && fallbackIndex < samples.size() && samples.get(fallbackIndex).valid) {
            return samples.get(fallbackIndex).depthMeters;
        }
        return -1f;
    }

    private static float boundaryNormalizedX(List<DepthSample> samples, int index) {
        if (index < 0 || index >= samples.size()) return 0.5f;
        return samples.get(index).normalizedX;
    }

    private static List<Float> concat(List<Float> first, List<Float> second) {
        List<Float> values = new ArrayList<>(first.size() + second.size());
        values.addAll(first);
        values.addAll(second);
        return values;
    }

    private static float median(List<Float> values) {
        if (values == null || values.isEmpty()) return -1f;
        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private static float medianOfValid(float[] values) {
        List<Float> valid = new ArrayList<>();
        for (float value : values) {
            if (value > 0f) valid.add(value);
        }
        return median(valid);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class DepthSample {
        final float normalizedX;
        final float depthMeters;
        final boolean valid;

        DepthSample(float normalizedX, float depthMeters, boolean valid) {
            this.normalizedX = normalizedX;
            this.depthMeters = depthMeters;
            this.valid = valid;
        }

        boolean isOpen(float openingThreshold) {
            return valid && depthMeters >= openingThreshold;
        }
    }

    public static final class DoorEvidence {
        public final boolean available;
        public final boolean qualitySufficient;
        public final boolean narrowDoor;
        public final float widthMeters;
        public final float distanceMeters;
        public final int qualityRowCount;
        public final int candidateRowCount;
        public final long sampleTimeMs;

        public DoorEvidence(boolean available,
                            boolean qualitySufficient,
                            boolean narrowDoor,
                            float widthMeters,
                            float distanceMeters,
                            int qualityRowCount,
                            int candidateRowCount,
                            long sampleTimeMs) {
            this.available = available;
            this.qualitySufficient = qualitySufficient;
            this.narrowDoor = narrowDoor;
            this.widthMeters = widthMeters;
            this.distanceMeters = distanceMeters;
            this.qualityRowCount = qualityRowCount;
            this.candidateRowCount = candidateRowCount;
            this.sampleTimeMs = sampleTimeMs;
        }

        public static DoorEvidence unavailable(long sampleTimeMs) {
            return new DoorEvidence(false, false, false, -1f, -1f, 0, 0, sampleTimeMs);
        }
    }
}
