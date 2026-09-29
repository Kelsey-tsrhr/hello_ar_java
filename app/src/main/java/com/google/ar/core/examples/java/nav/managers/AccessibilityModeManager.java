package com.google.ar.core.examples.java.nav.managers;

import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主界面无障碍模式的统一样式和可访问性处理。
 *
 * <p>该模式面向盲人触摸操作：单击只朗读控件名称，双击才执行原有点击动作。
 * 它不改变颜色，只提供更大的触控区域、焦点能力和明确的内容描述；如果使用
 * TalkBack，控件仍然保留为可聚焦、可朗读控件。</p>
 */
public final class AccessibilityModeManager {
    private static final int ACCESSIBLE_BUTTON_MIN_HEIGHT_DP = 64;
    private static final int ACCESSIBLE_BUTTON_MIN_WIDTH_DP = 112;
    private static final int ACCESSIBLE_ICON_BUTTON_MIN_SIZE_DP = 64;
    private static final int ACCESSIBLE_HORIZONTAL_PADDING_DP = 16;
    private static final int ACCESSIBLE_VERTICAL_PADDING_DP = 12;
    private static final float ACCESSIBLE_TEXT_SIZE_SP = 20f;

    private final float density;
    private final Map<View, ViewState> originalStates = new IdentityHashMap<>();
    private final Map<View, GestureDetector> gestureDetectors = new IdentityHashMap<>();
    private final Map<View, GestureDetector> dialogGestureDetectors = new IdentityHashMap<>();
    private SingleTapListener singleTapListener;
    private boolean enabled;

    public interface SingleTapListener {
        void onSingleTap(View view);
    }

    public AccessibilityModeManager(float density) {
        this.density = density > 0f ? density : 1f;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setSingleTapListener(SingleTapListener listener) {
        singleTapListener = listener;
    }

    /**
     * 为已经显示的设置/选择对话框安装同样的单击朗读、双击执行规则。
     * 对话框关闭后必须调用 {@link #unbindDialog(View, ListView)}。
     */
    public void bindDialog(View dialogRoot, ListView listView) {
        if (!enabled) return;
        clearGestureDetectors(dialogGestureDetectors);
        bindDialogControls(dialogRoot);
        if (listView != null) {
            installListViewInteraction(listView);
        }
    }

    public void unbindDialog(View dialogRoot, ListView listView) {
        clearGestureDetectors(dialogGestureDetectors);
    }

    /**
     * 应用或恢复无障碍样式。传入 ViewGroup 时会递归处理其中的文本控件，
     * 便于同步放大动态生成的路径标签。
     */
    public void apply(boolean shouldEnable, View... roots) {
        if (shouldEnable) {
            if (roots != null) {
                for (View root : roots) {
                    applyRecursively(root);
                }
            }
            enabled = true;
            return;
        }

        restoreOriginalStates();
        enabled = false;
    }

    private void applyRecursively(View view) {
        if (view == null) return;

        if (view instanceof TextView || view instanceof ImageView) {
            captureStateIfNeeded(view);
            applyToView(view);
        }

        if (isActionableControl(view)) {
            installDoubleTapInteraction(view, gestureDetectors);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursively(group.getChildAt(i));
            }
        }
    }

    private void captureStateIfNeeded(View view) {
        if (!originalStates.containsKey(view)) {
            originalStates.put(view, new ViewState(view));
        }
    }

    private void applyToView(View view) {
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        view.setFocusable(true);

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null) {
            if (layoutParams.width >= 0
                    && view instanceof ImageView) {
                layoutParams.width = Math.max(
                        layoutParams.width,
                        dp(ACCESSIBLE_ICON_BUTTON_MIN_SIZE_DP)
                );
            }
            if (layoutParams.height >= 0
                    && (view instanceof TextView || view instanceof ImageView)) {
                layoutParams.height = Math.max(
                        layoutParams.height,
                        dp(view instanceof ImageView
                                ? ACCESSIBLE_ICON_BUTTON_MIN_SIZE_DP
                                : ACCESSIBLE_BUTTON_MIN_HEIGHT_DP)
                );
            }
            view.setLayoutParams(layoutParams);
        }

        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            int size = dp(ACCESSIBLE_ICON_BUTTON_MIN_SIZE_DP);
            imageView.setMinimumWidth(size);
            imageView.setMinimumHeight(size);
        }

        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setMinimumHeight(dp(ACCESSIBLE_BUTTON_MIN_HEIGHT_DP));
            textView.setMinimumWidth(dp(ACCESSIBLE_BUTTON_MIN_WIDTH_DP));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ACCESSIBLE_TEXT_SIZE_SP);
            textView.setPadding(
                    Math.max(textView.getPaddingLeft(), dp(ACCESSIBLE_HORIZONTAL_PADDING_DP)),
                    Math.max(textView.getPaddingTop(), dp(ACCESSIBLE_VERTICAL_PADDING_DP)),
                    Math.max(textView.getPaddingRight(), dp(ACCESSIBLE_HORIZONTAL_PADDING_DP)),
                    Math.max(textView.getPaddingBottom(), dp(ACCESSIBLE_VERTICAL_PADDING_DP))
            );
        }
    }

    private boolean isActionableControl(View view) {
        return view instanceof android.widget.Button
                || view instanceof CompoundButton
                || view instanceof android.widget.ImageButton;
    }

    private void bindDialogControls(View view) {
        if (view == null) return;
        if (isActionableControl(view)) {
            installDoubleTapInteraction(view, dialogGestureDetectors);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                bindDialogControls(group.getChildAt(i));
            }
        }
    }

    private void installDoubleTapInteraction(View view,
                                             Map<View, GestureDetector> detectorMap) {
        GestureDetector detector = new GestureDetector(
                view.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent event) {
                        if (singleTapListener != null && view.isEnabled()) {
                            singleTapListener.onSingleTap(view);
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent event) {
                        if (view.isEnabled()) {
                            view.performClick();
                        }
                        return true;
                    }
                }
        );
        detector.setIsLongpressEnabled(false);
        detectorMap.put(view, detector);
        view.setOnTouchListener((touchedView, event) -> {
            if (!enabled || !touchedView.isEnabled()) {
                return false;
            }
            // 始终消费触摸事件，防止第一次单击在双击确认前触发原有 OnClickListener。
            detector.onTouchEvent(event);
            return true;
        });
    }

    private void installListViewInteraction(ListView listView) {
        GestureDetector detector = new GestureDetector(
                listView.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent event) {
                        View row = findListRow(listView, event);
                        if (row != null && singleTapListener != null) {
                            singleTapListener.onSingleTap(row);
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent event) {
                        int position = listView.pointToPosition(
                                Math.round(event.getX()),
                                Math.round(event.getY())
                        );
                        if (position >= 0
                                && listView.getAdapter() != null
                                && position < listView.getAdapter().getCount()) {
                            View row = findListRow(listView, event);
                            listView.performItemClick(
                                    row,
                                    position,
                                    listView.getAdapter().getItemId(position)
                            );
                        }
                        return true;
                    }
                }
        );
        detector.setIsLongpressEnabled(false);
        dialogGestureDetectors.put(listView, detector);
        listView.setOnTouchListener((touchedView, event) -> {
            if (!enabled) return false;
            detector.onTouchEvent(event);
            return true;
        });
    }

    private View findListRow(ListView listView, MotionEvent event) {
        int position = listView.pointToPosition(
                Math.round(event.getX()),
                Math.round(event.getY())
        );
        if (position < 0) return null;
        int childIndex = position - listView.getFirstVisiblePosition();
        if (childIndex < 0 || childIndex >= listView.getChildCount()) return null;
        return listView.getChildAt(childIndex);
    }

    private void clearGestureDetectors(Map<View, GestureDetector> detectors) {
        List<View> views = new ArrayList<>(detectors.keySet());
        for (View view : views) {
            if (view != null) {
                view.setOnTouchListener(null);
            }
        }
        detectors.clear();
    }

    private void restoreOriginalStates() {
        clearGestureDetectors(gestureDetectors);
        clearGestureDetectors(dialogGestureDetectors);

        for (Map.Entry<View, ViewState> entry : originalStates.entrySet()) {
            View view = entry.getKey();
            ViewState state = entry.getValue();
            if (view == null || state == null) continue;

            view.setMinimumWidth(state.minimumWidth);
            view.setMinimumHeight(state.minimumHeight);
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.width = state.layoutWidth;
                layoutParams.height = state.layoutHeight;
                view.setLayoutParams(layoutParams);
            }
            view.setPadding(
                    state.paddingLeft,
                    state.paddingTop,
                    state.paddingRight,
                    state.paddingBottom
            );
            view.setImportantForAccessibility(state.importantForAccessibility);
            view.setFocusable(state.focusable);

            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, state.textSizePx);
            }
        }
        originalStates.clear();
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    private static final class ViewState {
        final int minimumWidth;
        final int minimumHeight;
        final int layoutWidth;
        final int layoutHeight;
        final int paddingLeft;
        final int paddingTop;
        final int paddingRight;
        final int paddingBottom;
        final int importantForAccessibility;
        final boolean focusable;
        final float textSizePx;

        ViewState(View view) {
            minimumWidth = view.getMinimumWidth();
            minimumHeight = view.getMinimumHeight();
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            layoutWidth = layoutParams == null ? ViewGroup.LayoutParams.WRAP_CONTENT : layoutParams.width;
            layoutHeight = layoutParams == null ? ViewGroup.LayoutParams.WRAP_CONTENT : layoutParams.height;
            paddingLeft = view.getPaddingLeft();
            paddingTop = view.getPaddingTop();
            paddingRight = view.getPaddingRight();
            paddingBottom = view.getPaddingBottom();
            importantForAccessibility = view.getImportantForAccessibility();
            focusable = view.isFocusable();
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                textSizePx = textView.getTextSize();
            } else {
                textSizePx = 0f;
            }
        }
    }
}
