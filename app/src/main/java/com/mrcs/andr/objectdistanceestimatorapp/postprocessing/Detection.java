package com.mrcs.andr.objectdistanceestimatorapp.postprocessing;

public class Detection {
    public final float x, y, width, height;
    public final float score;
    public final int classId;

    /**
     * Constructor of Detection.
     * @param x x coordinate on the model input space
     * @param y y coordinate on the model input space
     * @param width width on the model input space
     * @param height   height on the model input space
     * @param score confidence score
     * @param classId class id
     */
    public Detection(float x, float y, float width, float height, float score, int classId) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.score = score;
        this.classId = classId;
    }
}