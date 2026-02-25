package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * CalibrationRunner is a utility class that provides functionality to perform camera calibration using OpenCV.
 * It contains a nested Result class that encapsulates the results of the calibration process, including
 * the reprojection error, camera matrix, and distortion coefficients. The static calibrate method in the
 * Result class performs the actual calibration given the image points, object points, and image size.
 */
public final class CalibrationRunner {

    public CalibrationRunner(){}

    /**
     * Result class encapsulates the results of the camera calibration process, including the reprojection error,
     * camera matrix, and distortion coefficients. It also provides a static method to perform the calibration
     * given the image points, object points, and image size.
     */
    public static class Result {
        public final double reprojectionError;
        public final Mat cameraMatrix;
        public final Mat distCoeffs;

        /**
         * Constructor for the Result class.
         * @param reprojectionError The reprojection error of the calibration.
         * @param cameraMatrix The camera matrix obtained from calibration.
         * @param distCoeffs The distortion coefficients obtained from calibration.
         */
        public Result(double reprojectionError, Mat cameraMatrix, Mat distCoeffs) {
            this.reprojectionError = reprojectionError;
            this.cameraMatrix = cameraMatrix;
            this.distCoeffs = distCoeffs;
        }

        /**
         * Static method to perform camera calibration using the provided image points, object points, and image size.
         * @param imagePoints A list of MatOfPoint2f representing the detected corners in the images.
         * @param objectPoints A list of MatOfPoint3f representing the corresponding 3D points in the world.
         * @param imageSize The size of the images used for calibration.
         * @return A Result object containing the reprojection error, camera matrix, and distortion coefficients.
         */
        public static Result calibrate(List<Mat> imagePoints, List<Mat> objectPoints, Size imageSize) {
            Mat cameraMatrix = new Mat();
            Mat distCoeffs = new Mat();
            List<Mat> rvecs = new ArrayList<>();
            List<Mat> tvecs = new ArrayList<>();

            double reprojectionError = Calib3d.calibrateCamera(objectPoints, imagePoints, imageSize,
                    cameraMatrix, distCoeffs, rvecs, tvecs);

            return new Result(reprojectionError, cameraMatrix, distCoeffs);
        }

    }


}
