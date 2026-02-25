package com.mrcs.andr.objectdistanceestimatorapp.tracking;

import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.video.KalmanFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ByteTrack multi-object tracker.
 *
 * <p>Implements the two-stage association strategy from the ByteTrack paper:
 * high-confidence detections are matched first against active tracks; remaining
 * tracks are then matched against low-confidence detections. An OpenCV Kalman
 * filter predicts each track's position between frames for robust association
 * even when detections are momentarily missed.</p>
 *
 * <p>Each matched detection is returned as a {@link TrackedDetection} carrying
 * a stable {@link TrackedDetection#trackId} that persists across frames for
 * the lifetime of the track.</p>
 */
public class ByteTracker implements IObjectTracker {

    /** Default number of frames a track survives without any association. */
    private static final int DEFAULT_MAX_LOST_FRAMES = 30;

    private final float highThresh;
    private final float lowThresh;
    private final float iouThreshold;
    private final int maxLostFrames;

    private final List<STrack> activeTracks = new ArrayList<>();
    private long nextTrackId = 1;

    /**
     * Creates a ByteTracker with default thresholds:
     * high-confidence = 0.5, low-confidence = 0.1, IoU = 0.3, max lost frames = 30.
     */
    public ByteTracker() {
        this(0.5f, 0.1f, 0.3f, DEFAULT_MAX_LOST_FRAMES);
    }

    /**
     * @param highThresh    minimum score for a detection to be used in first-stage association
     * @param lowThresh     minimum score for a detection to be used in second-stage association
     * @param iouThreshold  minimum IoU required to accept a detection–track match
     * @param maxLostFrames number of consecutive frames a track is retained after losing association
     */
    public ByteTracker(float highThresh, float lowThresh, float iouThreshold, int maxLostFrames) {
        this.highThresh = highThresh;
        this.lowThresh = lowThresh;
        this.iouThreshold = iouThreshold;
        this.maxLostFrames = maxLostFrames;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Runs the two-stage ByteTrack association for one frame and returns the
     * currently active tracks as {@link TrackedDetection} instances.</p>
     */
    @Override
    public List<Detection> update(List<Detection> detections) {
        // 1. Partition detections by confidence
        List<Detection> highConf = new ArrayList<>();
        List<Detection> lowConf = new ArrayList<>();
        for (Detection d : detections) {
            if (d.score >= highThresh) {
                highConf.add(d);
            } else if (d.score >= lowThresh) {
                lowConf.add(d);
            }
        }

        // 2. Kalman-predict all active track positions
        for (STrack t : activeTracks) {
            t.predict();
        }

        // 3. Stage 1: match active tracks ↔ high-confidence detections
        boolean[] activeTrackMatched = new boolean[activeTracks.size()];
        boolean[] highDetMatched = new boolean[highConf.size()];
        int[] activeAssignment = assignDetections(activeTracks, highConf,
                activeTrackMatched, highDetMatched);

        for (int i = 0; i < highConf.size(); i++) {
            if (highDetMatched[i]) {
                activeTracks.get(activeAssignment[i]).update(highConf.get(i));
            }
        }

        // 4. Stage 2: unmatched active tracks ↔ low-confidence detections
        List<STrack> unmatchedActive = new ArrayList<>();
        List<Integer> unmatchedActiveIdx = new ArrayList<>();
        for (int j = 0; j < activeTracks.size(); j++) {
            if (!activeTrackMatched[j]) {
                unmatchedActive.add(activeTracks.get(j));
                unmatchedActiveIdx.add(j);
            }
        }

        boolean[] unmatchedActiveMatched = new boolean[unmatchedActive.size()];
        boolean[] lowDetMatched = new boolean[lowConf.size()];
        int[] unmatchedAssignment = assignDetections(unmatchedActive, lowConf,
                unmatchedActiveMatched, lowDetMatched);

        for (int i = 0; i < lowConf.size(); i++) {
            if (lowDetMatched[i]) {
                unmatchedActive.get(unmatchedAssignment[i]).update(lowConf.get(i));
            }
        }

        // 5. Increment lostFrames for tracks that were not matched in either stage
        for (int k = 0; k < unmatchedActive.size(); k++) {
            if (!unmatchedActiveMatched[k]) {
                unmatchedActive.get(k).lostFrames++;
            }
        }

        // 6. Remove tracks that have been lost too long
        activeTracks.removeIf(t -> t.lostFrames > maxLostFrames);

        // 7. Initialize new tracks from unmatched high-confidence detections
        for (int i = 0; i < highConf.size(); i++) {
            if (!highDetMatched[i]) {
                activeTracks.add(new STrack(nextTrackId++, highConf.get(i)));
            }
        }

        // 8. Build result: return only tracks matched in this frame
        List<Detection> result = new ArrayList<>();
        for (STrack t : activeTracks) {
            if (t.lostFrames == 0) {
                Detection d = t.predicted;
                result.add(new TrackedDetection(
                        d.x, d.y, d.width, d.height, d.score, d.classId, t.trackId));
            }
        }
        return result;
    }

    /**
     * Greedily assigns detections to tracks by IoU (highest IoU first per detection).
     *
     * @param tracks       candidate tracks
     * @param dets         candidate detections
     * @param trackMatched output array (size = tracks.size()), set to {@code true} when matched
     * @param detMatched   output array (size = dets.size()),   set to {@code true} when matched
     * @return assignment[detIdx] = trackIdx that was matched, or -1 if unmatched
     */
    private int[] assignDetections(List<STrack> tracks, List<Detection> dets,
                                   boolean[] trackMatched, boolean[] detMatched) {
        int[] assignment = new int[dets.size()];
        Arrays.fill(assignment, -1);
        if (tracks.isEmpty() || dets.isEmpty()) return assignment;

        for (int i = 0; i < dets.size(); i++) {
            float bestIou = iouThreshold;
            int bestJ = -1;
            for (int j = 0; j < tracks.size(); j++) {
                if (trackMatched[j]) continue;
                float iou = computeIoU(dets.get(i), tracks.get(j).predicted);
                if (iou > bestIou) {
                    bestIou = iou;
                    bestJ = j;
                }
            }
            if (bestJ >= 0) {
                assignment[i] = bestJ;
                detMatched[i] = true;
                trackMatched[bestJ] = true;
            }
        }
        return assignment;
    }

    /**
     * Computes Intersection-over-Union between two detections in model-space coordinates.
     */
    private static float computeIoU(Detection a, Detection b) {
        float ax2 = a.x + a.width;
        float ay2 = a.y + a.height;
        float bx2 = b.x + b.width;
        float by2 = b.y + b.height;

        float ix1 = Math.max(a.x, b.x);
        float iy1 = Math.max(a.y, b.y);
        float ix2 = Math.min(ax2, bx2);
        float iy2 = Math.min(ay2, by2);

        if (ix2 <= ix1 || iy2 <= iy1) return 0f;

        float inter = (ix2 - ix1) * (iy2 - iy1);
        float aArea = a.width * a.height;
        float bArea = b.width * b.height;
        return inter / (aArea + bArea - inter);
    }

    /**
     * Internal representation of a single tracked object.
     *
     * <p>A constant-velocity Kalman filter (state = [x, y, w, h, vx, vy, vw, vh],
     * measurement = [x, y, w, h]) is used to predict the bounding-box position
     * between frames.</p>
     */
    private static class STrack {

        final long trackId;
        int lostFrames = 0;

        /** Current predicted (or last-updated) detection in model space. */
        Detection predicted;

        private final KalmanFilter kf;

        STrack(long trackId, Detection det) {
            this.trackId = trackId;
            this.predicted = det;
            this.kf = initKalman(det);
        }

        /** Advance the Kalman filter one time-step and update {@link #predicted}. */
        void predict() {
            Mat statePre = kf.predict();
            float px = (float) statePre.get(0, 0)[0];
            float py = (float) statePre.get(1, 0)[0];
            float pw = (float) statePre.get(2, 0)[0];
            float ph = (float) statePre.get(3, 0)[0];
            predicted = new Detection(px, py, Math.max(1f, pw), Math.max(1f, ph),
                    predicted.score, predicted.classId);
        }

        /** Correct the Kalman filter with a matched detection and reset {@link #lostFrames}. */
        void update(Detection det) {
            Mat measurement = new Mat(4, 1, CvType.CV_32F);
            measurement.put(0, 0, det.x, det.y, det.width, det.height);
            kf.correct(measurement);
            predicted = det;
            lostFrames = 0;
        }

        private static KalmanFilter initKalman(Detection det) {
            // 8 dynamic params (x, y, w, h, vx, vy, vw, vh), 4 measurement params (x, y, w, h)
            KalmanFilter kf = new KalmanFilter(8, 4, 0, CvType.CV_32F);

            // Transition matrix: constant-velocity model
            kf.transitionMatrix.put(0, 0,
                    1, 0, 0, 0, 1, 0, 0, 0,
                    0, 1, 0, 0, 0, 1, 0, 0,
                    0, 0, 1, 0, 0, 0, 1, 0,
                    0, 0, 0, 1, 0, 0, 0, 1,
                    0, 0, 0, 0, 1, 0, 0, 0,
                    0, 0, 0, 0, 0, 1, 0, 0,
                    0, 0, 0, 0, 0, 0, 1, 0,
                    0, 0, 0, 0, 0, 0, 0, 1);

            // Measurement matrix: observe position and size only
            kf.measurementMatrix.put(0, 0,
                    1, 0, 0, 0, 0, 0, 0, 0,
                    0, 1, 0, 0, 0, 0, 0, 0,
                    0, 0, 1, 0, 0, 0, 0, 0,
                    0, 0, 0, 1, 0, 0, 0, 0);

            Core.setIdentity(kf.processNoiseCov, new Scalar(1e-2));
            Core.setIdentity(kf.measurementNoiseCov, new Scalar(1e-1));
            Core.setIdentity(kf.errorCovPost, new Scalar(1.0));

            // Initial state: zero velocity
            kf.statePost.put(0, 0,
                    det.x, det.y, det.width, det.height, 0, 0, 0, 0);

            return kf;
        }
    }
}
