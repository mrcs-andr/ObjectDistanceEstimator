package com.mrcs.andr.objectdistanceestimatorapp.calibration;


import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
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
 * <p>The user may specify the marker's world-frame position
 * (markerWorldX, markerWorldY, markerWorldZ) and full orientation
 * (markerWorldYaw, markerWorldPitch, markerWorldRoll) so the returned camera
 * pose is expressed in global world coordinates rather than the marker-relative
 * frame.</p>
 *
 * <p>Coordinate convention used throughout: Z-up world frame (X right, Y forward,
 * Z up). Euler angles follow ZYX convention (yaw → pitch → roll).</p>
 *
 * <p>Uses the {@code DICT_4X4_50} ArUco dictionary. Print any marker from that
 * dictionary and supply its physical side length when calling
 * {@link #detectAndEstimatePose}.</p>
 */
public class ArucoMarkerDetector {

    /** Colour used to draw the detected marker border (green in BGR). */
    private static final Scalar MARKER_OUTLINE_COLOR = new Scalar(0, 255, 0);
    /** Stroke width (px) used when drawing the coordinate axes. */
    private static final int AXES_LINE_THICKNESS = 3;

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
        /**
         * Annotated camera frame with detected marker outline and coordinate axes drawn.
         * Suitable for display in an overlay {@link android.widget.ImageView}.
         */
        public final Bitmap overlayBitmap;

        public PoseResult(double cameraX, double cameraY, double cameraZ,
                          double cameraYaw, double cameraPitch, double cameraRoll,
                          int markerId, Bitmap overlayBitmap) {
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
            this.cameraYaw = cameraYaw;
            this.cameraPitch = cameraPitch;
            this.cameraRoll = cameraRoll;
            this.markerId = markerId;
            this.overlayBitmap = overlayBitmap;
        }
    }

    private DetectorParameters createDefaultDetectorParameters() {
        DetectorParameters params = new DetectorParameters();

        /*
        params.set_adaptiveThreshWinSizeMin(5);
        params.set_adaptiveThreshWinSizeMax(35);
        params.set_adaptiveThreshWinSizeStep(10);
        params.set_adaptiveThreshConstant(7);

        params.set_minMarkerPerimeterRate(0.02);  // raise if you only expect large markers
        params.set_maxMarkerPerimeterRate(4.0);

        params.set_polygonalApproxAccuracyRate(0.03); // 0.03..0.05
        params.set_minCornerDistanceRate(0.05);
        params.set_minDistanceToBorder(3);

        params.set_minOtsuStdDev(5.0);
        params.set_perspectiveRemovePixelPerCell(8);  // 4..10
        params.set_perspectiveRemoveIgnoredMarginPerCell(0.13f);

        params.set_maxErroneousBitsInBorderRate(0.35f);
        params.set_errorCorrectionRate(0.6f);
        */

        params.set_cornerRefinementMethod(1); // 0 = none, 1 = subpix, 2 = contour
        params.set_cornerRefinementWinSize(5);           // try 3..7
        params.set_cornerRefinementMaxIterations(30);
        params.set_cornerRefinementMinAccuracy(0.1);
        return params;
    }

    /**
     * Creates an {@code ArucoMarkerDetector} using the {@code DICT_4X4_50} dictionary
     * with default detector parameters.
     */
    public ArucoMarkerDetector() {
        Dictionary dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_250);
        DetectorParameters parameters = createDefaultDetectorParameters();
        this.arucoDetector = new ArucoDetector(dictionary, parameters);
    }

    /**
     * Detects ArUco markers in the given frame and estimates the full 6-DOF camera
     * pose from the first detected marker.
     *
     * <p>The marker may be placed at any orientation in the world. The user provides the
     * marker's centre position in world coordinates and its full orientation (yaw, pitch,
     * roll) so the returned pose is expressed in the global world frame.</p>
     *
     * @param frame             Input image (RGB or grayscale {@link Mat})
     * @param markerSizeM       Physical side length of the printed marker in metres
     * @param markerWorldX      Marker centre X in world coordinates (metres)
     * @param markerWorldY      Marker centre Y in world coordinates (metres)
     * @param markerWorldZ      Marker centre Z in world coordinates (metres, 0 = on ground)
     * @param markerWorldYaw    Marker yaw rotation in world frame (degrees, rotation around Z)
     * @param markerWorldPitch  Marker pitch rotation in world frame (degrees, rotation around Y)
     * @param markerWorldRoll   Marker roll rotation in world frame (degrees, rotation around X)
     * @param calibration       Camera intrinsics used for {@link Calib3d#solvePnP}
     * @return {@link PoseResult} with full 6-DOF if a marker was detected, {@code null} otherwise
     */
    public PoseResult detectAndEstimatePose(Mat frame, double markerSizeM,
                                             double markerWorldX, double markerWorldY,
                                             double markerWorldZ, double markerWorldYaw,
                                             double markerWorldPitch, double markerWorldRoll,
                                             CalibrationResult calibration) {
        List<Mat> corners = new ArrayList<>();
        Mat ids = new Mat();
        List<Mat> rejected = new ArrayList<>();

        Mat grayFrame = new Mat();
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGRA2BGR); // ensure 3-channel input
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY); // ensure 3-channel input

        arucoDetector.detectMarkers(grayFrame, corners, ids, rejected);

        if (ids.empty() || corners.isEmpty()) {
            ids.release();
            releaseAll(rejected);
            grayFrame.release();
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

        // --- Transform to global world frame using full marker orientation ---
        // R_marker = Rz(yaw) * Ry(pitch) * Rx(roll)
        double mYaw   = Math.toRadians(markerWorldYaw);
        double mPitch = Math.toRadians(markerWorldPitch);
        double mRoll  = Math.toRadians(markerWorldRoll);
        double cy = Math.cos(mYaw),   sy = Math.sin(mYaw);
        double cp = Math.cos(mPitch), sp = Math.sin(mPitch);
        double cr = Math.cos(mRoll),  sr = Math.sin(mRoll);

        // R_marker rows:
        //   [cy*cp,  cy*sp*sr - sy*cr,  cy*sp*cr + sy*sr]
        //   [sy*cp,  sy*sp*sr + cy*cr,  sy*sp*cr - cy*sr]
        //   [-sp,    cp*sr,             cp*cr            ]
        double rm00 = cy*cp,  rm01 = cy*sp*sr - sy*cr,  rm02 = cy*sp*cr + sy*sr;
        double rm10 = sy*cp,  rm11 = sy*sp*sr + cy*cr,  rm12 = sy*sp*cr - cy*sr;
        double rm20 = -sp,    rm21 = cp*sr,              rm22 = cp*cr;

        double camX_w = rm00*camX_m + rm01*camY_m + rm02*camZ_m + markerWorldX;
        double camY_w = rm10*camX_m + rm11*camY_m + rm12*camZ_m + markerWorldY;
        double camZ_w = rm20*camX_m + rm21*camY_m + rm22*camZ_m + markerWorldZ;

        // --- Camera-to-world rotation in global frame: W = R_marker * Rm^T ---
        // W[row][col] = sum_k R_marker[row][k] * Rm^T[k][col]
        //             = sum_k R_marker[row][k] * Rm[col][k]
        //
        // For col = 0: used for yaw / pitch
        double w00 = rm00*r(rotMat,0,0) + rm01*r(rotMat,0,1) + rm02*r(rotMat,0,2);
        double w10 = rm10*r(rotMat,0,0) + rm11*r(rotMat,0,1) + rm12*r(rotMat,0,2);
        double w20 = rm20*r(rotMat,0,0) + rm21*r(rotMat,0,1) + rm22*r(rotMat,0,2);
        // For col = 1: used for roll
        double w21 = rm20*r(rotMat,1,0) + rm21*r(rotMat,1,1) + rm22*r(rotMat,1,2);
        // For col = 2: used for roll
        double w22 = rm20*r(rotMat,2,0) + rm21*r(rotMat,2,1) + rm22*r(rotMat,2,2);

        // --- Extract ZYX Euler angles from W (R_c2w in global frame) ---
        // W = Rz(yaw) * Ry(pitch) * Rx(roll)
        // pitch = asin(-W[2][0]),  yaw = atan2(W[1][0], W[0][0]),  roll = atan2(W[2][1], W[2][2])
        double cameraPitch = Math.toDegrees(Math.asin(-clamp(w20, -1.0, 1.0)));
        double cameraYaw   = Math.toDegrees(Math.atan2(w10, w00));
        double cameraRoll  = Math.toDegrees(Math.atan2(w21, w22));

        // Draw detected marker outline and coordinate axes onto the frame.
        // The frame is owned by the caller and released after this method returns,
        // so annotating it in-place is safe.
        Log.d("ArucoMarkerDetector", frame.total() + ", " + frame.channels());
        Objdetect.drawDetectedMarkers(frame, corners, ids, MARKER_OUTLINE_COLOR);
        float axisLength = (float) (markerSizeM / 2.0);
        Calib3d.drawFrameAxes(frame, cameraMatrix, distCoeffs, rvec, tvec, axisLength, AXES_LINE_THICKNESS);
        Bitmap overlayBitmap = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(frame, overlayBitmap);

        // Release resources
        cameraMatrix.release();
        distCoeffs.release();
        objectPoints.release();
        imagePoints.release();
        rvec.release();
        tvec.release();
        rotMat.release();
        grayFrame.release();
        ids.release();
        releaseAll(corners);
        releaseAll(rejected);

        return new PoseResult(camX_w, camY_w, camZ_w,
                cameraYaw, cameraPitch, cameraRoll, markerId, overlayBitmap);
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
