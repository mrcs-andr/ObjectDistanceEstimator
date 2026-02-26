package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Room entity representing the result of a camera extrinsics calibration.
 * Stores the full 6-DOF camera pose (position + orientation) estimated from
 * ArUco marker detection.
 *
 * <p>Coordinate convention: world frame is Z-up (marker lies flat in the XY plane,
 * Z axis points upward). Position units are metres; angles are degrees.</p>
 */
@Entity(tableName = "extrinsics_results")
public class ExtrinsicsCalibrationResult {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Camera X position in world (metres, horizontal right). */
    public double cameraX;

    /** Camera Y position in world (metres, horizontal forward). */
    public double cameraY;

    /** Camera Z position in world (metres, height above ground). */
    public double cameraZ;

    /** Camera yaw angle in degrees (rotation around world Z axis / heading). */
    public double cameraYaw;

    /** Camera pitch angle in degrees (positive = tilted downward toward ground). */
    public double cameraPitch;

    /** Camera roll angle in degrees (rotation around the camera's forward axis). */
    public double cameraRoll;

    /** Timestamp of calibration (milliseconds since epoch). */
    public long timestamp;

    public ExtrinsicsCalibrationResult() {
    }

    @Ignore
    public ExtrinsicsCalibrationResult(double cameraX, double cameraY, double cameraZ,
                                        double cameraYaw, double cameraPitch, double cameraRoll,
                                        long timestamp) {
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
        this.cameraRoll = cameraRoll;
        this.timestamp = timestamp;
    }
}
