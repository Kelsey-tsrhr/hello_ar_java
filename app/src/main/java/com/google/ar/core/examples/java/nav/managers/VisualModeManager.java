package com.google.ar.core.examples.java.nav.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.FrameLayout;

import com.google.ar.core.examples.java.nav.R; // 替换为你的 R 包名

public class VisualModeManager {
    public static final int MODE_DEFAULT = 0;
    public static final int MODE_CATARACTS = 1;
    public static final int MODE_COLOR_BLIND = 2;

    private int currentVisualMode;
    private SharedPreferences prefs;

    // 缓存原始样式
    private Drawable defaultBtnBg;
    private Drawable defaultSettingsBg;
    private int defaultTextColor;
    private android.content.res.ColorStateList defaultBtnTextColor;

    public VisualModeManager(Context context) {
        prefs = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        currentVisualMode = prefs.getInt("VisualMode", MODE_DEFAULT);
    }

    public void saveDefaultStyles(Button btn, ImageButton settingsBtn, TextView statusTv) {
        defaultBtnBg = btn.getBackground();
        defaultSettingsBg = settingsBtn.getBackground();
        defaultTextColor = statusTv.getCurrentTextColor();
        defaultBtnTextColor = btn.getTextColors();
    }

    public void setMode(int mode) {
        this.currentVisualMode = mode;
        prefs.edit().putInt("VisualMode", mode).apply();
    }

    public int getCurrentMode() {
        return currentVisualMode;
    }

    public void applyVisualMode(Button localizeBtn, Button destBtn, ImageButton settingsBtn,
                                TextView statusTv, FrameLayout labelContainer) {
        int bgColor, textColor, btnColor, btnTextColor;
        float textSizeScale = 1.0f;

        switch (currentVisualMode) {
            case MODE_CATARACTS: // 黑底黄字
                bgColor = 0xFF000000;
                textColor = 0xFFFFFF00;
                btnColor = 0xFFFFFF00;
                btnTextColor = 0xFF000000;
                textSizeScale = 1.5f;
                break;
            case MODE_COLOR_BLIND: // 蓝底白字
                bgColor = 0xFFF0F0F0;
                textColor = 0xFFFFFFFF;
                btnColor = 0xFF0057D9;
                btnTextColor = 0xFFFFFFFF;
                textSizeScale = 1.1f;
                break;
            default: // 恢复默认
                localizeBtn.setBackground(defaultBtnBg);
                destBtn.setBackground(defaultBtnBg);
                settingsBtn.setBackground(defaultSettingsBg);
                localizeBtn.setTextColor(defaultBtnTextColor);
                destBtn.setTextColor(defaultBtnTextColor);
                statusTv.setTextColor(defaultTextColor);
                statusTv.setBackgroundColor(0x80000000);
                settingsBtn.clearColorFilter();
                updateTextSizes(textSizeScale, localizeBtn, destBtn, statusTv, labelContainer);
                return;
        }

        // 应用非默认模式颜色
        statusTv.setTextColor(textColor);
        statusTv.setBackgroundColor(currentVisualMode == MODE_CATARACTS ? 0xFF000000 : 0x800057D9);
        localizeBtn.setBackgroundColor(btnColor);
        localizeBtn.setTextColor(btnTextColor);
        destBtn.setBackgroundColor(btnColor);
        destBtn.setTextColor(btnTextColor);
        settingsBtn.setBackgroundResource(R.drawable.rounded_bg);
        settingsBtn.setColorFilter(textColor);

        updateTextSizes(textSizeScale, localizeBtn, destBtn, statusTv, labelContainer);
        updateLabelContainerColors(labelContainer, textColor, textSizeScale, currentVisualMode == MODE_CATARACTS);
    }

    private void updateTextSizes(float scale, Button b1, Button b2, TextView tv, FrameLayout container) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * scale);
        b1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * scale);
        b2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * scale);
    }

    private void updateLabelContainerColors(FrameLayout container, int textColor, float scale, boolean isCataract) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                tv.setTextColor(textColor);
                tv.setTextSize(14 * scale);
                tv.setBackgroundColor(isCataract ? 0xFF000000 : 0x80000000);
            }
        }
    }
}