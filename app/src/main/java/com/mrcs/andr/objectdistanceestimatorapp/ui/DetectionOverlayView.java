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
import androidx.annotation.OptIn;
import androidx.camera.view.PreviewView;
import androidx.camera.view.TransformExperimental;
import androidx.camera.view.transform.OutputTransform;

import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.KittiLabels;

import java.util.ArrayList;
import java.util.List;

public class DetectionOverlayView extends View {

    private final List<Detection> detections = new ArrayList<>();
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final Matrix previewMatrix = new Matrix();
    private int analysisWidth = 480;
    private int analysisHeight = 640;
    private float lbScale = 1f;
    private float lbPadX = 0f;
    private float lbPadY = 0f;

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

    public void setDetections(List<Detection> newDetections) {
        detections.clear();
        if (newDetections != null) detections.addAll(newDetections);
        postInvalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0 || detections.isEmpty()) return;

        for (Detection d : detections) {
            float mx1 = d.x;
            float my1 = d.y;
            float mx2 = d.x + d.width;
            float my2 = d.y + d.height;

            float ax1 = (mx1 - lbPadX) / lbScale;
            float ay1 = (my1 - lbPadY) / lbScale;
            float ax2 = (mx2 - lbPadX) / lbScale;
            float ay2 = (my2 - lbPadY) / lbScale;

            RectF inAnalysis = new RectF(ax1, ay1, ax2, ay2);

            RectF analysisBounds = new RectF(0, 0, analysisWidth, analysisHeight);
            if (!inAnalysis.intersect(analysisBounds)) {
                continue;
            }

            RectF norm = new RectF(
                    inAnalysis.left / analysisWidth,
                    inAnalysis.top / analysisHeight,
                    inAnalysis.right / analysisWidth,
                    inAnalysis.bottom / analysisHeight
            );

            // 4) Converter para retângulo base da Overlay
            RectF base = new RectF(
                    norm.left * getWidth(),
                    norm.top * getHeight(),
                    norm.right * getWidth(),
                    norm.bottom * getHeight()
            );

            RectF mapped = new RectF();
            previewMatrix.mapRect(mapped, base);

            RectF viewBounds = new RectF(0, 0, getWidth(), getHeight());
            if (!mapped.intersect(viewBounds)) {
                continue;
            }

            canvas.drawRect(mapped, boxPaint);

            String label = String.format("%s : %.2f",
                    KittiLabels.NAMES[d.classId], d.score);
            float textWidth = textPaint.measureText(label);
            float textHeight = textPaint.getTextSize();

            float labelLeft = mapped.left;
            float labelTop = Math.max(textHeight, mapped.top);
            canvas.drawRect(labelLeft, labelTop - textHeight, labelLeft + textWidth, labelTop, bgPaint);
            canvas.drawText(label, labelLeft, labelTop - 4f, textPaint);
        }
    }
}