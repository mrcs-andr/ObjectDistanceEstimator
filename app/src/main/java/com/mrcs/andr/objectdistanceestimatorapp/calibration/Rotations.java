package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import org.opencv.core.*;
import org.opencv.calib3d.Calib3d;

import java.util.Arrays;
import java.util.List;

public class Rotations {

    /**
     * Convert ZYX Euler angles (yaw, pitch, roll) in degrees to a rotation matrix R.
     * @param yaw Rotation around Z-axis in degrees
     * @param pitch Rotation around Y-axis in degrees
     * @param roll Rotation around X-axis in degrees
     * @return Rotation matrix (3x3) representing the orientation of an element
     */
    public static Mat eulerZYXToRotationMatrix(double yaw, double pitch, double roll) {
        // Convert angles from degrees to radians
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double rollRad = Math.toRadians(roll);

        double cr = Math.cos(roll),  sr = Math.sin(roll);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double cy = Math.cos(yaw),   sy = Math.sin(yaw);

        // Compute individual rotation matrices
        // Rz(yaw)
        Mat Rz = new Mat(3,3, CvType.CV_64F);
        Rz.put(0,0,
                cy, -sy, 0,
                sy,  cy, 0,
                0,   0, 1);

        // Ry(pitch)
        Mat Ry = new Mat(3,3, CvType.CV_64F);
        Ry.put(0,0,
                cp, 0, sp,
                0, 1,  0,
                -sp, 0, cp);

        // Rx(roll)
        Mat Rx = new Mat(3,3, CvType.CV_64F);
        Rx.put(0,0,
                1,  0,   0,
                0, cr, -sr,
                0, sr,  cr);

        Mat Rzy = new Mat();
        Core.gemm(Rz, Ry, 1.0, new Mat(), 0.0, Rzy);

        Mat R = new Mat();
        Core.gemm(Rzy, Rx, 1.0, new Mat(), 0.0, R);

        Rz.release();
        Ry.release();
        Rx.release();
        Rzy.release();

        return R;
    }

    /**
     * Convert a rotation matrix R to ZYX Euler angles (yaw, pitch, roll) in degrees.
     * @param R Rotation matrix (3x3) representing the orientation of an element
     * @return List of Euler angles [yaw, pitch, roll] in degrees
     */
    public static List<Double> RMatToEulerZYX(Mat  R) {
        // Elementos de R
        double r00 = R.get(0,0)[0], r01 = R.get(0,1)[0], r02 = R.get(0,2)[0];
        double r10 = R.get(1,0)[0], r11 = R.get(1,1)[0], r12 = R.get(1,2)[0];
        double r20 = R.get(2,0)[0], r21 = R.get(2,1)[0], r22 = R.get(2,2)[0];

        // 2) Extract ZYX:
        // pitch = asin(-r20)
        // roll  = atan2(r21, r22)
        // yaw   = atan2(r10, r00)
        double pitch = Math.asin(-r20);
        double cosPitch = Math.cos(pitch);

        double roll, yaw;
        if (Math.abs(cosPitch) > 1e-9) {
            roll = Math.atan2(r21, r22);
            yaw  = Math.atan2(r10, r00);
        } else {
            // gimbal lock: pitch ~ ±90°
            roll = 0.0;
            yaw  = Math.atan2(-r01, r11);
        }

        return Arrays.asList(Math.toDegrees(yaw), Math.toDegrees(pitch), Math.toDegrees(roll));
    }


}
