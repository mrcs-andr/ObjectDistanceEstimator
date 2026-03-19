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
import org.opencv.core.Core;
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
     * Helper method to build the world-to-marker transform T_wm from the marker's top-left
     * @param xTL x position of the marker's top-left corner in world coordinates (metres)
     * @param yTL y position of the marker's top-left corner in world coordinates (metres)
     * @param zTL z position of the marker's top-left corner in world coordinates (metres)
     * @param yawDeg yaw rotation of the marker in world frame (degrees, rotation around Z)
     * @param pitchDeg pitch rotation of the marker in world frame (degrees, rotation around Y)
     * @param rollDeg roll rotation of the marker in world frame (degrees, rotation around X)
     * @param markerLen physical side length of the marker (metres)
     * @return 4x4 homogeneous transform T_wm from marker frame to world frame, where the marker's
     */
    public Mat buildT_wmFromTL_XYZ_YPR_Deg(
            double xTL, double yTL, double zTL,
            double yawDeg, double pitchDeg, double rollDeg,
            double markerLen
    ) {

        // 2) Marker World Rotation, euler angles, comes from interface.
        Mat R_wm = Rotations.eulerZYXToRotationMatrix(yawDeg, pitchDeg, rollDeg );

        // 3) Tl Translation in world frame, comes from interface.
        Mat t_w_TL = new Mat(3, 1, CvType.CV_64F);
        t_w_TL.put(0, 0, xTL);
        t_w_TL.put(1, 0, yTL);
        t_w_TL.put(2, 0, zTL);

        // 4) TL -> Marker center.
        double half = markerLen * 0.5;
        Mat d_M = new Mat(3, 1, CvType.CV_64F);
        d_M.put(0, 0, 0.0);
        d_M.put(1, 0, -half);
        d_M.put(2, 0, -half);

        // 5) t_w_center = t_w_TL + R_wm * d_M
        Mat Rwm_dM = new Mat();
        Core.gemm(R_wm, d_M, 1.0, new Mat(), 0.0, Rwm_dM);

        Mat t_w_center = new Mat();
        Core.add(t_w_TL, Rwm_dM, t_w_center);

        // 6) Create T_wm.
        Mat T_wm = Mat.eye(4, 4, CvType.CV_64F);
        R_wm.copyTo(T_wm.submat(0, 3, 0, 3));
        t_w_center.copyTo(T_wm.submat(0, 3, 3, 4));

        return T_wm;
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

        //Get Marker pose relative to camera (rotation vector rvec and translation vector tvec)
        Mat rvec = new Mat();
        Mat tvec = new Mat();
        Calib3d.solvePnP(objectPoints, imagePoints, cameraMatrix, distCoeffs, rvec, tvec);

        //Get the camera pose in the marker frame.
        Mat R_cm = new Mat();
        Calib3d.Rodrigues(rvec, R_cm);
        Mat R_mc = R_cm.t(); // camera-to-marker rotation
        Mat t_mc = new Mat();  // camera position in marker frame: C_m = -R_mc * tvec
        Core.gemm(R_mc, tvec, -1.0, new Mat(), 0.0, t_mc);

        //Get marker in world frame transform T_wm from the marker's top-left corner position and full orientation.
        Mat T_wm = buildT_wmFromTL_XYZ_YPR_Deg(
                markerWorldX, markerWorldY, markerWorldZ,
                markerWorldYaw, markerWorldPitch, markerWorldRoll,
                markerSizeM);

        //Get camera in world frame transform T_wc = T_wm * T_mc
        // Build ^M T_C (camera in marker) as 4x4
        Mat T_mc = Mat.eye(4, 4, CvType.CV_64F);
        R_mc.convertTo(R_mc, CvType.CV_64F);
        t_mc.convertTo(t_mc, CvType.CV_64F);
        R_mc.copyTo(T_mc.submat(0, 3, 0, 3));
        t_mc.copyTo(T_mc.submat(0, 3, 3, 4));

        // Compose: ^W T_C = ^W T_M * ^M T_C
        Mat T_wc = new Mat();
        Core.gemm(T_wm, T_mc, 1.0, new Mat(), 0.0, T_wc);

        // R_wc = T[0:3, 0:3]
        Mat R_wc = T_wc.submat(0, 3, 0, 3).clone();
        R_wc.convertTo(R_wc, CvType.CV_64F);

        // t_wc = T[0:3, 3]
        Mat t_wc = T_wc.submat(0, 3, 3, 4).clone(); // 3x1
        t_wc.convertTo(t_wc, CvType.CV_64F);

        double camX_w = t_wc.get(0, 0)[0];
        double camY_w = t_wc.get(1, 0)[0];
        double camZ_w = t_wc.get(2, 0)[0];

        List<Double> eulerZYX = Rotations.RMatToEulerZYX(R_wc);
        double cameraYaw = eulerZYX.get(0);
        double cameraPitch = eulerZYX.get(1);
        double cameraRoll = eulerZYX.get(2);

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
        grayFrame.release();
        cameraMatrix.release();
        distCoeffs.release();
        objectPoints.release();
        imagePoints.release();
        rvec.release();
        tvec.release();
        R_cm.release();
        t_mc.release();
        T_wc.release();
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
