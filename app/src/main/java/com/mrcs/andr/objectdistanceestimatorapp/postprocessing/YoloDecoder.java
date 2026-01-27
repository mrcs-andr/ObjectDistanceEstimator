package com.mrcs.andr.objectdistanceestimatorapp.postprocessing;

import android.util.Log;

import com.mrcs.andr.objectdistanceestimatorapp.ui.DetectionOverlayView;

import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect2d;
import org.opencv.core.Rect2d;
import org.opencv.dnn.Dnn;

import java.util.ArrayList;
import java.util.List;


public final class YoloDecoder {

    private final int outC;
    private final int outN;
    private final float confThresh;
    private final float nmsIoU;

    /**
     * Constructor of the decode class.
     * @param outC first dimension of the output tensor, example (4 + numClasses)
     * @param outN second dimension of the output tensor, total of bounding boxes: example (5376)
     * @param confThresh threshold to filter boxes by confidence score
     * @param nmsIoU IoU threshold for Non-Maximum Suppression
     */
    public YoloDecoder(int outC, int outN, float confThresh, float nmsIoU) {
        this.outC = outC;
        this.outN = outN;
        this.confThresh = confThresh;
        this.nmsIoU = nmsIoU;
    }

    /**
     * Decode the model output to a list of detections.
     * @param out model output as float array
     * @param modelSpaceSize size of the model input space (e.g., 512 for 512x512)
     * @return list of detections after decoding and NMS
     */
    public List<Detection> decode(float[] out, int modelSpaceSize) {
        int expected = outC * outN; // outC = 12, outN = 5376
        if (out == null || out.length != expected) {
            throw new IllegalArgumentException("Invalid output: expected float[" + expected + "]");
        }

        List<Detection> detections = new ArrayList<>();

        int numClasses = outC - 4;          // 8
        for (int i = 0; i < outN; i++) {
            // best class
            float maxClassScore = -Float.MAX_VALUE;
            int classId = -1;
            for (int c = 0; c < numClasses; c++) {
                float score = out[(4 + c) * outN + i];
                if (score > maxClassScore) {
                    maxClassScore = score;
                    classId = c;
                }
            }
            if (maxClassScore < confThresh) continue;

            float cx = out[0 * outN + i];
            float cy = out[1 * outN + i];
            float bw = out[2 * outN + i];
            float bh = out[3 * outN + i];

            float x = (cx - bw / 2f) * modelSpaceSize;
            float y = (cy - bh / 2f) * modelSpaceSize;
            float w = bw * modelSpaceSize;
            float h = bh * modelSpaceSize;

            detections.add(new Detection(x, y, w, h, maxClassScore, classId));
        }

        return applyNms(detections);
    }

    public  List<Detection> applyNms(List<Detection> detections) {
        List<Rect2d> rectList = new ArrayList<>();
        List<Float> scoreList = new ArrayList<>();
        List<Integer> indexMap = new ArrayList<>(); // map to original indices

        // Filter by scoreThreshold first
        for (int i = 0; i < detections.size(); i++) {
            Detection d = detections.get(i);
            int x = Math.round(d.x);
            int y = Math.round(d.y);
            int w = Math.round(d.width);
            int h = Math.round(d.height);
            rectList.add(new Rect2d(x, y, w, h));
            scoreList.add(d.score);
            indexMap.add(i);
        }

        if (rectList.isEmpty()) {
            return new ArrayList<>();
        }

        MatOfRect2d boxes = new MatOfRect2d();
        boxes.fromList(rectList);

        MatOfFloat scores = new MatOfFloat();
        scores.fromArray(toFloatArray(scoreList));

        MatOfInt indices = new MatOfInt();
        Dnn.NMSBoxes(boxes, scores, this.confThresh, this.nmsIoU, indices);

        int[] idxs = indices.toArray();
        List<Detection> kept = new ArrayList<>(idxs.length);
        for (int idx : idxs) {
            int originalIndex = indexMap.get(idx);
            kept.add(detections.get(originalIndex));
        }

        return kept;
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

}
