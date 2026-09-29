package com.google.ar.core.examples.java.nav;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.media.Image;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.examples.java.nav.view.ObstacleOverlayView;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer;
import com.google.ar.core.examples.java.nav.api.model.Node;
import com.google.ar.core.examples.java.nav.api.model.RelativeLocalizationResponse;
import com.google.ar.core.examples.java.nav.i18n.AppText;
import com.google.ar.core.examples.java.nav.interaction.VoiceCommandParser;
import com.google.ar.core.examples.java.nav.managers.HapticFeedbackManager;
import com.google.ar.core.examples.java.nav.managers.AccessibilityModeManager;
import com.google.ar.core.examples.java.nav.managers.InteractionConfigManager;
import com.google.ar.core.examples.java.nav.managers.LocalVisionObstacleDetector;
import com.google.ar.core.examples.java.nav.managers.MultimodalInteractionManager;
import com.google.ar.core.examples.java.nav.managers.NavigationEngine;
import com.google.ar.core.examples.java.nav.managers.NetworkRepository;
import com.google.ar.core.examples.java.nav.managers.TTSManager;
import com.google.ar.core.examples.java.nav.managers.VisualModeManager;
import com.google.ar.core.examples.java.nav.managers.VoiceRecognitionManager;
import com.google.ar.core.examples.java.nav.navigation.NavigationMapBuilder;
import com.google.ar.core.examples.java.nav.navigation.NavigationPathPlanner;
import com.google.ar.core.examples.java.nav.navigation.NavigationReportBuilder;
import com.google.ar.core.examples.java.nav.navigation.CorridorModeController;
import com.google.ar.core.examples.java.nav.navigation.DepthCorridorAnalyzer;
import com.google.ar.core.examples.java.nav.navigation.DepthDoorwayAnalyzer;
import com.google.ar.core.examples.java.nav.navigation.NarrowDoorAlertController;
import com.google.ar.core.examples.java.nav.render.PathRenderer;
import com.google.ar.core.examples.java.nav.render.PointRenderer;
import com.google.ar.core.examples.java.nav.utils.ImageUtils;
import com.google.ar.core.examples.java.nav.utils.NodeNameMatcher;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.text.InputType;
import android.widget.EditText;
/**
 * AR 室内导航主 Activity
 *
 * 关键修复：
 * 1. 使用 Anchor 固定定位原点，避免 ARCore 世界坐标修正时整张地图漂移。
 * 2. 不再把路径/目的地一次性“烘焙”为静态世界坐标，而是基于 Anchor 每帧重建。
 * 3. tracking 恢复后增加短冷却，避免快速转身时立刻触发转弯/偏航误判。
 */
public class NavigationActivity extends AppCompatActivity implements SampleRender.Renderer {

    private static final String TAG = "NavigationActivity";
    private static final float TTS_SPEECH_RATE_ZH = 1.0f;
    private static final float TTS_SPEECH_RATE_EN = 1.2f;
    private static final int PERMISSION_CODE_RECORD_AUDIO = 101;
    private static final int PERMISSION_CODE_CAMERA = 102;
    private static final long LOCAL_VISION_INTERVAL_MS = 450L;
    private static final String LOCAL_VISION_MODEL_ASSET = LocalVisionObstacleDetector.DEFAULT_MODEL_ASSET_PATH;
    private static final long TRACKING_RECOVERY_COOLDOWN_MS = 600L;
    private static final long REROUTE_COOLDOWN_MS = 8000L;
    private static final float WHERE_AM_I_NEARBY_NODE_COUNT = 3f;
    private static final float WHERE_AM_I_TURN_ANGLE_THRESHOLD_DEG = 35f;
    private static final String LOCALIZATION_BASE_URL = "http://172.18.36.70:8100/";

    private Session session;
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private GLSurfaceView surfaceView;
    private BackgroundRenderer backgroundRenderer;
    private PathRenderer pathRenderer;
    private PointRenderer pointRenderer;

    private TTSManager ttsManager;
    private NetworkRepository networkRepo;
    private VisualModeManager visualManager;
    private NavigationEngine navEngine;
    private VoiceRecognitionManager voiceManager;
    private InteractionConfigManager interactionConfigManager;
    private HapticFeedbackManager hapticFeedbackManager;
    private MultimodalInteractionManager multimodalManager;
    private AccessibilityModeManager accessibilityModeManager;
    private final VoiceCommandParser voiceCommandParser = new VoiceCommandParser();
    private final NavigationPathPlanner navigationPathPlanner = new NavigationPathPlanner();
    private final NavigationReportBuilder navigationReportBuilder =
            new NavigationReportBuilder(this::tr);
    private final CorridorModeController corridorModeController = new CorridorModeController();
    private final DepthCorridorAnalyzer depthCorridorAnalyzer = new DepthCorridorAnalyzer();
    private final DepthDoorwayAnalyzer depthDoorwayAnalyzer = new DepthDoorwayAnalyzer();
    private final NarrowDoorAlertController narrowDoorAlertController =
            new NarrowDoorAlertController();

    private Button localizeButton;
    private Button selectDestButton;
    private Button voiceButton;
    private Button narrowDoorTestButton;
    private Button languageButton;
    private ImageButton settingsButton;
    private TextView statusTextView;
    private FrameLayout labelContainer;
    private ToggleButton toggleShowPath;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private TextView debugCoordsTextView;
    private TextView relocDebugTextView;

    private boolean installRequested = false;
    private boolean hasSetTextureNames = false;
    private volatile boolean shouldCaptureForLocalization = false;

    private boolean isAutoLocalizingEnabled = false;
    private boolean isCurrentlyProcessingAutoReloc = false;
    private long lastAutoLocalizeTime = 0;

    private boolean isLocalized = false;
    private boolean isNavigating = false;
    private Pose lastArPose = null;
    private boolean showCameraPath = false;
    private int renderFrameCounter = 0;
    private boolean isDebugging = true;

    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];

    private final List<Node> allNodes = new ArrayList<>();
    private final List<Node> doorplateNodes = new ArrayList<>();
    private final List<Node> pathNodes = new ArrayList<>();
    private final List<float[]> navPathPointsAr = new ArrayList<>();
    private final List<float[]> pathNodePointsAr = Collections.synchronizedList(new ArrayList<>());
    private final List<float[]> doorplatePointsAr = Collections.synchronizedList(new ArrayList<>());

    private List<RelativeLocalizationResponse.RelativeNode> originalRelativeNodes;
    private Pose capturePoseForScale;
    private Node currentTargetNode = null;
    private String currentTargetNodeId = null;
    private final List<String> currentPathNodeIds = new ArrayList<>();
    private volatile DepthCorridorAnalyzer.DepthEvidence latestDepthCorridorEvidence = null;
    private volatile DepthDoorwayAnalyzer.DoorEvidence latestNarrowDoorEvidence = null;
    private boolean isNarrowDoorTestMode = false;
    private TextView narrowDoorTestResultTextView;
    private boolean localizeEnabledBeforeNarrowDoorTest = true;
    private boolean selectDestinationEnabledBeforeNarrowDoorTest = false;
    private boolean voiceEnabledBeforeNarrowDoorTest = true;
    private long lastRerouteAtMs = 0L;
    private boolean isRerouting = false;

    private Anchor localizationAnchor = null;
    private TrackingState lastCameraTrackingState = TrackingState.PAUSED;
    private long trackingRecoveredAtMs = 0L;

    private LocalVisionObstacleDetector localVisionDetector;
    private boolean isDepthModeSupported = false;
    private long lastLocalVisionTimeMs = 0L;
    private volatile float lastObstacleDistanceMeters = -1f;
    private volatile String lastObstacleLabel = null;

    private static final long OBSTACLE_OVERLAY_UPDATE_INTERVAL_MS = 66L;

    private ObstacleOverlayView obstacleOverlayView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile LocalVisionObstacleDetector.ObstacleFrameResult lastObstacleFrameResult = null;
    private long lastObstacleOverlayUpdateMs = 0L;
    private boolean isCleanScreenModeEnabled = false;
    private boolean isAccessibilityModeEnabled = false;
    private int currentSceneId = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        surfaceView = findViewById(R.id.surfaceview);
        localizeButton = findViewById(R.id.btn_localize);
        statusTextView = findViewById(R.id.tv_status);
        relocDebugTextView = findViewById(R.id.tv_relocalize_debug);
        labelContainer = findViewById(R.id.label_container);
        toggleShowPath = findViewById(R.id.toggle_show_path_nodes);
        selectDestButton = findViewById(R.id.btn_select_dest);
        voiceButton = findViewById(R.id.btn_voice);
        narrowDoorTestButton = findViewById(R.id.btn_narrow_door_test);
        narrowDoorTestResultTextView = findViewById(R.id.tv_narrow_door_test_result);
        languageButton = findViewById(R.id.btn_language);
        settingsButton = findViewById(R.id.btn_settings);
        debugCoordsTextView = findViewById(R.id.tv_debug_coords);

        displayRotationHelper = new DisplayRotationHelper(this);
        new SampleRender(surfaceView, this, getAssets());

        interactionConfigManager = new InteractionConfigManager(this);
        accessibilityModeManager = new AccessibilityModeManager(
                getResources().getDisplayMetrics().density
        );
        accessibilityModeManager.setSingleTapListener(this::onAccessibilitySingleTap);
        isAccessibilityModeEnabled = interactionConfigManager.isAccessibilityModeEnabled();
        currentSceneId = interactionConfigManager.getCurrentSceneId();
        isCleanScreenModeEnabled = interactionConfigManager.isCleanScreenEnabled();

        networkRepo = new NetworkRepository(LOCALIZATION_BASE_URL);
        networkRepo.setSceneId(currentSceneId);
        syncSpeechRecognitionLanguage(getAppLanguage());

        visualManager = new VisualModeManager(this);
        navEngine = new NavigationEngine();

        ttsManager = new TTSManager(this, utteranceId -> {
            if (TTSManager.ID_LOC_SUCCESS.equals(utteranceId)) {
                if (ttsManager != null) {
                    ttsManager.speak(tr("tts_fetch_map_data"));
                }
            }
            if (TTSManager.ID_NAV_INTRO.equals(utteranceId)) {
                navEngine.isSpeakingNavIntro = false;
            }
        });
        syncTtsLanguageSettings(getAppLanguage());

        hapticFeedbackManager = new HapticFeedbackManager(this);
        multimodalManager = new MultimodalInteractionManager(
                this,
                ttsManager,
                hapticFeedbackManager,
                interactionConfigManager,
                networkRepo
        );

        navEngine.applyVeerSensitivity(interactionConfigManager.getHapticSensitivity());

        try {
            localVisionDetector = new LocalVisionObstacleDetector(this, LOCAL_VISION_MODEL_ASSET);
        } catch (Throwable t) {
            localVisionDetector = null;
            Log.e(TAG, "init local vision detector failed", t);
        }

        initVoiceManager();

        localizeButton.setOnClickListener(v -> triggerManualLocalization());
        selectDestButton.setOnClickListener(v -> showDestinationDialog());
        settingsButton.setOnClickListener(v -> showMainSettingsDialog());
        languageButton.setOnClickListener(v -> toggleAppLanguage());
        toggleShowPath.setOnCheckedChangeListener((btn, isChecked) -> {
            showCameraPath = isChecked;
            btn.setContentDescription(tr(
                    isChecked ? "a11y_btn_show_path" : "a11y_btn_hide_path"
            ));
        });
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            narrowDoorTestButton.setOnClickListener(v -> toggleNarrowDoorTestMode());
        } else {
            narrowDoorTestButton.setVisibility(View.GONE);
            narrowDoorTestResultTextView.setVisibility(View.GONE);
        }

        voiceButton.setOnClickListener(v -> {
            if (checkAudioPermission()) {
                if (ttsManager != null) {
                    ttsManager.stop();
                }
                if (multimodalManager != null) {
                    multimodalManager.onVoiceWakeup();
                }
                updateStatusTr("status_start_voice");
                voiceManager.startListening();
            }
        });

        voiceButton.setOnLongClickListener(v -> {
            if (interactionConfigManager != null && interactionConfigManager.isRepeatButtonEnabled()) {
                if (multimodalManager != null) {
                    multimodalManager.repeatLastInstruction();
                }
            } else if (multimodalManager != null) {
                multimodalManager.onFeatureUnavailable(tr("feature_repeat_button_disabled"));
            }
            return true;
        });

        surfaceView.setLongClickable(true);
        surfaceView.setOnLongClickListener(v -> {
            if (isCleanScreenModeEnabled) {
                setCleanScreenMode(false, true);
                return true;
            }
            return false;
        });

        visualManager.saveDefaultStyles(localizeButton, settingsButton, statusTextView);
        applyVisualModeAndAccessibility();

        selectDestButton.setEnabled(false);

        applyLanguageUi();
        setCleanScreenMode(isCleanScreenModeEnabled, false);
    }

    private InteractionConfigManager.AppLanguage getAppLanguage() {
        if (interactionConfigManager == null) {
            return InteractionConfigManager.AppLanguage.ZH;
        }
        return interactionConfigManager.getAppLanguage();
    }

    private String tr(String key, Object... args) {
        return AppText.text(getAppLanguage(), key, args);
    }

    private Locale getTtsLocale(InteractionConfigManager.AppLanguage language) {
        return language == InteractionConfigManager.AppLanguage.EN ? Locale.US : Locale.CHINA;
    }

    private float getTtsSpeechRate(InteractionConfigManager.AppLanguage language) {
        return language == InteractionConfigManager.AppLanguage.EN
                ? TTS_SPEECH_RATE_EN
                : TTS_SPEECH_RATE_ZH;
    }

    private void syncTtsLanguageSettings(InteractionConfigManager.AppLanguage language) {
        if (ttsManager == null) {
            return;
        }
        ttsManager.setLanguageLocale(getTtsLocale(language));
        ttsManager.setSpeechRate(getTtsSpeechRate(language));
    }

    private void syncSpeechRecognitionLanguage(InteractionConfigManager.AppLanguage language) {
        if (networkRepo == null) {
            return;
        }
        networkRepo.setBaiduAsrDevPid(
                language == InteractionConfigManager.AppLanguage.EN
                        ? NetworkRepository.BAIDU_DEV_PID_ENGLISH
                        : NetworkRepository.BAIDU_DEV_PID_MANDARIN
        );
    }

    private void updateStatusTr(String key, Object... args) {
        updateStatus(tr(key, args));
    }

    private void showToastTr(String key, Object... args) {
        Toast.makeText(this, tr(key, args), Toast.LENGTH_SHORT).show();
    }

    private void applyLanguageUi() {
        if (localizeButton != null) {
            localizeButton.setText(tr("btn_localize"));
            localizeButton.setContentDescription(tr("a11y_btn_localize"));
        }
        if (selectDestButton != null) {
            selectDestButton.setText(tr("btn_destination"));
            selectDestButton.setContentDescription(tr("a11y_btn_destination"));
        }
        if (voiceButton != null) {
            voiceButton.setText(tr("btn_voice"));
            voiceButton.setContentDescription(tr("a11y_btn_voice"));
        }
        if (narrowDoorTestButton != null) {
            narrowDoorTestButton.setText(tr(
                    isNarrowDoorTestMode
                            ? "btn_narrow_door_test_stop"
                            : "btn_narrow_door_test"
            ));
            narrowDoorTestButton.setContentDescription(tr(
                    isNarrowDoorTestMode
                            ? "a11y_btn_narrow_door_test_stop"
                            : "a11y_btn_narrow_door_test"
            ));
        }
        if (languageButton != null) {
            languageButton.setText(tr("btn_language_switch"));
            languageButton.setContentDescription(tr("a11y_btn_language"));
        }
        if (toggleShowPath != null) {
            toggleShowPath.setTextOn(tr("btn_show_path_on"));
            toggleShowPath.setTextOff(tr("btn_show_path_off"));
            toggleShowPath.setText(toggleShowPath.isChecked()
                    ? toggleShowPath.getTextOn()
                    : toggleShowPath.getTextOff());
            toggleShowPath.setContentDescription(tr(
                    toggleShowPath.isChecked()
                            ? "a11y_btn_show_path"
                            : "a11y_btn_hide_path"
            ));
        }
        if (settingsButton != null) {
            settingsButton.setContentDescription(tr("a11y_btn_settings"));
        }
        if (statusTextView != null && !isLocalized) {
            statusTextView.setText(tr("status_waiting_localization"));
        }
    }

    private void applyVisualModeAndAccessibility() {
        if (accessibilityModeManager != null && !isAccessibilityModeEnabled) {
            // 先恢复上次无障碍样式，再让视觉辅助模式还原其自身的样式。
            accessibilityModeManager.apply(false);
        }
        visualManager.applyVisualMode(
                localizeButton,
                selectDestButton,
                settingsButton,
                statusTextView,
                labelContainer
        );
        if (accessibilityModeManager != null) {
            accessibilityModeManager.apply(
                    isAccessibilityModeEnabled,
                    localizeButton,
                    selectDestButton,
                    voiceButton,
                    narrowDoorTestButton,
                    languageButton,
                    settingsButton,
                    toggleShowPath,
                    statusTextView,
                    relocDebugTextView,
                    narrowDoorTestResultTextView,
                    labelContainer
            );
            if (statusTextView != null) {
                statusTextView.setAccessibilityLiveRegion(
                        isAccessibilityModeEnabled
                                ? View.ACCESSIBILITY_LIVE_REGION_POLITE
                                : View.ACCESSIBILITY_LIVE_REGION_NONE
                );
            }
            if (narrowDoorTestResultTextView != null) {
                narrowDoorTestResultTextView.setAccessibilityLiveRegion(
                        isAccessibilityModeEnabled
                                ? View.ACCESSIBILITY_LIVE_REGION_POLITE
                                : View.ACCESSIBILITY_LIVE_REGION_NONE
                );
            }
        }
    }

    private void applyAccessibilityMode(boolean enabled) {
        isAccessibilityModeEnabled = enabled;
        applyVisualModeAndAccessibility();
    }

    private void onAccessibilitySingleTap(View view) {
        if (!isAccessibilityModeEnabled || view == null) return;

        String label = getAccessibleLabel(view);
        if (TextUtils.isEmpty(label)) return;

        if (view instanceof CompoundButton) {
            label = label + "，"
                    + tr(((CompoundButton) view).isChecked()
                    ? "a11y_state_on"
                    : "a11y_state_off");
        }
        String announcement = label + "，" + tr("a11y_double_tap_hint");
        if (ttsManager != null) {
            ttsManager.speakUrgent(announcement);
        } else {
            view.announceForAccessibility(announcement);
        }
    }

    private String getAccessibleLabel(View view) {
        if (view == null) return "";

        CharSequence contentDescription = view.getContentDescription();
        String label = contentDescription == null
                ? ""
                : contentDescription.toString().trim();
        if (!TextUtils.isEmpty(label)) return label;

        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            label = text == null ? "" : text.toString().trim();
            if (!TextUtils.isEmpty(label)) return label;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                label = getAccessibleLabel(group.getChildAt(i));
                if (!TextUtils.isEmpty(label)) return label;
            }
        }
        return "";
    }

    private void bindAccessibilityDialog(AlertDialog dialog) {
        if (!isAccessibilityModeEnabled
                || accessibilityModeManager == null
                || dialog == null
                || dialog.getWindow() == null) {
            return;
        }

        View root = dialog.getWindow().getDecorView();
        android.widget.ListView listView = dialog.getListView();
        accessibilityModeManager.bindDialog(root, listView);
        dialog.setOnDismissListener(d ->
                accessibilityModeManager.unbindDialog(root, listView)
        );
    }

    private void toggleAppLanguage() {
        if (interactionConfigManager == null) {
            return;
        }

        InteractionConfigManager.AppLanguage nextLanguage =
                getAppLanguage() == InteractionConfigManager.AppLanguage.EN
                        ? InteractionConfigManager.AppLanguage.ZH
                        : InteractionConfigManager.AppLanguage.EN;

        interactionConfigManager.setAppLanguage(nextLanguage);

        if (ttsManager != null) {
            ttsManager.stop();
            syncTtsLanguageSettings(nextLanguage);
        }
        syncSpeechRecognitionLanguage(nextLanguage);

        applyLanguageUi();

        String switched = AppText.text(
                nextLanguage,
                nextLanguage == InteractionConfigManager.AppLanguage.EN
                        ? "tts_language_switched_en"
                        : "tts_language_switched_zh"
        );
        updateStatus(switched);
        if (ttsManager != null) {
            ttsManager.speakUrgent(switched);
        }
    }

    private void initVoiceManager() {
        voiceManager = new VoiceRecognitionManager(this, networkRepo, new VoiceRecognitionManager.VoiceCallback() {
            @Override
            public void onReady() {
                updateStatusTr("status_ready");
            }

            @Override
            public void onListeningStarted() {
                updateStatusTr("status_listening");
            }

            @Override
            public void onListeningFinished() {
                updateStatusTr("status_recognizing");
            }

            @Override
            public void onCommandRecognized(String command) {
                updateStatusTr("status_recognized", command);
                processVoiceCommand(command);
            }

            @Override
            public void onError(String message) {
                updateStatusTr("status_error", message);
            }
        });
    }
    private void setCleanScreenMode(boolean enabled, boolean showToast) {
        isCleanScreenModeEnabled = enabled;

        if (interactionConfigManager != null) {
            interactionConfigManager.setCleanScreenEnabled(enabled);
        }

        int uiVisibility = enabled ? View.GONE : View.VISIBLE;

        localizeButton.setVisibility(uiVisibility);
        selectDestButton.setVisibility(uiVisibility);
        voiceButton.setVisibility(uiVisibility);
        languageButton.setVisibility(uiVisibility);
        settingsButton.setVisibility(uiVisibility);
        toggleShowPath.setVisibility(uiVisibility);
        statusTextView.setVisibility(uiVisibility);
        labelContainer.setVisibility(uiVisibility);
        narrowDoorTestButton.setVisibility(
                enabled ? View.GONE : narrowDoorTestButton.getVisibility()
        );
        narrowDoorTestResultTextView.setVisibility(
                enabled || !isNarrowDoorTestMode ? View.GONE : View.VISIBLE
        );

        debugCoordsTextView.setVisibility(
                enabled ? View.GONE : (isDebugging ? View.VISIBLE : View.GONE)
        );

        if (enabled) {
            relocDebugTextView.setVisibility(View.GONE);
        } else if (isDebugging
                && relocDebugTextView.getText() != null
                && relocDebugTextView.getText().length() > 0) {
            relocDebugTextView.setVisibility(View.VISIBLE);
        } else {
            relocDebugTextView.setVisibility(View.GONE);
        }

        // 如果你已经加了障碍框 Overlay，可在这里一起隐藏：
        // if (obstacleOverlayView != null) {
        //     obstacleOverlayView.setVisibility(enabled ? View.GONE : View.VISIBLE);
        // }

        if (showToast) {
            showToastTr(enabled ? "toast_clean_on" : "toast_clean_off");
        }
    }
    private void processVoiceCommand(String command) {
        if (command == null || isNarrowDoorTestMode) return;

        String spoken = command.trim();
        updateStatusTr("status_recognized", spoken);

        if (multimodalManager != null) {
            multimodalManager.onVoiceCommandRecognized(spoken);
        }

        VoiceCommandParser.ParsedCommand parsed = voiceCommandParser.parse(spoken);
        if (parsed.type == VoiceCommandParser.CommandType.STATUS_QUERY) {
            announceWhereAmIReport();
        } else if (parsed.type == VoiceCommandParser.CommandType.REPEAT) {
            if (multimodalManager != null) {
                multimodalManager.repeatLastInstruction();
            }
        } else if (applyVoiceInteractionSetting(spoken)
                || applyObstacleTestCommand(spoken)) {
            // Command handled by an interaction-specific handler.
        } else {
            switch (parsed.type) {
                case LOCALIZE:
                    triggerManualLocalization();
                    break;
                case NAVIGATE:
                    handleVoiceNavigation(parsed.destination);
                    break;
                case RESET:
                    performRelocalization();
                    break;
                case SETTINGS:
                    showMainSettingsDialog();
                    break;
                case UNKNOWN:
                    if (NodeNameMatcher.findBestMatch(spoken, doorplateNodes, allNodes) != null) {
                        handleVoiceNavigation(spoken);
                    } else {
                        updateStatusTr("status_feature_unmatched", spoken);
                        if (multimodalManager != null) {
                            multimodalManager.onUnknownVoiceCommand(spoken);
                        }
                    }
                    break;
                case STATUS_QUERY:
                case REPEAT:
                    break;
            }
        }
    }

    private boolean applyObstacleTestCommand(String spoken) {
        if (multimodalManager == null || spoken == null) return false;

        if (containsAnyPhrase(spoken,
                "停止障碍提醒",
                "关闭障碍提醒",
                "stop obstacle alert",
                "disable obstacle alert")) {
            multimodalManager.clearObstacleAlert();
            updateStatusTr("status_obstacle_test_stopped");
            return true;
        }
        if (containsAnyPhrase(spoken,
                "障碍测试近",
                "近距离障碍",
                "near obstacle",
                "obstacle test near")) {
            multimodalManager.onObstacleDistanceSample(0.6f);
            updateStatusTr("status_obstacle_test_near");
            return true;
        }
        if (containsAnyPhrase(spoken,
                "障碍测试中",
                "中距离障碍",
                "mid obstacle",
                "medium obstacle",
                "obstacle test medium")) {
            multimodalManager.onObstacleDistanceSample(1.2f);
            updateStatusTr("status_obstacle_test_mid");
            return true;
        }
        if (containsAnyPhrase(spoken,
                "障碍测试远",
                "远距离障碍",
                "far obstacle",
                "obstacle test far")) {
            multimodalManager.onObstacleDistanceSample(2.0f);
            updateStatusTr("status_obstacle_test_far");
            return true;
        }
        return false;
    }

    private void handleVoiceNavigation(String destName) {
        if (isNarrowDoorTestMode) return;

        if (!isLocalized) {
            if (multimodalManager != null) {
                multimodalManager.onNavigationMessage(tr("tts_need_localization_first"), true);
            }
            return;
        }

        if (destName == null || destName.trim().isEmpty()) {
            if (multimodalManager != null) {
                multimodalManager.onNavigationMessage(tr("tts_repeat_destination"), true);
            }
            return;
        }

        NodeNameMatcher.MatchResult match = NodeNameMatcher.findBestMatch(destName, doorplateNodes, allNodes);
        if (match != null && match.node != null) {
            currentTargetNode = match.node;
            currentTargetNodeId = match.node.id;
            updateStatusTr("status_destination_recognized", currentTargetNode.id, match.normalizedInput);
            if (networkRepo != null) {
                networkRepo.sendLogToServer(
                        "VOICE_MATCH",
                        "destination_matched",
                        "raw=" + destName
                                + ",normalized=" + match.normalizedInput
                                + ",target=" + currentTargetNode.id
                                + ",targetNorm=" + match.normalizedNodeId
                                + ",score=" + match.score
                );
            }
            navigateToNode(currentTargetNode);
            return;
        }

        String normalized = NodeNameMatcher.normalize(destName);
        updateStatusTr("status_destination_not_found", destName);
        if (networkRepo != null) {
            networkRepo.sendLogToServer(
                    "VOICE_MATCH",
                    "destination_not_found",
                    "raw=" + destName
                            + ",normalized=" + normalized
                            + ",doorplates=" + doorplateNodes.size()
                            + ",allNodes=" + allNodes.size()
            );
        }
        if (multimodalManager != null) {
            multimodalManager.onNavigationMessage(tr("status_destination_not_found", destName), true);
        }
    }

    private void triggerManualLocalization() {
        if (isNarrowDoorTestMode) return;

        updateStatusTr("status_collecting_image");
        localizeButton.setEnabled(false);
        isCurrentlyProcessingAutoReloc = false;
        shouldCaptureForLocalization = true;
        if (multimodalManager != null) {
            multimodalManager.onLocalizationRequested();
        }
    }

    private boolean checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_CODE_RECORD_AUDIO
            );
            return false;
        }
        return true;
    }
    private boolean ensureCameraPermissionForAr() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_CODE_CAMERA
            );
            reportArCoreIssue(tr("arcore_no_camera_permission"), null);
            return false;
        }
        return true;
    }

    private boolean ensureArCoreSessionReady() {
        if (session != null) return true;

        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        Log.i(TAG, "ARCore availability = " + availability);

        if (availability.isTransient()) {
            updateStatusTr("status_checking_arcore");
            return false;
        }

        switch (availability) {
            case UNSUPPORTED_DEVICE_NOT_CAPABLE:
                reportArCoreIssue(tr("arcore_unsupported_device"), null);
                return false;

            case UNKNOWN_ERROR:
                reportArCoreIssue(tr("arcore_unknown_support"), null);
                return false;

            case UNKNOWN_TIMED_OUT:
                reportArCoreIssue(tr("arcore_support_timeout"), null);
                return false;

            case SUPPORTED_NOT_INSTALLED:
            case SUPPORTED_APK_TOO_OLD:
            case SUPPORTED_INSTALLED:
                break;

            default:
                // 其他状态统一继续尝试 requestInstall，让系统给出更明确反馈
                break;
        }

        try {
            switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                case INSTALL_REQUESTED:
                    installRequested = true;
                    updateStatusTr("status_requesting_arcore_install");
                    return false;

                case INSTALLED:
                    installRequested = false;
                    break;
            }

            session = new Session(this);
            Log.i(TAG, "ARCore session created successfully");
            return true;

        } catch (Exception e) {
            reportArCoreIssue(describeArCoreException(e), e);
            return false;
        }
    }

    private String describeArCoreException(Exception e) {
        if (e instanceof UnavailableArcoreNotInstalledException) {
            return tr("arcore_not_installed");
        }
        if (e instanceof UnavailableApkTooOldException) {
            return tr("arcore_apk_too_old");
        }
        if (e instanceof UnavailableSdkTooOldException) {
            return tr("arcore_sdk_too_old");
        }
        if (e instanceof UnavailableDeviceNotCompatibleException) {
            return tr("arcore_device_not_compatible");
        }
        if (e instanceof UnavailableUserDeclinedInstallationException) {
            return tr("arcore_user_declined");
        }
        if (e instanceof SecurityException) {
            return tr("arcore_security");
        }
        if (e instanceof CameraNotAvailableException) {
            return tr("arcore_camera_not_available");
        }

        String msg = e.getMessage();
        if (msg != null && !msg.trim().isEmpty()) {
            return tr("arcore_start_failed", msg);
        }
        return tr("arcore_start_failed", e.getClass().getSimpleName());
    }

    private void reportArCoreIssue(String userMessage, Throwable t) {
        Log.e(TAG, "ARCore issue: " + userMessage, t);

        String fullMessage = tr("arcore_error_prefix", userMessage);
        updateStatus(fullMessage);

        runOnUiThread(() -> {
            if (statusTextView != null) {
                statusTextView.setVisibility(View.VISIBLE);
                statusTextView.setText(fullMessage);
            }

            if (isDebugging && debugCoordsTextView != null) {
                debugCoordsTextView.setVisibility(View.VISIBLE);
                debugCoordsTextView.setText(fullMessage);
            }

            if (localizeButton != null) {
                localizeButton.setEnabled(false);
            }
            if (selectDestButton != null) {
                selectDestButton.setEnabled(false);
            }
        });
    }

    private void releaseSessionOnError() {
        hasSetTextureNames = false;
        isDepthModeSupported = false;

        if (session != null) {
            try {
                session.pause();
            } catch (Exception ignored) {
            }
            try {
                session.close();
            } catch (Exception ignored) {
            }
            session = null;
        }
    }
    @Override
    public void onDrawFrame(SampleRender render) {
        if (session == null) return;

        if (!hasSetTextureNames) {
            session.setCameraTextureNames(new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        displayRotationHelper.updateSessionIfNeeded(session);

        Frame frame;
        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            reportArCoreIssue(tr("arcore_camera_not_available"), e);
            releaseSessionOnError();
            return;
        }

        Camera camera = frame.getCamera();
        backgroundRenderer.updateDisplayGeometry(frame);
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        if (frame.getTimestamp() != 0) {
            backgroundRenderer.drawBackground(render);
        }

        if (isLocalized) {
            renderFrameCounter++;
            if (renderFrameCounter % 60 == 0) sendDebugDiagnostics();
            if (renderFrameCounter % 15 == 0) updateDebugCoordsUI();

            if (isAutoLocalizingEnabled && !isNarrowDoorTestMode) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastAutoLocalizeTime > 3000 && !shouldCaptureForLocalization) {
                    lastAutoLocalizeTime = now;
                    isCurrentlyProcessingAutoReloc = true;
                    shouldCaptureForLocalization = true;
                }
            }

            if (renderFrameCounter >= 1200) {
                renderFrameCounter = 0;
            }
        }

        TrackingState trackingState = camera.getTrackingState();
        if (trackingState != lastCameraTrackingState) {
            if (trackingState == TrackingState.TRACKING) {
                trackingRecoveredAtMs = SystemClock.elapsedRealtime();
            }
            lastCameraTrackingState = trackingState;
        }

        if (trackingState != TrackingState.TRACKING) {
            clearObstacleOverlay();
            return;
        }

        lastArPose = camera.getPose();

        boolean localizationConsumedFrame = handleLocalizationCapture(frame, camera);
        handleLocalVisionDetection(frame, camera, localizationConsumedFrame);
        updateObstacleOverlay(frame);
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);
        camera.getViewMatrix(viewMatrix, 0);

        if (isLocalized) {
            rebuildNodesFromBasePose();
        }

        renderARContent();
    }

    private void updateObstacleOverlay(Frame frame) {
        if (obstacleOverlayView == null) return;

        if (!isNavigating || frame == null) {
            clearObstacleOverlay();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastObstacleOverlayUpdateMs < OBSTACLE_OVERLAY_UPDATE_INTERVAL_MS) {
            return;
        }
        lastObstacleOverlayUpdateMs = now;

        LocalVisionObstacleDetector.ObstacleFrameResult snapshot = lastObstacleFrameResult;
        if (snapshot == null || snapshot.obstacles.isEmpty()) {
            clearObstacleOverlay();
            return;
        }

        List<ObstacleOverlayView.BoxItem> boxes = new ArrayList<>();
        for (int i = 0; i < snapshot.obstacles.size(); i++) {
            LocalVisionObstacleDetector.ObstacleSample sample = snapshot.obstacles.get(i);
            RectF viewBox = mapImageBoxToView(frame, sample.boundingBox);
            if (viewBox == null || viewBox.width() < 8f || viewBox.height() < 8f) {
                continue;
            }

            String text = String.format(
                    Locale.CHINA,
                    "%s %.1fm",
                    sample.label == null ? "object" : sample.label,
                    sample.distanceMeters
            );
            boxes.add(new ObstacleOverlayView.BoxItem(viewBox, text, i == 0));
        }

        mainHandler.post(() -> {
            if (obstacleOverlayView != null) {
                obstacleOverlayView.setBoxes(boxes);
            }
        });
    }

    private void clearObstacleOverlay() {
        mainHandler.post(() -> {
            if (obstacleOverlayView != null) {
                obstacleOverlayView.clearBoxes();
            }
        });
    }

    private RectF mapImageBoxToView(Frame frame, RectF imageBox) {
        if (frame == null || imageBox == null) return null;

        float[] src = new float[]{
                imageBox.left, imageBox.top,
                imageBox.right, imageBox.top,
                imageBox.right, imageBox.bottom,
                imageBox.left, imageBox.bottom
        };
        float[] dst = new float[8];

        try {
            frame.transformCoordinates2d(
                    Coordinates2d.IMAGE_PIXELS,
                    src,
                    Coordinates2d.VIEW,
                    dst
            );
        } catch (Throwable t) {
            Log.w(TAG, "transform obstacle box failed", t);
            return null;
        }

        float minX = Math.min(Math.min(dst[0], dst[2]), Math.min(dst[4], dst[6]));
        float minY = Math.min(Math.min(dst[1], dst[3]), Math.min(dst[5], dst[7]));
        float maxX = Math.max(Math.max(dst[0], dst[2]), Math.max(dst[4], dst[6]));
        float maxY = Math.max(Math.max(dst[1], dst[3]), Math.max(dst[5], dst[7]));

        if (maxX <= minX || maxY <= minY) return null;

        return new RectF(minX, minY, maxX, maxY);
    }

    private boolean handleLocalizationCapture(Frame frame, Camera camera) {
        if (isNarrowDoorTestMode) {
            shouldCaptureForLocalization = false;
            return false;
        }
        if (!shouldCaptureForLocalization) return false;

        shouldCaptureForLocalization = false;
        try (Image image = frame.acquireCameraImage()) {
            if (image != null) {
                final Pose arPoseAtCapture = camera.getPose();
                final byte[] rawJpegData = ImageUtils.yuvToJpeg(image);
                final boolean currentAutoMode = isCurrentlyProcessingAutoReloc;

                runOnUiThread(() -> uploadImageAndLocalize(rawJpegData, arPoseAtCapture, currentAutoMode));
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "handleLocalizationCapture failed", e);
            if (!isCurrentlyProcessingAutoReloc) {
                updateStatusTr("status_capture_failed");
                localizeButton.setEnabled(true);
            }
        }
        return false;
    }

    private void handleLocalVisionDetection(Frame frame,
                                            Camera camera,
                                            boolean localizationConsumedFrame) {
        if (frame == null || localizationConsumedFrame) return;
        if (!isNavigating && !isNarrowDoorTestMode) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastLocalVisionTimeMs < LOCAL_VISION_INTERVAL_MS) return;
        lastLocalVisionTimeMs = now;

        Image cameraImage = null;
        Image depthImage = null;
        try {
            boolean narrowDoorDetectionEnabled =
                    interactionConfigManager.isNarrowDoorDetectionEnabled()
                            || isNarrowDoorTestMode;
            if (isDepthModeSupported) {
                depthImage = frame.acquireDepthImage16Bits();
                if (depthImage != null) {
                    latestDepthCorridorEvidence = depthCorridorAnalyzer.analyze(depthImage, now);
                    if (narrowDoorDetectionEnabled) {
                        CameraIntrinsics cameraIntrinsics = camera == null
                                ? null
                                : camera.getImageIntrinsics();
                        latestNarrowDoorEvidence = depthDoorwayAnalyzer.analyze(
                                depthImage,
                                cameraIntrinsics,
                                now
                        );
                        notifyNarrowDoorIfConfirmed(latestNarrowDoorEvidence, now);
                    } else {
                        latestNarrowDoorEvidence = null;
                        narrowDoorAlertController.reset();
                    }
                } else {
                    latestDepthCorridorEvidence = DepthCorridorAnalyzer.DepthEvidence.unavailable(now);
                    if (narrowDoorDetectionEnabled) {
                        latestNarrowDoorEvidence = DepthDoorwayAnalyzer.DoorEvidence.unavailable(now);
                        notifyNarrowDoorIfConfirmed(latestNarrowDoorEvidence, now);
                    } else {
                        latestNarrowDoorEvidence = null;
                        narrowDoorAlertController.reset();
                    }
                }
            } else {
                latestDepthCorridorEvidence = DepthCorridorAnalyzer.DepthEvidence.unavailable(now);
                if (narrowDoorDetectionEnabled) {
                    latestNarrowDoorEvidence = DepthDoorwayAnalyzer.DoorEvidence.unavailable(now);
                    notifyNarrowDoorIfConfirmed(latestNarrowDoorEvidence, now);
                } else {
                    latestNarrowDoorEvidence = null;
                    narrowDoorAlertController.reset();
                }
            }

            if (isNarrowDoorTestMode) return;

            // 楼道分析不依赖目标检测模型；模型可用时，再复用同一张图像执行障碍物检测。
            if (depthImage == null
                    || multimodalManager == null
                    || localVisionDetector == null
                    || !localVisionDetector.isReady()) {
                return;
            }

            cameraImage = frame.acquireCameraImage();
            if (cameraImage == null) return;

            boolean submitted = localVisionDetector.submitFrame(
                    cameraImage,
                    depthImage,
                    new LocalVisionObstacleDetector.Callback() {
                        @Override
                        public void onObstacleResult(LocalVisionObstacleDetector.ObstacleFrameResult result) {
                            if (result == null || result.obstacles.isEmpty()) {
                                lastObstacleFrameResult = null;
                                lastObstacleDistanceMeters = -1f;
                                lastObstacleLabel = null;

                                if (multimodalManager != null) {
                                    multimodalManager.clearObstacleAlert();
                                }
                                clearObstacleOverlay();
                                return;
                            }

                            lastObstacleFrameResult = result;

                            LocalVisionObstacleDetector.ObstacleSample nearest = result.nearestObstacle;
                            if (nearest != null) {
                                lastObstacleDistanceMeters = nearest.distanceMeters;
                                lastObstacleLabel = nearest.label;
                                Log.d(TAG, "local obstacle nearest: " + nearest);

                                if (multimodalManager != null && isNavigating) {
                                    multimodalManager.onObstacleDistanceSample(nearest.distanceMeters);
                                }
                            }
                        }

                        @Override
                        public void onError(@NonNull String message, Throwable throwable) {
                            Log.w(TAG, message, throwable);
                        }
                    }
            );

            if (submitted) {
                cameraImage = null;
                depthImage = null;
            }
        } catch (NotYetAvailableException e) {
            latestDepthCorridorEvidence = DepthCorridorAnalyzer.DepthEvidence.unavailable(now);
            latestNarrowDoorEvidence = DepthDoorwayAnalyzer.DoorEvidence.unavailable(now);
            notifyNarrowDoorIfConfirmed(latestNarrowDoorEvidence, now);
            Log.d(TAG, "depth/camera image not ready for local vision: " + e.getMessage());
        } catch (Exception e) {
            latestDepthCorridorEvidence = DepthCorridorAnalyzer.DepthEvidence.unavailable(now);
            latestNarrowDoorEvidence = DepthDoorwayAnalyzer.DoorEvidence.unavailable(now);
            notifyNarrowDoorIfConfirmed(latestNarrowDoorEvidence, now);
            Log.w(TAG, "handleLocalVisionDetection failed", e);
        } finally {
            closeImageQuietly(cameraImage);
            closeImageQuietly(depthImage);
        }
    }

    private void notifyNarrowDoorIfConfirmed(DepthDoorwayAnalyzer.DoorEvidence evidence,
                                             long nowMs) {
        NarrowDoorAlertController.AlertDecision decision =
                narrowDoorAlertController.update(evidence, nowMs);

        if (isNarrowDoorTestMode) {
            updateNarrowDoorTestResult(evidence, decision);
            return;
        }

        if (!decision.shouldAlert || multimodalManager == null || !isNavigating) return;

        Log.i(TAG, "narrow_door_detected: width=" + decision.widthMeters
                + "m, distance=" + decision.distanceMeters + "m");
        multimodalManager.onNarrowDoorDetected(
                decision.widthMeters,
                decision.distanceMeters
        );
    }

    private void toggleNarrowDoorTestMode() {
        isNarrowDoorTestMode = !isNarrowDoorTestMode;
        narrowDoorAlertController.reset();
        latestNarrowDoorEvidence = null;

        if (isNarrowDoorTestMode) {
            localizeEnabledBeforeNarrowDoorTest = localizeButton.isEnabled();
            selectDestinationEnabledBeforeNarrowDoorTest = selectDestButton.isEnabled();
            voiceEnabledBeforeNarrowDoorTest = voiceButton.isEnabled();

            shouldCaptureForLocalization = false;
            isCurrentlyProcessingAutoReloc = false;
            if (voiceManager != null) voiceManager.cancelListening();
            if (ttsManager != null) ttsManager.stop();

            localizeButton.setEnabled(false);
            selectDestButton.setEnabled(false);
            voiceButton.setEnabled(false);
        } else {
            localizeButton.setEnabled(localizeEnabledBeforeNarrowDoorTest);
            selectDestButton.setEnabled(selectDestinationEnabledBeforeNarrowDoorTest);
            voiceButton.setEnabled(voiceEnabledBeforeNarrowDoorTest);
        }

        if (narrowDoorTestResultTextView != null) {
            narrowDoorTestResultTextView.setVisibility(
                    isNarrowDoorTestMode ? View.VISIBLE : View.GONE
            );
            if (isNarrowDoorTestMode) {
                narrowDoorTestResultTextView.setText(
                        "窄门实拍测试已开启\n"
                                + "无需服务器、无需定位。请让左右竖直门框同时进入画面并保持缓慢移动。\n"
                                + "不要求拍到完整的上、下门框。\n"
                                + "等待 ARCore Depth 数据..."
                );
            }
        }
        applyLanguageUi();
    }

    private void updateNarrowDoorTestResult(DepthDoorwayAnalyzer.DoorEvidence evidence,
                                            NarrowDoorAlertController.AlertDecision decision) {
        if (narrowDoorTestResultTextView == null || !isNarrowDoorTestMode) return;

        final String message;
        if (evidence == null || !evidence.available) {
            message = "窄门实拍测试\n等待 ARCore Depth 数据...\n"
                    + "请保持相机跟踪，并缓慢移动手机。";
        } else if (!evidence.qualitySufficient) {
            message = "窄门实拍测试\nDepth 数据质量不足\n"
                    + "请让左右门框同时进入画面；上、下门框无需完整可见。";
        } else if (evidence.narrowDoor) {
            message = String.format(
                    Locale.CHINA,
                    "窄门实拍测试\n检测到窄门候选\n宽度：%.2fm\n距离：%.2fm\n扫描带：%d/%d\n%s",
                    evidence.widthMeters,
                    evidence.distanceMeters,
                    evidence.candidateRowCount,
                    evidence.qualityRowCount,
                    decision.shouldAlert ? "连续确认：已触发一次测试提示" : "正在连续确认..."
            );
        } else {
            message = String.format(
                    Locale.CHINA,
                    "窄门实拍测试\n当前未检测到窄门候选\n有效扫描带：%d/3\n请调整拍摄方向或距离。",
                    evidence.qualityRowCount
            );
        }

        runOnUiThread(() -> {
            if (isNarrowDoorTestMode && narrowDoorTestResultTextView != null) {
                narrowDoorTestResultTextView.setText(message);
            }
        });
    }

    private void closeImageQuietly(Image image) {
        if (image == null) return;
        try {
            image.close();
        } catch (Exception ignored) {
        }
    }

    private void uploadImageAndLocalize(byte[] imageBytes, Pose arPoseAtCapture, boolean isAuto) {
        if (!isAuto) {
            updateStatusTr("status_uploading_scene_map", currentSceneId);
        }

        if (networkRepo != null) {
            networkRepo.setSceneId(currentSceneId);
        }

        networkRepo.queryRelativeMap(imageBytes, new Callback<RelativeLocalizationResponse>() {
            @Override
            public void onResponse(@NonNull Call<RelativeLocalizationResponse> call,
                                   @NonNull Response<RelativeLocalizationResponse> response) {
                RelativeLocalizationResponse body = response.body();
                if (response.isSuccessful()
                        && body != null
                        && body.code == 200
                        && body.data != null
                        && body.data.relative_map != null
                        && !body.data.relative_map.isEmpty()) {
                    processLocalizationResult(body.data.relative_map, arPoseAtCapture, isAuto);
                } else {
                    Log.w(TAG, "localization response invalid: code="
                            + response.code()
                            + ", bodyCode=" + (body == null ? "null" : body.code)
                            + ", scene=" + currentSceneId);
                    if (!isAuto) {
                        updateStatusTr("status_scene_localization_failed", currentSceneId);
                        localizeButton.setEnabled(true);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<RelativeLocalizationResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "queryRelativeMap failed, scene=" + currentSceneId, t);
                if (!isAuto) {
                    updateStatusTr("status_network_error_scene", currentSceneId);
                    localizeButton.setEnabled(true);
                }
            }
        });
    }

    private void processLocalizationResult(List<RelativeLocalizationResponse.RelativeNode> newNodes,
                                           Pose capturePose,
                                           boolean isAuto) {
        if (newNodes == null || newNodes.isEmpty() || capturePose == null) {
            if (!isAuto) {
                updateStatusTr("status_localization_empty");
                localizeButton.setEnabled(true);
            }
            return;
        }

        if (!isAuto || !isLocalized || allNodes.isEmpty()) {
            originalRelativeNodes = newNodes;
            capturePoseForScale = capturePose;
            isLocalized = true;

            replaceLocalizationAnchor(capturePose);
            rebuildNodesFromBasePose();

            if (!isAuto) {
                updateStatusTr("status_localization_success");
                localizeButton.setEnabled(true);
                selectDestButton.setEnabled(true);
                if (multimodalManager != null) {
                    multimodalManager.onLocalizationSucceeded();
                }
            }
            return;
        }

        RelativeLocalizationResponse.RelativeNode rNode = newNodes.get(0);
        if (rNode == null || rNode.position == null || rNode.position.length < 3) return;

        float[] zAxis = capturePose.getZAxis();
        double theta = Math.atan2(zAxis[0], zAxis[2]);
        float[] flatHeadingQuat = new float[]{
                0f,
                (float) Math.sin(theta / 2.0),
                0f,
                (float) Math.cos(theta / 2.0)
        };

        Pose flatHeadingPose = new Pose(capturePose.getTranslation(), flatHeadingQuat);
        Pose newArPose = flatHeadingPose.compose(
                Pose.makeTranslation(rNode.position[0], rNode.position[1], rNode.position[2])
        );

        Node oldNode = null;
        for (Node n : allNodes) {
            if (n != null && rNode.name != null && rNode.name.equals(n.id)) {
                oldNode = n;
                break;
            }
        }

        if (oldNode != null) {
            float dx = newArPose.tx() - oldNode.arX;
            float dz = newArPose.tz() - oldNode.arZ;
            final float error = (float) Math.sqrt(dx * dx + dz * dz);

            runOnUiThread(() -> {
                if (isDebugging) {
                    relocDebugTextView.setVisibility(View.VISIBLE);
                    relocDebugTextView.setText(String.format(Locale.US, "Reloc Error: %.2fm", error));
                } else {
                    relocDebugTextView.setVisibility(View.GONE);
                }
            });

            Log.d(TAG, "自动重定位误差: " + error + "m");
            if (error >= 0.5f && error <= 1.0f) {
                originalRelativeNodes = newNodes;
                capturePoseForScale = capturePose;
                replaceLocalizationAnchor(capturePose);
                rebuildNodesFromBasePose();
                corridorModeController.reset();
                latestDepthCorridorEvidence = null;
                latestNarrowDoorEvidence = null;
                narrowDoorAlertController.reset();
                navEngine.startNewNavigation();
                navEngine.setInitialRouteReferencePoint(new float[]{
                        lastArPose.tx(), lastArPose.ty(), lastArPose.tz()
                });
            }
        }
    }

    private void showSceneSwitchDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(tr("input_scene_hint"));
        input.setText(String.valueOf(currentSceneId));
        input.setSelection(input.getText().length());

        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog sceneDialog = new AlertDialog.Builder(this)
                .setTitle(tr("dialog_scene_title"))
                .setMessage(tr("dialog_scene_message"))
                .setView(container)
                .setPositiveButton(tr("dialog_scene_switch"), (dialog, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    if (text.isEmpty()) {
                        showToastTr("toast_scene_input_empty");
                        return;
                    }

                    int newSceneId;
                    try {
                        newSceneId = Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                        showToastTr("toast_scene_format_invalid");
                        return;
                    }

                    switchScene(newSceneId);
                })
                .setNegativeButton(tr("dialog_cancel"), null)
                .show();
        bindAccessibilityDialog(sceneDialog);
    }

    private void switchScene(int newSceneId) {
        if (newSceneId < 1) {
            showToastTr("toast_scene_min");
            return;
        }

        if (newSceneId == currentSceneId) {
            showToastTr("toast_scene_same", newSceneId);
            return;
        }

        if (voiceManager != null) {
            voiceManager.cancelListening();
        }
        if (ttsManager != null) {
            ttsManager.stop();
        }
        if (multimodalManager != null) {
            multimodalManager.stopAllHaptics();
            multimodalManager.clearObstacleAlert();
        }

        shouldCaptureForLocalization = false;
        isCurrentlyProcessingAutoReloc = false;
        lastAutoLocalizeTime = 0L;

        currentSceneId = newSceneId;

        if (interactionConfigManager != null) {
            interactionConfigManager.setCurrentSceneId(newSceneId);
        }
        if (networkRepo != null) {
            networkRepo.setSceneId(newSceneId);
        }

        updateStatusTr("status_scene_switched", newSceneId);
        showToastTr("toast_scene_switched", newSceneId);

        // 直接重建 Activity，触发全量初始化
        recreate();
    }

    private void replaceLocalizationAnchor(@NonNull Pose pose) {
        if (session == null) return;

        try {
            if (localizationAnchor != null) {
                localizationAnchor.detach();
            }
        } catch (Exception ignored) {
        }

        try {
            localizationAnchor = session.createAnchor(pose);
        } catch (Exception e) {
            localizationAnchor = null;
            Log.w(TAG, "replaceLocalizationAnchor failed", e);
        }
    }

    private void clearLocalizationAnchor() {
        try {
            if (localizationAnchor != null) {
                localizationAnchor.detach();
            }
        } catch (Exception ignored) {
        }
        localizationAnchor = null;
    }

    private Pose buildFlatBasePose() {
        Pose rawPose = null;

        if (localizationAnchor != null
                && localizationAnchor.getTrackingState() == TrackingState.TRACKING) {
            rawPose = localizationAnchor.getPose();
        } else if (capturePoseForScale != null) {
            rawPose = capturePoseForScale;
        }

        if (rawPose == null) return null;

        float[] zAxis = rawPose.getZAxis();
        double theta = Math.atan2(zAxis[0], zAxis[2]);
        float[] flatHeadingQuat = new float[]{
                0f,
                (float) Math.sin(theta / 2.0),
                0f,
                (float) Math.cos(theta / 2.0)
        };
        return new Pose(rawPose.getTranslation(), flatHeadingQuat);
    }

    private float getGroundY() {
        Pose basePose = buildFlatBasePose();
        if (basePose != null) return basePose.ty() - 1.3f;
        return lastArPose != null ? (lastArPose.ty() - 1.3f) : 0f;
    }

    private Node findNodeById(String id) {
        if (id == null) return null;
        for (Node node : allNodes) {
            if (id.equals(node.id)) {
                return node;
            }
        }
        return null;
    }

    private void rebuildNodesFromBasePose() {
        Pose basePose = buildFlatBasePose();
        if (basePose == null || originalRelativeNodes == null || originalRelativeNodes.isEmpty()) {
            return;
        }

        allNodes.clear();
        doorplateNodes.clear();
        pathNodes.clear();
        doorplatePointsAr.clear();

        NavigationMapBuilder.Result mappedMap = NavigationMapBuilder.build(
                originalRelativeNodes,
                basePose,
                basePose.ty() - 1.3f
        );
        allNodes.addAll(mappedMap.allNodes);
        doorplateNodes.addAll(mappedMap.doorplateNodes);
        pathNodes.addAll(mappedMap.pathNodes);
        doorplatePointsAr.addAll(mappedMap.doorplatePointsAr);

        if (currentTargetNodeId != null) {
            currentTargetNode = findNodeById(currentTargetNodeId);
        }

        rebuildLiveNavPath();
    }

    private void rebuildLiveNavPath() {
        synchronized (navPathPointsAr) {
            navPathPointsAr.clear();

            if (!isNavigating || lastArPose == null || currentPathNodeIds.isEmpty()) {
                return;
            }

            float groundY = getGroundY();
            navPathPointsAr.add(new float[]{lastArPose.tx(), groundY, lastArPose.tz()});

            String lastId = null;
            for (String id : currentPathNodeIds) {
                if (id == null || id.equals(lastId)) continue;

                Node node = findNodeById(id);
                if (node != null) {
                    navPathPointsAr.add(new float[]{node.arX, groundY, node.arZ});
                }
                lastId = id;
            }
        }
    }

    private void applyScaleAndRender() {
        rebuildNodesFromBasePose();
    }

    private void navigateToNode(Node targetNode) {
        lastRerouteAtMs = 0L;
        corridorModeController.reset();
        navEngine.setCorridorMode(false);
        latestDepthCorridorEvidence = null;
        latestNarrowDoorEvidence = null;
        narrowDoorAlertController.reset();
        planNavigationToNode(targetNode, true, false);
    }

    /**
     * 从当前 AR 位姿重新规划到原目标。
     *
     * <p>偏航时不改变目标节点，只重新计算起点到目标节点的 A* 路径。重规划失败时保留
     * 原有路线，避免一次临时失败直接中断当前导航。</p>
     */
    private void replanNavigationFromCurrentPose() {
        if (!isLocalized || !isNavigating || pathNodes.isEmpty() || lastArPose == null
                || currentTargetNodeId == null || isRerouting) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastRerouteAtMs < REROUTE_COOLDOWN_MS) {
            Log.d(TAG, "reroute skipped: cooldown="
                    + (REROUTE_COOLDOWN_MS - (now - lastRerouteAtMs)) + "ms");
            return;
        }

        isRerouting = true;
        lastRerouteAtMs = now;
        try {
            boolean replanned = planNavigationToNode(null, false, true);
            if (replanned) {
                corridorModeController.reset();
                navEngine.setCorridorMode(false);
                latestDepthCorridorEvidence = null;
                latestNarrowDoorEvidence = null;
                narrowDoorAlertController.reset();
                Log.d(TAG, "reroute succeeded: target=" + currentTargetNodeId);
                if (networkRepo != null) {
                    networkRepo.sendLogToServer(
                            "PATH_REROUTE",
                            "veer_detected",
                            "target=" + currentTargetNodeId
                    );
                }
                if (multimodalManager != null) {
                    multimodalManager.onNavigationReplanned();
                }
            } else {
                Log.w(TAG, "reroute failed: keeping the current route, target=" + currentTargetNodeId);
            }
        } finally {
            isRerouting = false;
        }
    }

    /**
     * 计算并安装一条导航路径。
     *
     * @param announceStart 是否播报“开始导航”；偏航重规划时不重复播报
     * @param keepRouteOnFailure 计算失败时是否保留当前正在使用的路线
     * @return 是否成功安装新路径
     */
    private boolean planNavigationToNode(Node targetNode,
                                         boolean announceStart,
                                         boolean keepRouteOnFailure) {
        if (!isLocalized || pathNodes.isEmpty() || lastArPose == null) return false;

        if (targetNode != null) {
            currentTargetNode = targetNode;
            currentTargetNodeId = targetNode.id;
        } else if (currentTargetNodeId != null) {
            currentTargetNode = findNodeById(currentTargetNodeId);
        }

        if (currentTargetNode == null) return false;

        NavigationPathPlanner.PathPlan plan = navigationPathPlanner.plan(
                lastArPose.tx(),
                lastArPose.ty(),
                lastArPose.tz(),
                currentTargetNode,
                pathNodes,
                doorplateNodes
        );
        if (plan.startNode == null) {
            if (!keepRouteOnFailure && multimodalManager != null) {
                multimodalManager.onNavigationMessage(tr("tts_no_route_now"), true);
            }
            return false;
        }

        if (networkRepo != null) {
            networkRepo.sendLogToServer("PATH_PLAN", "compute_ordered_path", plan.debugInfo);
        }

        if (!plan.nodeIds.isEmpty()) {
            currentPathNodeIds.clear();
            currentPathNodeIds.addAll(plan.nodeIds);
            isNavigating = true;
            navEngine.startNewNavigation();
            navEngine.setInitialRouteReferencePoint(new float[]{
                    lastArPose.tx(), lastArPose.ty(), lastArPose.tz()
            });
            rebuildLiveNavPath();

            if (announceStart) {
                if (multimodalManager != null) {
                    multimodalManager.onNavigationStarted(currentTargetNode.id);
                }
            }
            return true;
        } else {
            if (!keepRouteOnFailure) {
                isNavigating = false;
                synchronized (navPathPointsAr) {
                    navPathPointsAr.clear();
                }
                if (multimodalManager != null) {
                    multimodalManager.onNavigationMessage(tr("tts_path_not_found"), true);
                }
            }
            return false;
        }
    }

    private void showMainSettingsDialog() {
        String debugLabel = tr(isDebugging ? "settings_debug_off" : "settings_debug_on");
        String relocLabel = tr(isAutoLocalizingEnabled ? "settings_auto_reloc_off" : "settings_auto_reloc_on");
        String cleanUiLabel = tr(isCleanScreenModeEnabled ? "settings_clean_off" : "settings_clean_on");
        String accessibilityModeLabel = tr(isAccessibilityModeEnabled
                ? "settings_accessibility_mode_on"
                : "settings_accessibility_mode_off");
        String sceneLabel = tr("settings_scene_switch", currentSceneId);

        String[] options = {
                tr("settings_interaction_feedback"),
                tr("settings_navigation_features"),
                accessibilityModeLabel,
                tr("settings_visual_mode"),
                cleanUiLabel,
                tr("settings_announce_status"),
                debugLabel,
                relocLabel,
                sceneLabel,
                tr("settings_relocalize"),
                tr("settings_about"),
                tr("settings_exit")
        };

        AlertDialog settingsDialog = new AlertDialog.Builder(this)
                .setTitle(tr("dialog_settings_title"))
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showInteractionSettingsDialog();
                            break;
                        case 1:
                            showNavigationFeaturesDialog();
                            break;
                        case 2:
                            showAccessibilityModeDialog();
                            break;
                        case 3:
                            showAccessibilitySettings();
                            break;
                        case 4:
                            setCleanScreenMode(!isCleanScreenModeEnabled, true);
                            break;
                        case 5:
                            announceWhereAmIReport();
                            break;
                        case 6:
                            isDebugging = !isDebugging;
                            if (!isDebugging) {
                                relocDebugTextView.setVisibility(View.GONE);
                                debugCoordsTextView.setVisibility(View.GONE);
                            } else if (!isCleanScreenModeEnabled) {
                                updateDebugCoordsUI();
                            }
                            showToastTr(isDebugging ? "toast_debug_on" : "toast_debug_off");
                            break;
                        case 7:
                            isAutoLocalizingEnabled = !isAutoLocalizingEnabled;
                            showToastTr(isAutoLocalizingEnabled ? "toast_auto_reloc_on" : "toast_auto_reloc_off");
                            break;
                        case 8:
                            showSceneSwitchDialog();
                            break;
                        case 9:
                            performRelocalization();
                            break;
                        case 10:
                            AlertDialog aboutDialog = new AlertDialog.Builder(this)
                                    .setTitle(tr("dialog_about_title"))
                                    .setMessage(tr("dialog_about_message"))
                                    .setPositiveButton(tr("dialog_ok"), null)
                                    .show();
                            bindAccessibilityDialog(aboutDialog);
                            break;
                        case 11:
                            finish();
                            break;
                    }
                })
                .setNegativeButton(tr("dialog_close"), null)
                .show();
        bindAccessibilityDialog(settingsDialog);
    }

    private void showAccessibilityModeDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_accessibility_mode, null);
        Switch accessibilitySwitch = content.findViewById(R.id.switch_accessibility_mode);
        TextView accessibilityHint = content.findViewById(R.id.tv_accessibility_mode_hint);

        accessibilitySwitch.setText(tr("accessibility_mode_switch"));
        accessibilityHint.setText(tr("accessibility_mode_hint"));
        accessibilitySwitch.setChecked(isAccessibilityModeEnabled);

        AlertDialog accessibilityDialog = new AlertDialog.Builder(this)
                .setTitle(tr("dialog_accessibility_mode_title"))
                .setView(content)
                .setPositiveButton(tr("dialog_save"), (dialog, which) -> {
                    boolean enabled = accessibilitySwitch.isChecked();
                    boolean changed = enabled != isAccessibilityModeEnabled;
                    if (interactionConfigManager != null) {
                        interactionConfigManager.setAccessibilityModeEnabled(enabled);
                    }
                    applyAccessibilityMode(enabled);
                    if (changed) {
                        showToastTr(enabled
                                ? "toast_accessibility_mode_on"
                                : "toast_accessibility_mode_off");
                        if (ttsManager != null) {
                            ttsManager.speakUrgent(tr(enabled
                                    ? "tts_accessibility_mode_on"
                                    : "tts_accessibility_mode_off"));
                        }
                    }
                })
                .setNegativeButton(tr("dialog_cancel"), null)
                .show();
        bindAccessibilityDialog(accessibilityDialog);
    }

    private void showNavigationFeaturesDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_navigation_features, null);
        Switch narrowDoorSwitch = content.findViewById(R.id.switch_narrow_door_detection);
        Switch corridorModeSwitch = content.findViewById(R.id.switch_corridor_mode);
        TextView narrowDoorHint = content.findViewById(R.id.tv_narrow_door_setting_hint);
        TextView corridorModeHint = content.findViewById(R.id.tv_corridor_mode_setting_hint);

        narrowDoorSwitch.setText(tr("navigation_feature_narrow_door"));
        corridorModeSwitch.setText(tr("navigation_feature_corridor_mode"));
        narrowDoorHint.setText(tr("navigation_feature_narrow_door_hint"));
        corridorModeHint.setText(tr("navigation_feature_corridor_hint"));
        narrowDoorSwitch.setChecked(interactionConfigManager.isNarrowDoorDetectionEnabled());
        corridorModeSwitch.setChecked(interactionConfigManager.isCorridorModeEnabled());

        AlertDialog navigationFeaturesDialog = new AlertDialog.Builder(this)
                .setTitle(tr("dialog_navigation_features_title"))
                .setView(content)
                .setPositiveButton(tr("dialog_save"), (dialog, which) -> {
                    boolean oldNarrowDoorEnabled =
                            interactionConfigManager.isNarrowDoorDetectionEnabled();
                    boolean oldCorridorModeEnabled =
                            interactionConfigManager.isCorridorModeEnabled();
                    boolean newNarrowDoorEnabled = narrowDoorSwitch.isChecked();
                    boolean newCorridorModeEnabled = corridorModeSwitch.isChecked();

                    interactionConfigManager.setNarrowDoorDetectionEnabled(newNarrowDoorEnabled);
                    interactionConfigManager.setCorridorModeEnabled(newCorridorModeEnabled);
                    applyNavigationFeatureSettings(
                            newNarrowDoorEnabled,
                            newCorridorModeEnabled
                    );

                    if (oldNarrowDoorEnabled != newNarrowDoorEnabled) {
                        showToastTr(newNarrowDoorEnabled
                                ? "toast_narrow_door_on"
                                : "toast_narrow_door_off");
                    }
                    if (oldCorridorModeEnabled != newCorridorModeEnabled) {
                        showToastTr(newCorridorModeEnabled
                                ? "toast_corridor_mode_on"
                                : "toast_corridor_mode_off");
                    }
                })
                .setNegativeButton(tr("dialog_cancel"), null)
                .show();
        bindAccessibilityDialog(navigationFeaturesDialog);
    }

    private void applyNavigationFeatureSettings(boolean narrowDoorEnabled,
                                                boolean corridorModeEnabled) {
        if (!narrowDoorEnabled) {
            latestNarrowDoorEvidence = null;
            narrowDoorAlertController.reset();
        }

        if (!corridorModeEnabled) {
            corridorModeController.reset();
            if (navEngine != null) {
                navEngine.setCorridorMode(false);
            }
        }
    }

    private void showAccessibilitySettings() {
        String[] modes = {
                tr("visual_mode_default"),
                tr("visual_mode_high_contrast"),
                tr("visual_mode_color_assist")
        };
        AlertDialog visualSettingsDialog = new AlertDialog.Builder(this)
                .setTitle(tr("dialog_visual_mode_title"))
                .setSingleChoiceItems(modes, visualManager.getCurrentMode(), (dialog, which) -> {
                    visualManager.setMode(which);
                    applyVisualModeAndAccessibility();
                    if (multimodalManager != null) {
                        multimodalManager.onVisualModeChanged(modes[which]);
                    }
                    dialog.dismiss();
                })
                .show();
        bindAccessibilityDialog(visualSettingsDialog);
    }

    private void showInteractionSettingsDialog() {
        String verbosityLabel =
                interactionConfigManager.getVerbosityLevel() == InteractionConfigManager.VerbosityLevel.VERBOSE
                        ? tr("interaction_verbosity_to_essential")
                        : tr("interaction_verbosity_to_verbose");

        String hapticLabel =
                interactionConfigManager.isHapticEnabled()
                        ? tr("interaction_haptic_disable")
                        : tr("interaction_haptic_enable");

        String repeatLabel =
                interactionConfigManager.isRepeatButtonEnabled()
                        ? tr("interaction_repeat_disable")
                        : tr("interaction_repeat_enable");

        String sensitivityLabel = tr(
                "interaction_sensitivity",
                getSensitivityDisplayName(interactionConfigManager.getHapticSensitivity())
        );

        String[] options = {
                verbosityLabel,
                hapticLabel,
                sensitivityLabel,
                repeatLabel,
                tr("interaction_restore_defaults")
        };

        AlertDialog interactionSettingsDialog = new AlertDialog.Builder(this)
                .setTitle(tr("dialog_interaction_title"))
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            InteractionConfigManager.VerbosityLevel newLevel =
                                    interactionConfigManager.getVerbosityLevel() == InteractionConfigManager.VerbosityLevel.VERBOSE
                                            ? InteractionConfigManager.VerbosityLevel.ESSENTIAL
                                            : InteractionConfigManager.VerbosityLevel.VERBOSE;
                            interactionConfigManager.setVerbosityLevel(newLevel);
                            if (multimodalManager != null) {
                                multimodalManager.onVerbosityChanged(newLevel);
                            }
                            break;
                        case 1:
                            boolean enabled = !interactionConfigManager.isHapticEnabled();
                            interactionConfigManager.setHapticEnabled(enabled);
                            if (multimodalManager != null) {
                                multimodalManager.onHapticToggled(enabled);
                            }
                            break;
                        case 2:
                            showSensitivityDialog();
                            break;
                        case 3:
                            boolean repeatEnabled = !interactionConfigManager.isRepeatButtonEnabled();
                            interactionConfigManager.setRepeatButtonEnabled(repeatEnabled);
                            if (multimodalManager != null) {
                                multimodalManager.onRepeatButtonToggled(repeatEnabled);
                            }
                            break;
                        case 4:
                            interactionConfigManager.resetToDefaults();
                            navEngine.applyVeerSensitivity(interactionConfigManager.getHapticSensitivity());
                            if (multimodalManager != null) {
                                multimodalManager.onNavigationMessage(tr("tts_restore_interaction_defaults"), false);
                            }
                            break;
                    }
                })
                .setNegativeButton(tr("dialog_close"), null)
                .show();
        bindAccessibilityDialog(interactionSettingsDialog);
    }

    private void showSensitivityDialog() {
        final InteractionConfigManager.HapticSensitivity[] levels =
                InteractionConfigManager.HapticSensitivity.values();

        String[] labels = new String[levels.length];
        int checkedIndex = 0;
        for (int i = 0; i < levels.length; i++) {
            labels[i] = getSensitivityDisplayName(levels[i]);
            if (levels[i] == interactionConfigManager.getHapticSensitivity()) {
                checkedIndex = i;
            }
        }

        AlertDialog sensitivityDialog = new AlertDialog.Builder(this)
                .setTitle(tr("dialog_sensitivity_title"))
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    InteractionConfigManager.HapticSensitivity selected = levels[which];
                    interactionConfigManager.setHapticSensitivity(selected);
                    navEngine.applyVeerSensitivity(selected);
                    if (multimodalManager != null) {
                        multimodalManager.onSensitivityChanged(selected);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(tr("dialog_cancel"), null)
                .show();
        bindAccessibilityDialog(sensitivityDialog);
    }

    private String getSensitivityDisplayName(InteractionConfigManager.HapticSensitivity sensitivity) {
        switch (sensitivity) {
            case HIGH:
                return tr("sensitivity_high");
            case LOW:
                return tr("sensitivity_low");
            case MEDIUM:
            default:
                return tr("sensitivity_medium");
        }
    }

    private String getCurrentTargetName() {
        return currentTargetNode != null ? currentTargetNode.id : null;
    }

    private boolean applyVoiceInteractionSetting(String spoken) {
        if (containsAnyPhrase(spoken, "详尽模式", "详细模式", "verbose mode", "detailed mode")) {
            interactionConfigManager.setVerbosityLevel(InteractionConfigManager.VerbosityLevel.VERBOSE);
            if (multimodalManager != null) {
                multimodalManager.onVerbosityChanged(InteractionConfigManager.VerbosityLevel.VERBOSE);
            }
            return true;
        }
        if (containsAnyPhrase(spoken, "简略模式", "精简模式", "essential mode", "simple mode")) {
            interactionConfigManager.setVerbosityLevel(InteractionConfigManager.VerbosityLevel.ESSENTIAL);
            if (multimodalManager != null) {
                multimodalManager.onVerbosityChanged(InteractionConfigManager.VerbosityLevel.ESSENTIAL);
            }
            return true;
        }
        if (containsAnyPhrase(spoken, "开启触觉", "打开触觉", "enable haptics", "turn on haptics")) {
            interactionConfigManager.setHapticEnabled(true);
            if (multimodalManager != null) {
                multimodalManager.onHapticToggled(true);
            }
            return true;
        }
        if (containsAnyPhrase(spoken, "关闭触觉", "disable haptics", "turn off haptics")) {
            interactionConfigManager.setHapticEnabled(false);
            if (multimodalManager != null) {
                multimodalManager.onHapticToggled(false);
            }
            return true;
        }
        if (containsAnyPhrase(spoken, "高灵敏度", "high sensitivity")) {
            interactionConfigManager.setHapticSensitivity(InteractionConfigManager.HapticSensitivity.HIGH);
            navEngine.applyVeerSensitivity(InteractionConfigManager.HapticSensitivity.HIGH);
            if (multimodalManager != null) {
                multimodalManager.onSensitivityChanged(InteractionConfigManager.HapticSensitivity.HIGH);
            }
            return true;
        }
        if (containsAnyPhrase(spoken, "中灵敏度", "medium sensitivity")) {
            interactionConfigManager.setHapticSensitivity(InteractionConfigManager.HapticSensitivity.MEDIUM);
            navEngine.applyVeerSensitivity(InteractionConfigManager.HapticSensitivity.MEDIUM);
            if (multimodalManager != null) {
                multimodalManager.onSensitivityChanged(InteractionConfigManager.HapticSensitivity.MEDIUM);
            }
            return true;
        }
        if (containsAnyPhrase(spoken, "低灵敏度", "low sensitivity")) {
            interactionConfigManager.setHapticSensitivity(InteractionConfigManager.HapticSensitivity.LOW);
            navEngine.applyVeerSensitivity(InteractionConfigManager.HapticSensitivity.LOW);
            if (multimodalManager != null) {
                multimodalManager.onSensitivityChanged(InteractionConfigManager.HapticSensitivity.LOW);
            }
            return true;
        }
        return false;
    }

    private boolean containsAnyPhrase(String spoken, String... phrases) {
        if (spoken == null || phrases == null) {
            return false;
        }

        String normalized = spoken.toLowerCase(Locale.ROOT);
        for (String phrase : phrases) {
            if (phrase == null || phrase.isEmpty()) {
                continue;
            }
            if (normalized.contains(phrase.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void renderARContent() {
        if (!isLocalized) return;

        boolean trackingRecoveredRecently =
                SystemClock.elapsedRealtime() - trackingRecoveredAtMs < TRACKING_RECOVERY_COOLDOWN_MS;

        if (showCameraPath) {
            updatePathNodeArPositions();
            synchronized (pathNodePointsAr) {
                pointRenderer.drawPathNodes(
                        viewMatrix,
                        projectionMatrix,
                        new ArrayList<>(pathNodePointsAr)
                );
            }
        }

        if (!doorplatePointsAr.isEmpty()) {
            pointRenderer.drawDestinations(
                    viewMatrix,
                    projectionMatrix,
                    new ArrayList<>(doorplatePointsAr)
            );
        }

        if (isNavigating) {
            rebuildLiveNavPath();
            synchronized (navPathPointsAr) {
                if (!navPathPointsAr.isEmpty()) {
                    pathRenderer.draw(viewMatrix, projectionMatrix, new ArrayList<>(navPathPointsAr));
                    if (!trackingRecoveredRecently && renderFrameCounter % 30 == 0) {
                        runOnUiThread(this::updateNavigationLogic);
                    }
                }
            }
        }
    }

    private void updateNavigationLogic() {
        if (!isNavigating || isNarrowDoorTestMode || lastArPose == null) return;

        final List<float[]> pathSnapshot;
        synchronized (navPathPointsAr) {
            pathSnapshot = new ArrayList<>(navPathPointsAr);
        }

        final float[] viewMatrixSnapshot = viewMatrix != null ? viewMatrix.clone() : null;

        updateCorridorMode(
                lastArPose.tx(),
                lastArPose.tz(),
                pathSnapshot,
                viewMatrixSnapshot
        );

        navEngine.update(
                lastArPose.tx(),
                lastArPose.ty(),
                lastArPose.tz(),
                pathSnapshot,
                viewMatrixSnapshot,
                new NavigationEngine.NavigationCallback() {
                    @Override
                    public void onArrived() {
                        Log.d(TAG, "navigation callback: arrived");
                        isNavigating = false;
                        corridorModeController.reset();
                        navEngine.setCorridorMode(false);
                        latestDepthCorridorEvidence = null;
                        latestNarrowDoorEvidence = null;
                        narrowDoorAlertController.reset();
                        synchronized (navPathPointsAr) {
                            navPathPointsAr.clear();
                        }
                        if (multimodalManager != null) {
                            multimodalManager.onArrived();
                        }
                    }

                    @Override
                    public void onPreTurnInstruction(int direction, float distanceMeters) {
                        Log.d(TAG, "navigation callback: pre_turn=" + direction + ", distance=" + distanceMeters);
                        if (multimodalManager != null) {
                            multimodalManager.onPreTurnInstruction(direction, distanceMeters);
                        }
                    }

                    @Override
                    public void onTurnNow(int direction) {
                        Log.d(TAG, "navigation callback: turn_now=" + direction);
                        if (multimodalManager != null) {
                            multimodalManager.onTurnNow(direction);
                        }
                    }

                    @Override
                    public void onHeadingGuidance(int direction) {
                        Log.d(TAG, "navigation callback: heading=" + direction);
                        if (multimodalManager != null) {
                            multimodalManager.onHeadingGuidance(direction);
                        }
                    }

                    @Override
                    public void onVeerDetected(int veerDirection, float lateralOffsetMeters) {
                        Log.d(TAG, "navigation callback: veer=" + veerDirection + ", offset=" + lateralOffsetMeters);
                        if (multimodalManager != null) {
                            multimodalManager.onVeerDetected(veerDirection, lateralOffsetMeters);
                        }
                        replanNavigationFromCurrentPose();
                    }

                    @Override
                    public void onVeerRecovered() {
                        Log.d(TAG, "navigation callback: veer recovered");
                        if (multimodalManager != null) {
                            multimodalManager.onVeerRecovered();
                        }
                    }
                }
        );
        int passed = navEngine.consumePassedPathPoints();
        if (passed > 0 && !currentPathNodeIds.isEmpty()) {
            int removeCount = Math.min(passed, currentPathNodeIds.size());
            for (int i = 0; i < removeCount; i++) {
                currentPathNodeIds.remove(0);
            }
            rebuildLiveNavPath();
        }
    }

    private void updateCorridorMode(float currentX,
                                    float currentZ,
                                    List<float[]> pathSnapshot,
                                    float[] viewMatrixSnapshot) {
        if (!interactionConfigManager.isCorridorModeEnabled()) {
            corridorModeController.reset();
            if (navEngine.isCorridorMode()) {
                navEngine.setCorridorMode(false);
            }
            return;
        }

        CorridorModeController.Decision decision = corridorModeController.update(
                currentX,
                currentZ,
                pathSnapshot,
                navEngine.getRouteReferencePoint(),
                viewMatrixSnapshot,
                latestDepthCorridorEvidence,
                isDepthModeSupported,
                SystemClock.elapsedRealtime()
        );

        boolean shouldEnableCorridorMode =
                decision.mode == CorridorModeController.Mode.CORRIDOR;
        if (navEngine.isCorridorMode() != shouldEnableCorridorMode) {
            navEngine.setCorridorMode(shouldEnableCorridorMode);
        }

        if (decision.modeChanged) {
            Log.i(TAG, "corridor_mode_changed: mode=" + decision.mode
                    + ", headingDot=" + decision.headingDot
                    + ", lateralOffset=" + decision.lateralOffsetMeters
                    + ", depthUsable=" + decision.depthEvidenceUsable
                    + ", spatiallyEligible=" + decision.spatiallyEligible);
        }
    }

    private void showDestinationDialog() {
        List<Node> candidates = !doorplateNodes.isEmpty() ? doorplateNodes : allNodes;
        if (candidates.isEmpty()) return;

        String[] nodeNames = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            nodeNames[i] = candidates.get(i).id;
        }

        AlertDialog destinationDialog = new AlertDialog.Builder(this)
                .setTitle(tr("dialog_destination_title"))
                .setItems(nodeNames, (d, which) -> navigateToNode(candidates.get(which)))
                .show();
        bindAccessibilityDialog(destinationDialog);
    }

    private void performRelocalization() {
        isLocalized = false;
        isNavigating = false;
        lastRerouteAtMs = 0L;
        isRerouting = false;
        corridorModeController.reset();
        navEngine.setCorridorMode(false);
        latestDepthCorridorEvidence = null;
        latestNarrowDoorEvidence = null;
        narrowDoorAlertController.reset();

        currentTargetNode = null;
        currentTargetNodeId = null;
        currentPathNodeIds.clear();

        originalRelativeNodes = null;
        capturePoseForScale = null;

        clearLocalizationAnchor();

        allNodes.clear();
        doorplateNodes.clear();
        pathNodes.clear();
        doorplatePointsAr.clear();

        synchronized (pathNodePointsAr) {
            pathNodePointsAr.clear();
        }
        synchronized (navPathPointsAr) {
            navPathPointsAr.clear();
        }

        localizeButton.setEnabled(true);
        selectDestButton.setEnabled(false);
        relocDebugTextView.setVisibility(View.GONE);
        updateStatusTr("status_reset");

        if (multimodalManager != null) {
            multimodalManager.clearObstacleAlert();
            multimodalManager.onRelocalizationReset();
        }

        lastObstacleDistanceMeters = -1f;
        lastObstacleLabel = null;

        currentTargetNodeId = null;
        currentPathNodeIds.clear();
    }

    private void updateStatus(String msg) {
        runOnUiThread(() -> {
            statusTextView.setText(msg);
            statusTextView.setVisibility(isCleanScreenModeEnabled ? View.GONE : View.VISIBLE);
        });
    }

    private void updateDebugCoordsUI() {
        if (lastArPose == null) return;

        String obstaclePart = lastObstacleDistanceMeters > 0f
                ? String.format(
                Locale.US,
                "\nOBS: %s %.2fm",
                lastObstacleLabel == null ? "object" : lastObstacleLabel,
                lastObstacleDistanceMeters
        )
                : "\nOBS: none";

        String debugMsg = String.format(
                Locale.US,
                "AR: X:%.2f Y:%.2f Z:%.2f%s",
                lastArPose.tx(),
                lastArPose.ty(),
                lastArPose.tz(),
                obstaclePart
        );

        runOnUiThread(() -> {
            debugCoordsTextView.setVisibility(
                    !isCleanScreenModeEnabled && isDebugging ? View.VISIBLE : View.GONE
            );
            if (!isCleanScreenModeEnabled) {
                debugCoordsTextView.setText(debugMsg);
            }
        });
    }

    private void sendDebugDiagnostics() {
        if (networkRepo != null && lastArPose != null) {
            networkRepo.sendLogToServer("AR_LOG", "Diag", "Pos: " + lastArPose.tx());
        }
    }

    private void updatePathNodeArPositions() {
        synchronized (pathNodePointsAr) {
            pathNodePointsAr.clear();
            for (Node node : pathNodes) {
                pathNodePointsAr.add(new float[]{node.arX, node.arY, node.arZ});
            }
        }
    }

    @Override
    public void onSurfaceCreated(SampleRender render) {
        try {
            backgroundRenderer = new BackgroundRenderer(render);
            backgroundRenderer.setUseDepthVisualization(render, false);
            pathRenderer = new PathRenderer(render);
            pointRenderer = new PointRenderer();
        } catch (IOException e) {
            Log.e(TAG, "加载失败", e);
        }
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!ensureCameraPermissionForAr()) {
            return;
        }

        if (!ensureArCoreSessionReady()) {
            return;
        }

        try {
            configureSession();
            session.resume();

            surfaceView.onResume();
            displayRotationHelper.onResume();

            if (localizeButton != null && !isLocalized) {
                localizeButton.setEnabled(true);
            }

            if (isDepthModeSupported) {
                updateStatusTr("status_arcore_ready");
            } else {
                updateStatusTr("status_arcore_ready_no_depth");
            }

        } catch (CameraNotAvailableException e) {
            reportArCoreIssue(tr("arcore_camera_not_available"), e);
            releaseSessionOnError();
        } catch (Exception e) {
            reportArCoreIssue(describeArCoreException(e), e);
            releaseSessionOnError();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (session != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }

        if (voiceManager != null) {
            voiceManager.cancelListening();
        }

        if (multimodalManager != null) {
            multimodalManager.stopAllHaptics();
        }
    }

    @Override
    protected void onDestroy() {
        if (multimodalManager != null) {
            multimodalManager.stopAllHaptics();
        }

        if (localVisionDetector != null) {
            localVisionDetector.close();
            localVisionDetector = null;
        }

        if (ttsManager != null) {
            ttsManager.destroy();
        }

        if (voiceManager != null) {
            voiceManager.destroy();
        }

        clearLocalizationAnchor();
        clearObstacleOverlay();
        if (session != null) {
            session.close();
        }

        super.onDestroy();
    }

    private void configureSession() {
        Config config = session.getConfig();
        config.setFocusMode(Config.FocusMode.AUTO);

        isDepthModeSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
        if (isDepthModeSupported) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
            Log.i(TAG, "ARCore depth mode enabled for local obstacle detection");
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
            Log.w(TAG, "Depth API not supported on this device; local obstacle ranging disabled");
        }

        session.configure(config);
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(rc, p, r);

        if (rc == PERMISSION_CODE_RECORD_AUDIO) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
                if (ttsManager != null) {
                    ttsManager.stop();
                }
                if (multimodalManager != null) {
                    multimodalManager.onVoiceWakeup();
                }
                updateStatusTr("status_start_voice");
                if (voiceManager != null) {
                    voiceManager.startListening();
                }
            } else {
                updateStatusTr("status_no_audio_permission");
            }
            return;
        }

        if (rc == PERMISSION_CODE_CAMERA) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
                updateStatusTr("status_camera_permission_granted");
                onResume();
            } else {
                reportArCoreIssue(tr("arcore_no_camera_permission"), null);
            }
            return;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, f);
    }

    private void announceWhereAmIReport() {
        String report = buildWhereAmIReport();
        updateStatus(report);

        if (ttsManager != null) {
            ttsManager.speakUrgent(report);
        }

        if (networkRepo != null) {
            networkRepo.sendLogToServer(
                    "WHERE_AM_I",
                    "announce_where_am_i",
                    report
            );
        }
    }

    private String buildWhereAmIReport() {
        return navigationReportBuilder.build(new NavigationReportBuilder.ReportData(
                isLocalized,
                isNavigating,
                lastArPose,
                viewMatrix != null ? viewMatrix.clone() : null,
                allNodes,
                doorplateNodes,
                pathNodes,
                currentTargetNode,
                currentPathNodeIds,
                getGroundY(),
                (int) WHERE_AM_I_NEARBY_NODE_COUNT,
                WHERE_AM_I_TURN_ANGLE_THRESHOLD_DEG,
                getAppLanguage()
        ));
    }

}

