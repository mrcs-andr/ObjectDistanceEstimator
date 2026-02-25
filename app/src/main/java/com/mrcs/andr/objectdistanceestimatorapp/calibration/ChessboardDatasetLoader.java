package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ChessboardDatasetLoader {

    /**
     * Dataset class encapsulates the results of loading and detecting chessboard corners from a set of images. It contains
     * the list of detected image points (corners), the corresponding object points in the world coordinate system, and the size of the images used for calibration.
     */
    public static class Dataset{
        public final List<Mat> imagePoints;
        public final List<Mat> objectsPoints;
        public final Size imageSize;

        public Dataset(List<Mat> imagePoints, List<Mat> objectsPoints, Size imageSize) {
            this.imagePoints = imagePoints;
            this.objectsPoints = objectsPoints;
            this.imageSize = imageSize;
        }
    }

    /**
     * Loads images from the specified directory, detects chessboard corners in each image,
     * and returns a Dataset containing the detected image points, corresponding object points, and image size.
     * The method expects the images to contain a chessboard pattern with the specified number of rows and columns of inner corners, and the size of each square in millimeters.
     * @param directory The directory containing the chessboard images for calibration.
     * @param rols number of inner corners per chessboard column
     * @param cols number of inner corners per chessboard row
     * @param squareSizeMm
     * @return Dataset containing the detected image points, corresponding object points, and image size for the chessboard calibration process.
     */
    public Dataset loadAndDectect(File directory, int rols, int cols, int squareSizeMm) {

        if(directory == null || !directory.exists() || !directory.isDirectory()) {
            assert directory != null;
            throw new IllegalArgumentException(directory.getAbsolutePath());
        }

        File[] files = directory.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
        });

        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No image files found in directory: " + directory.getAbsolutePath());
        }

        List<Mat> imagePoints = new ArrayList<>();
        List<Mat> objectsPoints = new ArrayList<>();
        MatOfPoint3f objectPoints = createObjectPoints(cols, rols, squareSizeMm);

        Size imageSize = null;

        for(File file : files) {
            Mat img = Imgcodecs.imread(file.getAbsolutePath(), Imgcodecs.IMREAD_COLOR);
            if(img.empty()) {
                continue;
            }

            if(imageSize==null) {
                imageSize = new Size(img.width(), img.height());
            }

            //1 - Convert it to grayscale
            Mat gray = new Mat();
            Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);

            //2 - Get the size of the chessboard pattern
            Size patternSize = new Size(cols, rols);

            //3 - Find the chessboard corners
            MatOfPoint2f corners = new MatOfPoint2f();
            boolean found = Calib3d.findChessboardCorners(gray, patternSize, corners,
                    Calib3d.CALIB_CB_ADAPTIVE_THRESH + Calib3d.CALIB_CB_NORMALIZE_IMAGE);

            //4 - if not found, skip this image
            if(!found){
                continue;
            }

            //5 - Refine the corner positions
            Imgproc.cornerSubPix(gray, corners, new Size(11, 11), new Size(-1, -1),
                    new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 30, 0.001));

            //6 - Store the results in the dataset
            imagePoints.add(corners);
            objectsPoints.add(objectPoints);

            //7. - Release the Mats to free memory
            img.release();
            gray.release();
        }

        if(imageSize == null) {
            throw new IllegalArgumentException("No valid chessboard corners found in any image in directory: " + directory.getAbsolutePath());
        }

        return new Dataset(imagePoints, objectsPoints, imageSize);

    }

    /**
     * Creates a MatOfPoint3f containing the 3D coordinates of the chessboard corners in the world coordinate system.
     * @param cols number of inner corners per chessboard row
     * @param rows number of inner corners per chessboard column
     * @param squareSize The size of each square of the board.
     * @return MatOfPoint3f containing the 3D coordinates of the chessboard corners in the world coordinate system.
     */
    private static MatOfPoint3f createObjectPoints(int cols, int rows, float squareSize) {
        List<Point3> pts = new ArrayList<>(cols * rows);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                pts.add(new Point3(x * squareSize, y * squareSize, 0.0));
            }
        }
        MatOfPoint3f obj = new MatOfPoint3f();
        obj.fromList(pts);
        return obj;
    }

}
