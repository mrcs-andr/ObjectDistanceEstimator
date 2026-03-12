package com.mrcs.andr.objectdistanceestimatorapp;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.mrcs.andr.objectdistanceestimatorapp.calibration.CalibrationDatabase;
import com.mrcs.andr.objectdistanceestimatorapp.calibration.CalibrationResult;
import com.mrcs.andr.objectdistanceestimatorapp.calibration.CalibrationRunner;
import com.mrcs.andr.objectdistanceestimatorapp.calibration.ChessboardDatasetLoader;
import com.mrcs.andr.objectdistanceestimatorapp.camera.CameraController;
import com.mrcs.andr.objectdistanceestimatorapp.camera.IFrameAvailableListener;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class IntrinsicsCalibrationActivity extends AppCompatActivity
        implements IFrameAvailableListener {

    private static final String TAG = "IntrinsicsCalibration";
    /**
     * Set to {@code true} to enable saving each frame where chessboard corners are detected to the
     * device's Downloads folder. Intended for debugging only — disabled by default.
     */
    private static final boolean DEBUG_SAVE_FRAMES = false;
    private static final int REQUIRED_IMAGE_COUNT = 20;
    private int savedImageCount = 0;
    private TextView tvLabel;
    private TextView tvChessboardStatus;
    private ImageView ivChessboardOverlay;
    private ProgressBar pbCalibration;
    private Button btnCalibrate;
    private EditText etChessRows;
    private EditText etChessCols;
    private EditText etSquareSize;
    private CameraController cameraController;
    private final ExecutorService calibrationExecutor = Executors.newSingleThreadExecutor();

    private volatile int liveChessRows = 6;
    private volatile int liveChessCols = 7;
    private Bitmap lastOverlayBitmap = null;

    // Latest detected corners from the live preview (updated on camera executor thread)
    private final AtomicReference<MatOfPoint2f> latestCorners = new AtomicReference<>(null);
    private volatile Size latestFrameSize = null;

    // Counter for naming debug frames in chronological order
    private final AtomicInteger debugFrameCounter = new AtomicInteger(0);

    // In-memory calibration data accumulated across "Take" presses
    private final List<Mat> accumulatedObjectPoints = new ArrayList<>();
    private Size calibImageSize = null;

    /**
     * On Create method for the IntrinsicsCalibrationActivity. Sets the content view to the activity_intrinsics_calibration layout.
     * @param savedInstanceState The saved instance state bundle, if any.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intrinsics_calibration);
        PreviewView previewView = findViewById(R.id.intrinsicsPreviewView);
        Button bntCapture = findViewById(R.id.buttonTakePicture);
        Button bntClear = findViewById(R.id.buttonClearPictures);
        this.btnCalibrate = findViewById(R.id.buttonCalibrate);
        this.tvLabel = findViewById(R.id.tvCount);
        this.pbCalibration = findViewById(R.id.progressCalibration);
        this.tvChessboardStatus = findViewById(R.id.tvChessboardStatus);
        this.ivChessboardOverlay = findViewById(R.id.ivChessboardOverlay);

        //Calibration parameters input fields
        this.etChessCols = findViewById(R.id.etCols);
        this.etChessRows = findViewById(R.id.etRows);
        this.etSquareSize = findViewById(R.id.etSquareSize);

        bindIntField(etChessRows, v -> liveChessRows = v, 6);
        bindIntField(etChessCols, v -> liveChessCols = v, 7);

        this.cameraController = new CameraController(this, this, this, previewView);
        this.cameraController.setMode(CameraController.Mode.ANALYSIS);
        this.cameraController.start();
        bntCapture.setOnClickListener(v -> onTakeClicked());
        bntClear.setOnClickListener(v -> clearCalibrationImages());
        this.btnCalibrate.setOnClickListener(v -> onCalibrateClicked());
    }

    /**
     * Clears all accumulated in-memory calibration data and resets the saved image count and UI elements.
     * Must be called on the UI thread (invoked from button click listeners only).
     */
    private void clearCalibrationImages() {
        for (Mat m : accumulatedImagePoints) m.release();
        for (Mat m : accumulatedObjectPoints) m.release();
        accumulatedImagePoints.clear();
        accumulatedObjectPoints.clear();
        calibImageSize = null;
        this.savedImageCount = 0;
        tvLabel.setText(savedImageCount + " / " + REQUIRED_IMAGE_COUNT);
        pbCalibration.setProgress(0);
        btnCalibrate.setEnabled(false);
    }

    /**
     * On Destroy Activity lifecycle event
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.cameraController.stop();
        this.calibrationExecutor.shutdown();
        for (Mat m : accumulatedImagePoints) m.release();
        for (Mat m : accumulatedObjectPoints) m.release();
        if (lastOverlayBitmap != null) {
            lastOverlayBitmap.recycle();
            lastOverlayBitmap = null;
        }
    }

    /**
     * Handles the take picture button click event. If a chessboard is currently detected in the live
     * preview, stores the detected corners in memory for later calibration. Rejects the capture if
     * no chessboard is detected or the square size is not configured.
     */
    private void onTakeClicked() {
        MatOfPoint2f snap = latestCorners.get();
        if (snap == null) {
            Toast.makeText(this, R.string.intrinsics_no_detection_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        int squareSizeMm = 0;
        try { squareSizeMm = Integer.parseInt(etSquareSize.getText().toString()); }
        catch (NumberFormatException ignored) {}
        if (squareSizeMm <= 0) {
            Toast.makeText(this, R.string.intrinsics_invalid_square_size_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        // Clone the detected corners into the accumulated list
        MatOfPoint2f cornersCopy = new MatOfPoint2f();
        snap.copyTo(cornersCopy);
        accumulatedImagePoints.add(cornersCopy);
        accumulatedObjectPoints.add(
                ChessboardDatasetLoader.createObjectPoints(liveChessCols, liveChessRows, squareSizeMm));

        if (calibImageSize == null) {
            calibImageSize = latestFrameSize;
        }

        ++savedImageCount;
        tvLabel.setText(savedImageCount + " / " + REQUIRED_IMAGE_COUNT);
        pbCalibration.setProgress((int) ((savedImageCount / (float) REQUIRED_IMAGE_COUNT) * 100));
        if (savedImageCount >= REQUIRED_IMAGE_COUNT) {
            btnCalibrate.setEnabled(true);
        }
    }

    /**
     * Starts the calibration process using the accumulated in-memory data. Runs on a background thread,
     * shows a progress dialog while running, then displays the result and saves it to the database.
     */
    private void onCalibrateClicked() {
        btnCalibrate.setEnabled(false);

        // Snapshot the accumulated in-memory data before launching background work
        final List<Mat> imagePoints = new ArrayList<>(accumulatedImagePoints);
        final List<Mat> objectPoints = new ArrayList<>(accumulatedObjectPoints);
        final Size imageSize = calibImageSize;

        if (imagePoints.isEmpty() || imageSize == null) {
            Toast.makeText(this, R.string.calibration_no_patterns, Toast.LENGTH_LONG).show();
            btnCalibrate.setEnabled(savedImageCount >= REQUIRED_IMAGE_COUNT);
            return;
        }

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.calibration_in_progress_title)
                .setMessage(R.string.calibration_in_progress_message)
                .setCancelable(false)
                .create();
        progressDialog.show();

        calibrationExecutor.execute(() -> {
            try {
                Log.d(TAG, "Running calibration with " + imagePoints.size() + " captured frames.");

                CalibrationRunner.Result calibResult = CalibrationRunner.Result.calibrate(
                        imagePoints, objectPoints, imageSize);

                CalibrationResult dbResult = toCalibrationResult(calibResult);
                calibResult.cameraMatrix.release();
                calibResult.distCoeffs.release();

                // Save result to database (synchronous insert on background thread)
                CalibrationDatabase.getInstance(getApplicationContext())
                        .calibrationDao().insert(dbResult);

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.calibration_result_title)
                            .setMessage(getString(R.string.calibration_result_message,
                                    dbResult.fx, dbResult.fy, dbResult.cx, dbResult.cy,
                                    dbResult.rmsError))
                            .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                            .show();
                    btnCalibrate.setEnabled(true);
                });
            } catch (Exception e) {
                Log.e(TAG, "Calibration failed", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this,
                            getString(R.string.calibration_failed_message, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                    btnCalibrate.setEnabled(savedImageCount >= REQUIRED_IMAGE_COUNT);
                });
            }
        });
    }

    /**
     * Converts a {@link CalibrationRunner.Result} (raw OpenCV Mats) into a {@link CalibrationResult}
     * entity suitable for database persistence.
     */
    private CalibrationResult toCalibrationResult(CalibrationRunner.Result r) {
        double fx = r.cameraMatrix.get(0, 0)[0];
        double fy = r.cameraMatrix.get(1, 1)[0];
        double cx = r.cameraMatrix.get(0, 2)[0];
        double cy = r.cameraMatrix.get(1, 2)[0];

        double[] dist = new MatOfDouble(r.distCoeffs).toArray();
        double k1 = dist.length > 0 ? dist[0] : 0;
        double k2 = dist.length > 1 ? dist[1] : 0;
        double p1 = dist.length > 2 ? dist[2] : 0;
        double p2 = dist.length > 3 ? dist[3] : 0;
        double k3 = dist.length > 4 ? dist[4] : 0;

        return new CalibrationResult(fx, fy, cx, cy, k1, k2, p1, p2, k3,
                r.reprojectionError, System.currentTimeMillis());
    }

    /**
     * Processes each live camera frame: detects chessboard corners and draws them on the preview overlay.
     * Also stores the latest detected corners in memory so that pressing "Take" can save them
     * without any disk I/O.
     */
    @Override
    public void onFrameAvailable(Bitmap bmp) {
        int cols = liveChessCols;
        int rows = liveChessRows;
        if (cols <= 0 || rows <= 0) return;

        Mat frame = new Mat();
        Utils.bitmapToMat(bmp, frame);

        Mat gray = new Mat();
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGBA2GRAY);

        Size patternSize = new Size(cols, rows);
        MatOfPoint2f corners = new MatOfPoint2f();
        boolean found = Calib3d.findChessboardCorners(gray, patternSize, corners,
                Calib3d.CALIB_CB_ADAPTIVE_THRESH + Calib3d.CALIB_CB_NORMALIZE_IMAGE);

        Bitmap overlayBmp = null;
        if (found) {
            Imgproc.cornerSubPix(gray, corners, new Size(11, 11), new Size(-1, -1),
                    new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 30, 0.001));

            // Store a copy of the detected corners and frame size for use when "Take" is pressed.
            // We intentionally do NOT release the previous latestCorners value here: doing so would
            // introduce a race condition where the UI thread could be mid-copyTo on the old Mat
            // when this thread releases it. The replaced Mats are tiny (~35 points × 8 bytes) and
            // are reclaimed by OpenCV's native finalizer without meaningful memory pressure.
            MatOfPoint2f cornersCopy = new MatOfPoint2f();
            corners.copyTo(cornersCopy);
            latestFrameSize = new Size(frame.cols(), frame.rows());
            latestCorners.set(cornersCopy);

            Calib3d.drawChessboardCorners(frame, patternSize, corners, true);
            overlayBmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(frame, overlayBmp);
            if (DEBUG_SAVE_FRAMES) {
                saveDebugFrame(overlayBmp);
            }
        } else {
            latestCorners.set(null);
        }

        gray.release();
        corners.release();
        frame.release();

        final Bitmap finalOverlayBmp = overlayBmp;
        runOnUiThread(() -> {
            Bitmap old = lastOverlayBitmap;
            if (finalOverlayBmp != null) {
                lastOverlayBitmap = finalOverlayBmp;
                ivChessboardOverlay.setImageBitmap(finalOverlayBmp);
                ivChessboardOverlay.setVisibility(View.VISIBLE);
                tvChessboardStatus.setText(R.string.intrinsics_status_detected);
            } else {
                lastOverlayBitmap = null;
                ivChessboardOverlay.setImageBitmap(null);
                ivChessboardOverlay.setVisibility(View.INVISIBLE);
                tvChessboardStatus.setText(R.string.intrinsics_status_searching);
            }
            if (old != null) old.recycle();
        });
    }

    /**
     * Saves a debug copy of {@code bmp} (containing the drawn chessboard corners) to the device's
     * public Downloads folder. This method is only active when {@link #DEBUG_SAVE_FRAMES} is
     * {@code true}. On API 29+ the file is inserted via {@link MediaStore}; on earlier versions it
     * is written directly to {@link Environment#DIRECTORY_DOWNLOADS}.
     *
     * <p>Failures are logged but never rethrown so they never disrupt the live preview.</p>
     *
     * @param bmp The bitmap to persist. Must not be null or recycled.
     */
    @SuppressWarnings("deprecation") // Environment.getExternalStoragePublicDirectory used on API < 29
    private void saveDebugFrame(Bitmap bmp) {
        String filename = String.format(Locale.US,
                "calib_debug_%04d.png", debugFrameCounter.incrementAndGet());
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                cv.put(MediaStore.Downloads.MIME_TYPE, "image/png");
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    try {
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                        }
                        cv.clear();
                        cv.put(MediaStore.Downloads.IS_PENDING, 0);
                        getContentResolver().update(uri, cv, null, null);
                        Log.d(TAG, "Debug frame saved via MediaStore: " + filename);
                    } catch (Exception e) {
                        // Remove the pending entry so it does not remain incomplete
                        getContentResolver().delete(uri, null, null);
                        throw e;
                    }
                }
            } else {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    downloadsDir.mkdirs();
                }
                File out = new File(downloadsDir, filename);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                } catch (Exception e) {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    throw e;
                }
                Log.d(TAG, "Debug frame saved to: " + out.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save debug frame: " + e.getMessage(), e);
        }
    }

    /**
     * Attaches a {@link TextWatcher} to an {@link EditText} that writes the parsed integer value
     * to the given consumer. Falls back to {@code defaultValue} on parse errors.
     */
    private void bindIntField(EditText et, IntConsumer consumer, int defaultValue) {
        consumer.accept(defaultValue);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    consumer.accept(Integer.parseInt(s.toString()));
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    private interface IntConsumer {
        void accept(int value);
    }
}
