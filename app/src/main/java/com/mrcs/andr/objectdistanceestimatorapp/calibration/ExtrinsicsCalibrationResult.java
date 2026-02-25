package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing the result of a camera extrinsics calibration.
 * Stores the camera pose parameters (height and pitch) estimated from ArUco marker detection.
 */
@Entity(tableName = "extrinsics_results")
public class ExtrinsicsCalibrationResult {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Height of the camera above the ground plane, in metres */
    public double cameraHeight;

    /** Camera pitch angle in degrees (positive = tilted downward toward ground) */
    public double cameraPitch;

    /** Timestamp of calibration (milliseconds since epoch) */
    public long timestamp;

    public ExtrinsicsCalibrationResult() {
    }

    public ExtrinsicsCalibrationResult(double cameraHeight, double cameraPitch, long timestamp) {
        this.cameraHeight = cameraHeight;
        this.cameraPitch = cameraPitch;
        this.timestamp = timestamp;
    }
}
