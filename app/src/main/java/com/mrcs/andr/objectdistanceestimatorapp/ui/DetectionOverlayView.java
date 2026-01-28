// java
package com.mrcs.andr.objectdistanceestimatorapp.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.view.PreviewView;

import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.KittiLabels;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.LetterBoxParams;

import java.util.ArrayList;
import java.util.List;

public class DetectionOverlayView extends View {

    private final List<Detection> detections = new ArrayList<>();
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final Matrix previewMatrix = new Matrix();
    private int analysisWidth;     // Size of the image before preprocessing
    private int analysisHeight;    // Size of the image before preprocessing
    private float lbScale;  //Scale factor used in letterboxing
    private float lbPadX; //Letterbox x padding.
    private float lbPadY; //Letterbox y padding.

    public DetectionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DetectionOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);

        bgPaint.setColor(Color.argb(160, 0, 0, 0));
    }

    public void setLetterbox(LetterBoxParams params) {
        this.lbScale = params.scale;
        this.lbPadX = params.padX;
        this.lbPadY = params.padY;
        this.analysisWidth = params.analysisImageWidth;
        this.analysisHeight = params.analysisImageHeight;
    }

    public void setDetections(List<Detection> newDetections) {
        detections.clear();
        if (newDetections != null) detections.addAll(newDetections);
        postInvalidateOnAnimation();
    }

    public void setFromPreviewView(@NonNull PreviewView previewView) {
        previewView.post(() -> {
            int vw = previewView.getWidth();
            int vh = previewView.getHeight();
            if (vw <= 0 || vh <= 0 || analysisWidth <= 0 || analysisHeight <= 0) return;

            RectF src = new RectF(0f, 0f, analysisWidth, analysisHeight);
            RectF dst = new RectF(0f, 0f, vw, vh);

            float sx = dst.width() / src.width();
            float sy = dst.height() / src.height();

            float s;
            switch (previewView.getScaleType()) {
                case FIT_CENTER:
                case FIT_START:
                case FIT_END:
                    s = Math.min(sx, sy); // fit
                    break;
                default: // FILL_CENTER, FILL_START, FILL_END
                    s = Math.max(sx, sy); // fill (center-crop)
                    break;
            }

            float dx, dy;
            switch (previewView.getScaleType()) {
                case FIT_START:
                case FILL_START:
                    dx = dst.left;
                    dy = dst.top;
                    break;
                case FIT_END:
                case FILL_END:
                    dx = dst.left + (dst.width()  - src.width()  * s);
                    dy = dst.top  + (dst.height() - src.height() * s);
                    break;
                default: // center variants
                    dx = dst.left + (dst.width()  - src.width()  * s) / 2f;
                    dy = dst.top  + (dst.height() - src.height() * s) / 2f;
                    break;
            }

            Matrix analysisToView = new Matrix();
            analysisToView.setScale(s, s);
            analysisToView.postTranslate(dx, dy);

            synchronized (previewMatrix) {
                previewMatrix.set(analysisToView);
            }
            postInvalidateOnAnimation();
        });
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0 || detections.isEmpty()) return;
        if (analysisWidth <= 0 || analysisHeight <= 0) return;
        if (lbScale <= 0f) return;

        RectF analysisBounds = new RectF(0, 0, analysisWidth, analysisHeight);
        RectF viewBounds = new RectF(0, 0, getWidth(), getHeight());

        for (Detection d : detections) {
            float mx1 = d.x;
            float my1 = d.y;
            float mx2 = d.x + d.width;
            float my2 = d.y + d.height;

            float ax1 = (mx1 - lbPadX) / lbScale;
            float ay1 = (my1 - lbPadY) / lbScale;
            float ax2 = (mx2 - lbPadX) / lbScale;
            float ay2 = (my2 - lbPadY) / lbScale;

            if (Float.isNaN(ax1) || Float.isNaN(ay1) || Float.isNaN(ax2) || Float.isNaN(ay2)) continue;
            if (ax1 > ax2 || ay1 > ay2) continue;

            RectF inAnalysis = new RectF(
                    clamp(ax1, 0, analysisWidth),
                    clamp(ay1, 0, analysisHeight),
                    clamp(ax2, 0, analysisWidth),
                    clamp(ay2, 0, analysisHeight)
            );

            if (!RectF.intersects(inAnalysis, analysisBounds)) continue;

            RectF mapped = new RectF();
            previewMatrix.mapRect(mapped, inAnalysis);

            if (!RectF.intersects(mapped, viewBounds)) continue;

            canvas.drawRect(mapped, boxPaint);

            String label = safeLabel(d);
            float textWidth = textPaint.measureText(label);
            float textHeight = textPaint.getTextSize();

            float labelLeft = mapped.left;
            float labelTop = Math.max(textHeight, mapped.top);
            canvas.drawRect(labelLeft, labelTop - textHeight, labelLeft + textWidth, labelTop, bgPaint);
            canvas.drawText(label, labelLeft, labelTop - 4f, textPaint);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(v, max));
    }

    private static String safeLabel(Detection d) {
        String name = (d.classId >= 0 && d.classId < KittiLabels.NAMES.length)
                ? KittiLabels.NAMES[d.classId] : "Unknown";
        return String.format("%s : %.2f", name, d.score);
    }
}