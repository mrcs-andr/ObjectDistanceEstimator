package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.mrcs.andr.objectdistanceestimatorapp.R;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;
import org.opencv.calib3d.Calib3d;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for camera intrinsics calibration.
 * The user captures 20 pictures of a checkerboard pattern. Once 20 pictures
 * are captured, the "Calibrate" button is enabled. Pressing it runs the
 * OpenCV calibration and saves the result to the Room database.
 */
public class CalibrationActivity extends AppCompatActivity {

    private static final String TAG = "CalibrationActivity";
    private static final int REQUIRED_PICTURES = 20;
    private static final int CHESSBOARD_COLS = 9;
    private static final int CHESSBOARD_ROWS = 6;

    private PreviewView previewView;
    private Button btnTakePicture;
    private Button btnCalibrate;
    private TextView tvPictureCount;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    private final List<Mat> capturedCorners = new ArrayList<>();
    private int pictureCount = 0;
    private Size capturedImageSize = null;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            startCamera();
                        } else {
                            Toast.makeText(this, getString(R.string.calibration_camera_permission_denied),
                                    Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        previewView = findViewById(R.id.calibrationPreviewView);
        btnTakePicture = findViewById(R.id.btnTakePicture);
        btnCalibrate = findViewById(R.id.btnCalibrate);
        tvPictureCount = findViewById(R.id.tvPictureCount);

        cameraExecutor = Executors.newSingleThreadExecutor();

        updatePictureCount();

        btnTakePicture.setOnClickListener(v -> takePicture());
        btnCalibrate.setOnClickListener(v -> runCalibration());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    /**
     * Start camera preview and prepare ImageCapture use case.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (Exception e) {
                Log.e(TAG, "Failed to start camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Capture a single picture and attempt to detect checkerboard corners.
     */
    private void takePicture() {
        if (imageCapture == null) {
            return;
        }
        btnTakePicture.setEnabled(false);

        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(ImageProxy image) {
                try {
                    Bitmap bitmap = imageProxyToBitmap(image);
                    processImageForCalibration(bitmap);
                } finally {
                    image.close();
                    runOnUiThread(() -> btnTakePicture.setEnabled(true));
                }
            }

            @Override
            public void onError(ImageCaptureException exception) {
                Log.e(TAG, "Image capture failed", exception);
                runOnUiThread(() -> {
                    btnTakePicture.setEnabled(true);
                    Toast.makeText(CalibrationActivity.this,
                            getString(R.string.calibration_capture_failed), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Convert ImageProxy (JPEG format from ImageCapture) to Bitmap, handling rotation.
     */
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        int rotation = image.getImageInfo().getRotationDegrees();
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    matrix, true);
        }
        return bitmap;
    }

    /**
     * Process a captured bitmap: detect checkerboard corners and store them.
     * @param bitmap the captured image
     */
    private void processImageForCalibration(Bitmap bitmap) {
        Mat gray = new Mat();
        Mat colorMat = new Mat();
        Utils.bitmapToMat(bitmap, colorMat);
        Imgproc.cvtColor(colorMat, gray, Imgproc.COLOR_RGB2GRAY);

        // Record the actual captured image dimensions for use during calibration
        if (capturedImageSize == null) {
            capturedImageSize = new Size(colorMat.cols(), colorMat.rows());
        }
        colorMat.release();

        Size patternSize = new Size(CHESSBOARD_COLS, CHESSBOARD_ROWS);
        MatOfPoint2f corners = new MatOfPoint2f();
        boolean found = Calib3d.findChessboardCorners(gray, patternSize, corners,
                Calib3d.CALIB_CB_ADAPTIVE_THRESH | Calib3d.CALIB_CB_NORMALIZE_IMAGE);

        if (found) {
            TermCriteria criteria = new TermCriteria(
                    TermCriteria.EPS | TermCriteria.MAX_ITER, 30, 0.001);
            Imgproc.cornerSubPix(gray, corners, new Size(11, 11), new Size(-1, -1), criteria);
            capturedCorners.add(corners);
            pictureCount++;
            runOnUiThread(() -> {
                updatePictureCount();
                Toast.makeText(this, getString(R.string.calibration_pattern_found,
                        pictureCount, REQUIRED_PICTURES), Toast.LENGTH_SHORT).show();
                if (pictureCount >= REQUIRED_PICTURES) {
                    btnCalibrate.setEnabled(true);
                }
            });
        } else {
            corners.release();
            runOnUiThread(() -> Toast.makeText(this,
                    getString(R.string.calibration_pattern_not_found), Toast.LENGTH_SHORT).show());
        }
        gray.release();
    }

    /**
     * Run the OpenCV camera calibration and save the result to the database.
     */
    private void runCalibration() {
        btnCalibrate.setEnabled(false);
        btnTakePicture.setEnabled(false);

        cameraExecutor.execute(() -> {
            try {
                double rms = performCalibration();
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            getString(R.string.calibration_success, rms), Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "Calibration failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.calibration_failed),
                            Toast.LENGTH_LONG).show();
                    btnCalibrate.setEnabled(true);
                    btnTakePicture.setEnabled(true);
                });
            }
        });
    }

    /**
     * Perform the actual OpenCV calibration using collected corner data.
     * @return the RMS reprojection error
     */
    private double performCalibration() {
        List<Mat> objectPoints = new ArrayList<>();
        for (int i = 0; i < capturedCorners.size(); i++) {
            objectPoints.add(buildObjectPoints());
        }

        Mat cameraMatrix = Mat.eye(3, 3, CvType.CV_64F);
        MatOfDouble distCoeffs = new MatOfDouble();
        List<Mat> rvecs = new ArrayList<>();
        List<Mat> tvecs = new ArrayList<>();

        // Use the actual captured image size for correct calibration
        Size imageSize = capturedImageSize != null
                ? capturedImageSize
                : new Size(previewView.getWidth(), previewView.getHeight());

        double rms = Calib3d.calibrateCamera(objectPoints, capturedCorners,
                imageSize, cameraMatrix, distCoeffs, rvecs, tvecs);

        double fx = cameraMatrix.get(0, 0)[0];
        double fy = cameraMatrix.get(1, 1)[0];
        double cx = cameraMatrix.get(0, 2)[0];
        double cy = cameraMatrix.get(1, 2)[0];

        double[] distData = distCoeffs.toArray();
        double k1 = distData.length > 0 ? distData[0] : 0;
        double k2 = distData.length > 1 ? distData[1] : 0;
        double p1 = distData.length > 2 ? distData[2] : 0;
        double p2 = distData.length > 3 ? distData[3] : 0;
        double k3 = distData.length > 4 ? distData[4] : 0;

        CalibrationResult result = new CalibrationResult(fx, fy, cx, cy, k1, k2, p1, p2, k3,
                rms, System.currentTimeMillis());

        // Insert is performed on the background cameraExecutor thread; Room allows
        // synchronous database access on non-main threads.
        CalibrationDatabase.getInstance(this).calibrationDao().insert(result);

        Log.d(TAG, String.format("Calibration done. RMS=%.4f fx=%.2f fy=%.2f cx=%.2f cy=%.2f",
                rms, fx, fy, cx, cy));

        cameraMatrix.release();
        distCoeffs.release();
        for (Mat rv : rvecs) rv.release();
        for (Mat tv : tvecs) tv.release();
        for (Mat op : objectPoints) op.release();

        return rms;
    }

    /**
     * Build the 3D object points for a single checkerboard view.
     * Assumes squares of size 1 (unit) in real-world coordinates.
     * @return MatOfPoint3f with 3D corner positions
     */
    private MatOfPoint3f buildObjectPoints() {
        List<Point3> points = new ArrayList<>();
        for (int row = 0; row < CHESSBOARD_ROWS; row++) {
            for (int col = 0; col < CHESSBOARD_COLS; col++) {
                points.add(new Point3(col, row, 0));
            }
        }
        MatOfPoint3f mat = new MatOfPoint3f();
        mat.fromList(points);
        return mat;
    }

    /**
     * Update the picture count text view.
     */
    private void updatePictureCount() {
        tvPictureCount.setText(getString(R.string.calibration_picture_count,
                pictureCount, REQUIRED_PICTURES));
    }
}
