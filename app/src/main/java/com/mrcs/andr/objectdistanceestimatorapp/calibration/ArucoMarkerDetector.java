package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for detecting ArUco markers in a camera frame and estimating
 * the camera pose relative to the detected marker.
 *
 * <p>The marker is assumed to be placed flat on the ground. The marker's Z axis
 * points upward (toward the camera). The camera height and pitch are derived from
 * the rotation and translation vectors returned by {@link Calib3d#solvePnP}.</p>
 *
 * <p>Uses the {@code DICT_4X4_50} ArUco dictionary. Print any marker from that
 * dictionary and supply its physical side length when calling
 * {@link #detectAndEstimatePose}.</p>
 */
public class ArucoMarkerDetector {

    private final ArucoDetector arucoDetector;

    /**
     * Result of a single ArUco pose estimation.
     */
    public static class PoseResult {
        /** Height of the camera above the ground plane (metres). */
        public final double cameraHeight;
        /** Camera pitch in degrees (positive = tilted downward toward ground). */
        public final double cameraPitch;
        /** ID of the detected marker. */
        public final int markerId;

        public PoseResult(double cameraHeight, double cameraPitch, int markerId) {
            this.cameraHeight = cameraHeight;
            this.cameraPitch = cameraPitch;
            this.markerId = markerId;
        }
    }

    /**
     * Creates an {@code ArucoMarkerDetector} using the {@code DICT_4X4_50} dictionary
     * with default detector parameters.
     */
    public ArucoMarkerDetector() {
        Dictionary dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50);
        DetectorParameters parameters = new DetectorParameters();
        this.arucoDetector = new ArucoDetector(dictionary, parameters);
    }

    /**
     * Detects ArUco markers in the given frame and estimates the camera pose
     * from the first detected marker.
     *
     * @param frame        Input image (RGB or grayscale {@link Mat})
     * @param markerSizeM  Physical side length of the printed marker in metres
     * @param calibration  Camera intrinsics used for {@link Calib3d#solvePnP}
     * @return {@link PoseResult} if a marker was detected, {@code null} otherwise
     */
    public PoseResult detectAndEstimatePose(Mat frame, double markerSizeM,
                                             CalibrationResult calibration) {
        List<Mat> corners = new ArrayList<>();
        Mat ids = new Mat();
        List<Mat> rejected = new ArrayList<>();

        arucoDetector.detectMarkers(frame, corners, ids, rejected);

        if (ids.empty() || corners.isEmpty()) {
            ids.release();
            releaseAll(rejected);
            return null;
        }

        Mat firstCorners = corners.get(0);
        int markerId = (int) ids.get(0, 0)[0];

        // Build OpenCV camera matrix from intrinsics
        Mat cameraMatrix = new Mat(3, 3, CvType.CV_64F);
        cameraMatrix.put(0, 0,
                calibration.fx,             0, calibration.cx,
                            0, calibration.fy, calibration.cy,
                            0,             0,              1);

        Mat distCoeffs = new Mat(1, 5, CvType.CV_64F);
        distCoeffs.put(0, 0,
                calibration.k1, calibration.k2,
                calibration.p1, calibration.p2,
                calibration.k3);

        // 3D marker corners in marker coordinate system (Z = 0, flat on ground)
        // Order: top-left, top-right, bottom-right, bottom-left (ArUco convention)
        double half = markerSizeM / 2.0;
        MatOfPoint3f objectPoints = new MatOfPoint3f(
                new Point3(-half,  half, 0),
                new Point3( half,  half, 0),
                new Point3( half, -half, 0),
                new Point3(-half, -half, 0)
        );

        // Convert detected 2D corners (shape 1×4, CV_32FC2) to MatOfPoint2f
        MatOfPoint2f imagePoints = new MatOfPoint2f(
                new org.opencv.core.Point(firstCorners.get(0, 0)[0], firstCorners.get(0, 0)[1]),
                new org.opencv.core.Point(firstCorners.get(0, 1)[0], firstCorners.get(0, 1)[1]),
                new org.opencv.core.Point(firstCorners.get(0, 2)[0], firstCorners.get(0, 2)[1]),
                new org.opencv.core.Point(firstCorners.get(0, 3)[0], firstCorners.get(0, 3)[1])
        );

        Mat rvec = new Mat();
        Mat tvec = new Mat();
        Calib3d.solvePnP(objectPoints, imagePoints, cameraMatrix, distCoeffs, rvec, tvec);

        // Compute camera position in marker world coordinates: pos = -R^T * t
        Mat rotMat = new Mat();
        Calib3d.Rodrigues(rvec, rotMat);

        double tx = tvec.get(0, 0)[0];
        double ty = tvec.get(1, 0)[0];
        double tz = tvec.get(2, 0)[0];

        // Camera position = -R^T * t  (R transforms world → camera)
        double camZ = -(rotMat.get(0, 2)[0] * tx
                      + rotMat.get(1, 2)[0] * ty
                      + rotMat.get(2, 2)[0] * tz);

        // Camera forward direction in world = R^T * [0,0,1] = [R[2][0], R[2][1], R[2][2]]
        double fwdX = rotMat.get(2, 0)[0];
        double fwdY = rotMat.get(2, 1)[0];
        double fwdZ = rotMat.get(2, 2)[0];

        // Pitch = angle below horizontal (positive = looking down toward ground).
        // In the marker world (Z up), horizontal plane is XY.
        double pitchRad = Math.atan2(-fwdZ, Math.sqrt(fwdX * fwdX + fwdY * fwdY));
        double cameraPitch = Math.toDegrees(pitchRad);

        // Release resources
        cameraMatrix.release();
        distCoeffs.release();
        objectPoints.release();
        imagePoints.release();
        rvec.release();
        tvec.release();
        rotMat.release();
        ids.release();
        releaseAll(corners);
        releaseAll(rejected);

        return new PoseResult(camZ, cameraPitch, markerId);
    }

    private static void releaseAll(List<Mat> mats) {
        for (Mat m : mats) {
            if (m != null) m.release();
        }
    }
}
