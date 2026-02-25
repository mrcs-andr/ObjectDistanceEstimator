package com.mrcs.andr.objectdistanceestimatorapp.tracking;

import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;

import java.util.List;

/**
 * Interface for ByteTrack multi-object tracker.
 *
 * <p>Implementations wrap the bytetrack-sdk-android library and convert YOLO
 * {@link Detection} objects into {@link TrackedDetection} objects that carry a
 * stable {@link TrackedDetection#trackId} across frames.</p>
 *
 * <p>Usage in the detection pipeline:
 * <pre>
 *   image -&gt; pre-processing -&gt; yolo inference -&gt; post-processing -&gt; ByteTrack
 * </pre>
 * </p>
 */
public interface IByteTracker {

    /**
     * Update the tracker with the latest YOLO detections and return the
     * tracked objects for the current frame.
     *
     * @param detections raw detections produced by the YOLO post-processor for
     *                   one frame (may be empty but must not be {@code null})
     * @return list of {@link TrackedDetection} instances, each carrying a stable
     *         {@link TrackedDetection#trackId}; never {@code null}
     */
    List<Detection> update(List<Detection> detections);
}
