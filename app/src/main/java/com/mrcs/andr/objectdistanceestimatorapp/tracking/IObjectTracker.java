package com.mrcs.andr.objectdistanceestimatorapp.tracking;

import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;

import java.util.List;

/**
 * Generic interface for multi-object tracker implementations.
 *
 * <p>Each call to {@link #update} processes one video frame: raw YOLO
 * {@link Detection} objects go in, and {@link TrackedDetection} objects
 * (each carrying a stable {@link TrackedDetection#trackId}) come out.</p>
 *
 * <p>Pipeline position:
 * <pre>
 *   image -&gt; pre-processing -&gt; yolo inference -&gt; post-processing -&gt; tracker
 * </pre>
 * </p>
 */
public interface IObjectTracker {

    /**
     * Update the tracker with the latest detections for one frame and return
     * the tracked objects.
     *
     * @param detections raw detections produced by the YOLO post-processor
     *                   (may be empty but must not be {@code null})
     * @return list of {@link TrackedDetection} instances, each carrying a
     *         stable {@link TrackedDetection#trackId}; never {@code null}
     */
    List<Detection> update(List<Detection> detections);
}
