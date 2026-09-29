package com.google.ar.core.examples.java.nav.managers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
import com.google.ar.core.examples.java.nav.utils.ImageUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 第二阶段：本地视觉障碍检测模块。
 *
 * 技术路线：
 * 1. 使用 MediaPipe Tasks Object Detector 在本地执行目标检测；
 * 2. 使用 ARCore Depth API 的 acquireDepthImage16Bits() 对检测框进行距离估计；
 * 3. 输出最近、位于前进走廊中的障碍物距离，供多模态交互层映射为动态触觉。
 *
 * 说明：
 * - 需要在 app 的 build.gradle 中加入 com.google.mediapipe:tasks-vision 依赖；
 * - 需要在 src/main/assets 中放置目标检测模型，例如 efficientdet_lite0.tflite。
 */
public class LocalVisionObstacleDetector {

    private static final String TAG = "LocalVisionDetector";
    public static final String DEFAULT_MODEL_ASSET_PATH = "efficientdet_lite0.tflite";

    private static final float DEFAULT_SCORE_THRESHOLD = 0.35f;
    private static final int DEFAULT_MAX_RESULTS = 5;

    private static final float ROI_LEFT_NORM = 0.18f;
    private static final float ROI_RIGHT_NORM = 0.82f;
    private static final float ROI_TOP_NORM = 0.18f;
    private static final float ROI_BOTTOM_NORM = 0.95f;
    private static final float MIN_BOX_AREA_RATIO = 0.015f;
    private static final float MAX_BOX_AREA_RATIO = 0.80f;

    private static final float MIN_VALID_DISTANCE_METERS = 0.25f;
    private static final float MAX_VALID_DISTANCE_METERS = 4.5f;
    private static final int DEPTH_SAMPLE_GRID = 5;

    private static final Set<String> PREFERRED_OBSTACLE_LABELS = buildPreferredLabelSet();

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    private ObjectDetector objectDetector;
    private boolean initialized = false;

    public interface Callback {
        void onObstacleResult(@Nullable ObstacleFrameResult result);
        void onError(@NonNull String message, @Nullable Throwable throwable);
    }

    public static class ObstacleFrameResult {
        public final int imageWidth;
        public final int imageHeight;
        public final List<ObstacleSample> obstacles;
        @Nullable
        public final ObstacleSample nearestObstacle;

        public ObstacleFrameResult(int imageWidth,
                                   int imageHeight,
                                   @Nullable List<ObstacleSample> obstacles) {
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            List<ObstacleSample> safe = obstacles == null ? Collections.emptyList() : new ArrayList<>(obstacles);
            this.obstacles = Collections.unmodifiableList(safe);
            this.nearestObstacle = safe.isEmpty() ? null : safe.get(0);
        }
    }

    public static class ObstacleSample {
        public final String label;
        public final float score;
        public final float distanceMeters;
        public final RectF boundingBox;

        public ObstacleSample(String label, float score, float distanceMeters, RectF boundingBox) {
            this.label = label;
            this.score = score;
            this.distanceMeters = distanceMeters;
            this.boundingBox = boundingBox;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%s score=%.2f distance=%.2fm box=[%.1f,%.1f,%.1f,%.1f]",
                    label, score, distanceMeters, boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom);
        }
    }

    public LocalVisionObstacleDetector(Context context) {
        this(context, DEFAULT_MODEL_ASSET_PATH);
    }

    public LocalVisionObstacleDetector(Context context, String modelAssetPath) {
        this.context = context.getApplicationContext();
        setupDetector(modelAssetPath);
    }

    public boolean isReady() {
        return initialized && objectDetector != null;
    }

    public boolean submitFrame(@Nullable Image cameraImage,
                               @Nullable Image depthImage,
                               @Nullable Callback callback) {
        if (cameraImage == null || depthImage == null) {
            closeQuietly(cameraImage);
            closeQuietly(depthImage);
            return false;
        }
        if (!isReady() || !isProcessing.compareAndSet(false, true)) {
            closeQuietly(cameraImage);
            closeQuietly(depthImage);
            return false;
        }

        executor.execute(() -> {
            try {
                Bitmap bitmap = ImageUtils.yuvToBitmap(cameraImage);
                if (bitmap == null) {
                    notifyError(callback, "camera image -> bitmap failed", null);
                    return;
                }

                MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                ObjectDetectorResult result = objectDetector.detect(mpImage);
                ObstacleFrameResult frameResult = buildObstacleFrameResult(
                        result,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        depthImage
                );
                if (callback != null) {
                    callback.onObstacleResult(frameResult);
                }
            } catch (Throwable t) {
                notifyError(callback, "local obstacle detection failed", t);
            } finally {
                closeQuietly(cameraImage);
                closeQuietly(depthImage);
                isProcessing.set(false);
            }
        });
        return true;
    }

    public void close() {
        try {
            if (objectDetector != null) {
                objectDetector.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "close detector failed: " + e.getMessage());
        }
        executor.shutdownNow();
        objectDetector = null;
        initialized = false;
    }

    private void setupDetector(String modelAssetPath) {
        try {
            ObjectDetector.ObjectDetectorOptions options =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetPath).build())
                            .setRunningMode(RunningMode.IMAGE)
                            .setScoreThreshold(DEFAULT_SCORE_THRESHOLD)
                            .setMaxResults(DEFAULT_MAX_RESULTS)
                            .build();
            objectDetector = ObjectDetector.createFromOptions(context, options);
            initialized = true;
            Log.i(TAG, "MediaPipe object detector initialized: model=" + modelAssetPath);
        } catch (Throwable t) {
            initialized = false;
            objectDetector = null;
            Log.e(TAG, "setupDetector failed", t);
        }
    }

    @NonNull
    private ObstacleFrameResult buildObstacleFrameResult(@Nullable ObjectDetectorResult result,
                                                         int cameraWidth,
                                                         int cameraHeight,
                                                         Image depthImage) {
        List<ObstacleSample> samples = new ArrayList<>();
        if (result == null || result.detections() == null || result.detections().isEmpty()) {
            return new ObstacleFrameResult(cameraWidth, cameraHeight, samples);
        }

        for (Detection detection : result.detections()) {
            if (detection == null || detection.boundingBox() == null) continue;

            RectF box = detection.boundingBox();
            if (!isForwardObstacleCandidate(box, cameraWidth, cameraHeight)) continue;

            float distanceMeters = estimateDistanceMeters(depthImage, box, cameraWidth, cameraHeight);
            if (distanceMeters < MIN_VALID_DISTANCE_METERS || distanceMeters > MAX_VALID_DISTANCE_METERS) {
                continue;
            }

            String label = getPrimaryLabel(detection);
            float score = getPrimaryScore(detection);

            samples.add(new ObstacleSample(
                    label,
                    score,
                    distanceMeters,
                    new RectF(box)
            ));
        }

        Collections.sort(samples, (a, b) -> Float.compare(rankSample(a), rankSample(b)));
        return new ObstacleFrameResult(cameraWidth, cameraHeight, samples);
    }

    private float rankSample(@NonNull ObstacleSample sample) {
        float rankingDistance = sample.distanceMeters;
        if (!PREFERRED_OBSTACLE_LABELS.contains(sample.label.toLowerCase(Locale.US))) {
            rankingDistance += 0.35f;
        }
        return rankingDistance;
    }

    private boolean isForwardObstacleCandidate(RectF box, int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) return false;
        float clampedLeft = clamp(box.left, 0f, imageWidth);
        float clampedTop = clamp(box.top, 0f, imageHeight);
        float clampedRight = clamp(box.right, 0f, imageWidth);
        float clampedBottom = clamp(box.bottom, 0f, imageHeight);
        if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) return false;

        float width = clampedRight - clampedLeft;
        float height = clampedBottom - clampedTop;
        float areaRatio = (width * height) / (imageWidth * imageHeight);
        if (areaRatio < MIN_BOX_AREA_RATIO || areaRatio > MAX_BOX_AREA_RATIO) return false;

        float cx = (clampedLeft + clampedRight) * 0.5f / imageWidth;
        float cy = (clampedTop + clampedBottom) * 0.5f / imageHeight;
        float bottom = clampedBottom / imageHeight;

        return cx >= ROI_LEFT_NORM
                && cx <= ROI_RIGHT_NORM
                && cy >= ROI_TOP_NORM
                && bottom <= ROI_BOTTOM_NORM
                && bottom >= 0.30f;
    }

    private float estimateDistanceMeters(Image depthImage, RectF cameraBox, int cameraWidth, int cameraHeight) {
        if (depthImage == null || depthImage.getPlanes() == null || depthImage.getPlanes().length == 0) {
            return -1f;
        }
        Image.Plane plane = depthImage.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int rowStride = plane.getRowStride();
        int depthWidth = depthImage.getWidth();
        int depthHeight = depthImage.getHeight();
        if (depthWidth <= 0 || depthHeight <= 0) return -1f;

        RectF focus = shrinkBox(cameraBox, 0.28f);
        List<Integer> validDepthMm = new ArrayList<>();

        for (int gy = 0; gy < DEPTH_SAMPLE_GRID; gy++) {
            float y = focus.top + (gy + 0.5f) * (focus.height() / DEPTH_SAMPLE_GRID);
            int depthY = clamp(Math.round((y / cameraHeight) * (depthHeight - 1)), 0, depthHeight - 1);
            for (int gx = 0; gx < DEPTH_SAMPLE_GRID; gx++) {
                float x = focus.left + (gx + 0.5f) * (focus.width() / DEPTH_SAMPLE_GRID);
                int depthX = clamp(Math.round((x / cameraWidth) * (depthWidth - 1)), 0, depthWidth - 1);
                int offset = depthY * rowStride + depthX * 2;
                if (offset < 0 || offset + 1 >= buffer.limit()) continue;

                int lo = buffer.get(offset) & 0xFF;
                int hi = buffer.get(offset + 1) & 0xFF;
                int depthMm = (hi << 8) | lo;
                if (depthMm >= (int) (MIN_VALID_DISTANCE_METERS * 1000f)
                        && depthMm <= (int) (MAX_VALID_DISTANCE_METERS * 1000f)) {
                    validDepthMm.add(depthMm);
                }
            }
        }

        if (validDepthMm.size() < 4) return -1f;
        Collections.sort(validDepthMm);
        int medianMm = validDepthMm.get(validDepthMm.size() / 2);
        return medianMm / 1000f;
    }

    private RectF shrinkBox(RectF box, float ratio) {
        float shrinkX = box.width() * ratio;
        float shrinkY = box.height() * ratio;
        return new RectF(
                box.left + shrinkX,
                box.top + shrinkY,
                box.right - shrinkX,
                box.bottom - shrinkY
        );
    }

    private String getPrimaryLabel(Detection detection) {
        if (detection.categories() == null || detection.categories().isEmpty()) {
            return "object";
        }
        Category c = detection.categories().get(0);
        String label = c.categoryName();
        if (label == null || label.trim().isEmpty()) {
            label = c.displayName();
        }
        return label == null || label.trim().isEmpty() ? "object" : label.trim();
    }

    private float getPrimaryScore(Detection detection) {
        if (detection.categories() == null || detection.categories().isEmpty()) {
            return 0f;
        }
        return detection.categories().get(0).score();
    }

    private static Set<String> buildPreferredLabelSet() {
        Set<String> labels = new HashSet<>();
        Collections.addAll(labels,
                "person", "chair", "bench", "couch", "sofa", "table", "dining table",
                "potted plant", "backpack", "suitcase", "bottle", "box", "tv", "bed", "dog");
        return labels;
    }

    private void notifyError(@Nullable Callback callback, @NonNull String message, @Nullable Throwable t) {
        Log.w(TAG, message, t);
        if (callback != null) {
            callback.onError(message, t);
        }
    }

    private static void closeQuietly(@Nullable Image image) {
        if (image == null) return;
        try {
            image.close();
        } catch (Exception ignored) {
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
