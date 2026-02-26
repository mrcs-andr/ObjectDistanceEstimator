package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
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
 * the full 6-DOF camera pose (position x,y,z and orientation yaw,pitch,roll)
 * relative to a user-specified world frame.
 *
 * <p>The marker is assumed to be placed flat on the ground (Z = 0 in its local frame,
 * Z axis pointing upward). The user may specify the marker's world-frame position
 * (markerWorldX, markerWorldY, markerWorldZ) and horizontal rotation (markerWorldYaw)
 * so the returned camera pose is expressed in global world coordinates rather than
 * the marker-relative frame.</p>
 *
 * <p>Coordinate convention used throughout: Z-up world frame (X right, Y forward,
 * Z up). Euler angles follow ZYX convention (yaw → pitch → roll).</p>
 *
 * <p>Uses the {@code DICT_4X4_50} ArUco dictionary. Print any marker from that
 * dictionary and supply its physical side length when calling
 * {@link #detectAndEstimatePose}.</p>
 */
public class ArucoMarkerDetector {

    private final ArucoDetector arucoDetector;

    /**
     * Full 6-DOF camera pose result from a single ArUco detection.
     */
    public static class PoseResult {
        /** Camera X position in world (metres, horizontal right). */
        public final double cameraX;
        /** Camera Y position in world (metres, horizontal forward). */
        public final double cameraY;
        /** Camera Z position in world (metres, height above ground). */
        public final double cameraZ;
        /** Camera yaw angle in degrees (rotation around world Z axis). */
        public final double cameraYaw;
        /** Camera pitch angle in degrees (positive = tilted downward toward ground). */
        public final double cameraPitch;
        /** Camera roll angle in degrees (rotation around camera forward axis). */
        public final double cameraRoll;
        /** ID of the detected marker. */
        public final int markerId;

        public PoseResult(double cameraX, double cameraY, double cameraZ,
                          double cameraYaw, double cameraPitch, double cameraRoll,
                          int markerId) {
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
            this.cameraYaw = cameraYaw;
            this.cameraPitch = cameraPitch;
            this.cameraRoll = cameraRoll;
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
     * Detects ArUco markers in the given frame and estimates the full 6-DOF camera
     * pose from the first detected marker.
     *
     * <p>The marker is assumed to be flat on the ground. The user provides the
     * marker's centre position in world coordinates and its yaw rotation so the
     * returned pose is expressed in the global world frame.</p>
     *
     * @param frame           Input image (RGB or grayscale {@link Mat})
     * @param markerSizeM     Physical side length of the printed marker in metres
     * @param markerWorldX    Marker centre X in world coordinates (metres)
     * @param markerWorldY    Marker centre Y in world coordinates (metres)
     * @param markerWorldZ    Marker centre Z in world coordinates (metres, 0 = on ground)
     * @param markerWorldYaw  Marker yaw rotation in world frame (degrees, rotation around Z)
     * @param calibration     Camera intrinsics used for {@link Calib3d#solvePnP}
     * @return {@link PoseResult} with full 6-DOF if a marker was detected, {@code null} otherwise
     */
    public PoseResult detectAndEstimatePose(Mat frame, double markerSizeM,
                                             double markerWorldX, double markerWorldY,
                                             double markerWorldZ, double markerWorldYaw,
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

        MatOfDouble distCoeffs = new MatOfDouble(
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

        // Rotation matrix Rm (world-to-camera, in marker frame)
        Mat rotMat = new Mat();
        Calib3d.Rodrigues(rvec, rotMat);

        double tx = tvec.get(0, 0)[0];
        double ty = tvec.get(1, 0)[0];
        double tz = tvec.get(2, 0)[0];

        // --- Camera position in marker frame: Cm = -Rm^T * t ---
        // (Rm^T * t).i = sum_j Rm[j][i] * t[j]
        double camX_m = -(r(rotMat,0,0)*tx + r(rotMat,1,0)*ty + r(rotMat,2,0)*tz);
        double camY_m = -(r(rotMat,0,1)*tx + r(rotMat,1,1)*ty + r(rotMat,2,1)*tz);
        double camZ_m = -(r(rotMat,0,2)*tx + r(rotMat,1,2)*ty + r(rotMat,2,2)*tz);

        // --- Transform to global world frame using marker pose (Rz(mYaw) + translation) ---
        // Rz(mYaw) = [[c, -s, 0], [s, c, 0], [0, 0, 1]]
        double mYaw = Math.toRadians(markerWorldYaw);
        double c = Math.cos(mYaw);
        double s = Math.sin(mYaw);

        double camX_w = c * camX_m - s * camY_m + markerWorldX;
        double camY_w = s * camX_m + c * camY_m + markerWorldY;
        double camZ_w = camZ_m + markerWorldZ;

        // --- Camera-to-world rotation in global frame: W = Rz(mYaw) * Rm^T ---
        // W[row][col]:
        //   row 0: c * Rm^T[0][col] - s * Rm^T[1][col] = c * Rm[col][0] - s * Rm[col][1]
        //   row 1: s * Rm^T[0][col] + c * Rm^T[1][col] = s * Rm[col][0] + c * Rm[col][1]
        //   row 2: Rm^T[2][col] = Rm[col][2]
        //
        // For col = 0: used for yaw / pitch
        double w00 = c * r(rotMat,0,0) - s * r(rotMat,0,1);
        double w10 = s * r(rotMat,0,0) + c * r(rotMat,0,1);
        double w20 = r(rotMat,0,2);
        // For col = 1: used for roll
        double w21 = r(rotMat,1,2);
        // For col = 2: used for roll
        double w22 = r(rotMat,2,2);

        // --- Extract ZYX Euler angles from W (R_c2w in global frame) ---
        // W = Rz(yaw) * Ry(pitch) * Rx(roll)
        // pitch = asin(-W[2][0]),  yaw = atan2(W[1][0], W[0][0]),  roll = atan2(W[2][1], W[2][2])
        double cameraPitch = Math.toDegrees(Math.asin(-clamp(w20, -1.0, 1.0)));
        double cameraYaw   = Math.toDegrees(Math.atan2(w10, w00));
        double cameraRoll  = Math.toDegrees(Math.atan2(w21, w22));

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

        return new PoseResult(camX_w, camY_w, camZ_w,
                cameraYaw, cameraPitch, cameraRoll, markerId);
    }

    /** Convenience accessor: rotMat.get(row, col)[0] */
    private static double r(Mat rotMat, int row, int col) {
        return rotMat.get(row, col)[0];
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void releaseAll(List<Mat> mats) {
        for (Mat m : mats) {
            if (m != null) m.release();
        }
    }
}
