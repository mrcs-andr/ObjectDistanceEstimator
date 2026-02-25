package com.mrcs.andr.objectdistanceestimatorapp.tracking;

import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;

/**
 * A Detection enriched with a stable track ID assigned by ByteTrack.
 */
public class TrackedDetection extends Detection {

    /** Unique track identifier assigned by the tracker. */
    public final long trackId;

    /**
     * @param x       x coordinate on the model input space
     * @param y       y coordinate on the model input space
     * @param width   width on the model input space
     * @param height  height on the model input space
     * @param score   confidence score
     * @param classId class id
     * @param trackId stable track identifier from ByteTrack
     */
    public TrackedDetection(float x, float y, float width, float height,
                            float score, int classId, long trackId) {
        super(x, y, width, height, score, classId);
        this.trackId = trackId;
    }
}
