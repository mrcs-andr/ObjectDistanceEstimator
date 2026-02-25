package com.mrcs.andr.objectdistanceestimatorapp.distance;

import com.mrcs.andr.objectdistanceestimatorapp.calibration.CalibrationResult;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.ILetterBoxObserver;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.LetterBoxParams;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

/**
 * Estimates the distance from the camera to a detected object using a ground-plane (z = 0)
 * intersection model.
 *
 * <p>The camera is assumed to be mounted at a known height above the ground and to be rotated
 * around its own X-axis by a known pitch angle. The bottom-centre of each detection's bounding
 * box is first undistorted using OpenCV's {@link Calib3d#undistortPoints} with the calibrated
 * camera matrix and distortion coefficients, then back-projected to a ray in world space.
 * The intersection of that ray with the ground plane (world Y = 0) gives the 3-D position of
 * the foot of the object, and the Euclidean distance to that point is returned in metres.</p>
 *
 * <p>Camera-to-world rotation used:
 * <pre>
 *   R_c2w = [[1,       0,        0      ],
 *            [0, -cos(p),  -sin(p)],
 *            [0, -sin(p),   cos(p)]]
 * </pre>
 * where {@code p} is the pitch angle in radians (positive = tilted downward toward the ground).
 * World convention: X right, Y up, Z forward.</p>
 */
public class DistanceEstimator implements ILetterBoxObserver {

    private volatile CalibrationResult calibration;
    private volatile LetterBoxParams letterBoxParams;

    /**
     * Updates the camera calibration used for distance estimation.
     * Safe to call from any thread.
     *
     * @param calibration latest calibration result from the database
     */
    public void setCalibration(CalibrationResult calibration) {
        this.calibration = calibration;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stores the latest letterbox parameters so that model-space bounding-box coordinates
     * can be converted back to the original camera image space before applying the intrinsics.</p>
     */
    @Override
    public void onLetterBoxComputed(LetterBoxParams params) {
        this.letterBoxParams = params;
    }

    /**
     * Estimates the 3-D Euclidean distance from the camera to the foot of a detected object
     * (i.e. the bottom-centre of the bounding box projected onto the ground plane).
     *
     * <p>Uses {@link Calib3d#undistortPoints} to correct lens distortion before computing
     * the ground-plane ray intersection, ensuring the stored distortion coefficients
     * (k1, k2, p1, p2, k3) are taken into account.</p>
     *
     * @param d detection in model space (coordinates in the letterboxed 512x512 input space)
     * @return distance in metres, or {@link Float#NaN} if calibration / letterbox params are not
     *         yet available, or if the ray does not intersect the ground plane
     */
    public float estimate(Detection d) {
        CalibrationResult cal = this.calibration;
        LetterBoxParams lb = this.letterBoxParams;
        if (cal == null || lb == null || lb.scale <= 0f) {
            return Float.NaN;
        }

        if (cal.cameraHeight <= 0 || cal.fx <= 0 || cal.fy <= 0) {
            return Float.NaN;
        }

        // Bottom-centre of the bounding box in model space
        float u_m = d.x + d.width / 2f;
        float v_m = d.y + d.height;

        // Convert to original camera image space using the letterbox parameters
        double u = (u_m - lb.padX) / lb.scale;
        double v = (v_m - lb.padY) / lb.scale;

        // Build OpenCV camera matrix from stored intrinsics
        Mat cameraMatrix = new Mat(3, 3, CvType.CV_64F);
        cameraMatrix.put(0, 0,
                cal.fx,     0, cal.cx,
                    0, cal.fy, cal.cy,
                    0,      0,      1);

        // Build OpenCV distortion coefficients [k1, k2, p1, p2, k3]
        Mat distCoeffs = new Mat(1, 5, CvType.CV_64F);
        distCoeffs.put(0, 0, cal.k1, cal.k2, cal.p1, cal.p2, cal.k3);

        // Undistort the pixel using OpenCV – returns normalised coordinates
        MatOfPoint2f distortedPt = new MatOfPoint2f(new Point(u, v));
        MatOfPoint2f undistortedPt = new MatOfPoint2f();
        Calib3d.undistortPoints(distortedPt, undistortedPt, cameraMatrix, distCoeffs);

        double[] xy = undistortedPt.get(0, 0);
        double xn = xy[0];
        double yn = xy[1];

        cameraMatrix.release();
        distCoeffs.release();
        distortedPt.release();
        undistortedPt.release();

        double h = cal.cameraHeight;
        double pitch = Math.toRadians(cal.cameraPitch);
        double cosp = Math.cos(pitch);
        double sinp = Math.sin(pitch);

        // World-space Y component of the ray (R_c2w second row · d_c)
        // R_c2w row 1: [0, -cos(p), -sin(p)]
        double dwy = -cosp * yn - sinp;

        // The ray must be heading downward (negative world Y) to hit the ground
        if (dwy >= 0) {
            return Float.NaN;
        }

        // Parameter t at which the ray intersects Y = 0 (ground plane)
        double t = -h / dwy;  // positive

        // World-space X and Z components of the ray
        double dwx = xn;
        double dwz = -sinp * yn + cosp;

        // 3-D Euclidean distance from camera to the ground point
        double dist = t * Math.sqrt(dwx * dwx + dwy * dwy + dwz * dwz);

        return (float) dist;
    }
}
