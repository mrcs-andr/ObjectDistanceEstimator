package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing the result of a camera intrinsics calibration.
 * Stores the camera matrix components and distortion coefficients.
 */
@Entity(tableName = "calibration_results")
public class CalibrationResult {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Focal length in x direction (pixels) */
    public double fx;

    /** Focal length in y direction (pixels) */
    public double fy;

    /** Principal point x coordinate (pixels) */
    public double cx;

    /** Principal point y coordinate (pixels) */
    public double cy;

    /** Radial distortion coefficient k1 */
    public double k1;

    /** Radial distortion coefficient k2 */
    public double k2;

    /** Tangential distortion coefficient p1 */
    public double p1;

    /** Tangential distortion coefficient p2 */
    public double p2;

    /** Radial distortion coefficient k3 */
    public double k3;

    /** Root mean square reprojection error */
    public double rmsError;

    /** Timestamp of calibration (milliseconds since epoch) */
    public long timestamp;

    /** Height of the camera above the ground plane, in metres */
    public double cameraHeight = 1.5;

    /** Camera pitch angle in degrees (positive = tilted downward toward ground) */
    public double cameraPitch = 0.0;

    public CalibrationResult() {
    }

    public CalibrationResult(double fx, double fy, double cx, double cy,
                              double k1, double k2, double p1, double p2, double k3,
                              double rmsError, long timestamp) {
        this.fx = fx;
        this.fy = fy;
        this.cx = cx;
        this.cy = cy;
        this.k1 = k1;
        this.k2 = k2;
        this.p1 = p1;
        this.p2 = p2;
        this.k3 = k3;
        this.rmsError = rmsError;
        this.timestamp = timestamp;
    }
}
