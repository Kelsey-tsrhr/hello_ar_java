package com.google.ar.core.examples.java.nav.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ObstacleOverlayView extends View {

    public static class BoxItem {
        public final RectF rect;
        public final String text;
        public final boolean primary;

        public BoxItem(@NonNull RectF rect, @Nullable String text, boolean primary) {
            this.rect = new RectF(rect);
            this.text = text == null ? "" : text;
            this.primary = primary;
        }
    }

    private final List<BoxItem> items = new ArrayList<>();

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint primaryBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ObstacleOverlayView(Context context) {
        super(context);
        init();
    }

    public ObstacleOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);
        boxPaint.setColor(0xFF00E676);

        primaryBoxPaint.setStyle(Paint.Style.STROKE);
        primaryBoxPaint.setStrokeWidth(8f);
        primaryBoxPaint.setColor(0xFFFF5252);

        textBgPaint.setStyle(Paint.Style.FILL);
        textBgPaint.setColor(0xB0000000);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(36f);
    }

    public synchronized void setBoxes(@Nullable List<BoxItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        postInvalidateOnAnimation();
    }

    public synchronized void clearBoxes() {
        items.clear();
        postInvalidateOnAnimation();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (BoxItem item : items) {
            if (item == null || item.rect == null) continue;

            Paint p = item.primary ? primaryBoxPaint : boxPaint;
            canvas.drawRect(item.rect, p);

            if (!item.text.isEmpty()) {
                float textWidth = textPaint.measureText(item.text);
                float textHeight = textPaint.getTextSize();

                float left = item.rect.left;
                float top = Math.max(0, item.rect.top - textHeight - 18f);
                float right = left + textWidth + 24f;
                float bottom = top + textHeight + 18f;

                canvas.drawRoundRect(new RectF(left, top, right, bottom), 12f, 12f, textBgPaint);
                canvas.drawText(item.text, left + 12f, bottom - 10f, textPaint);
            }
        }
    }
}