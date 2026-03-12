package com.mrcs.andr.objectdistanceestimatorapp;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IntrinsicsCalibrationActivity extends AppCompatActivity
        implements IFrameAvailableListener {

    private static final String TAG = "IntrinsicsCalibration";
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
        this.cameraController.setMode(CameraController.Mode.CAPTURE);
        this.cameraController.start();
        bntCapture.setOnClickListener(v -> onTakeClicked());
        bntClear.setOnClickListener(v -> clearCalibrationImages());
        this.btnCalibrate.setOnClickListener(v -> onCalibrateClicked());
    }

    /**
     * Returns the directory where calibration images should be stored. The directory is created if it does not exist.
     * @return The directory for storing calibration images.
     */
    private File getCalibrationImagesDir(){
        File picturesRoot = this.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        File calibrationDir = new File(picturesRoot, "intrinsics_images");
        if (!calibrationDir.exists() && !calibrationDir.mkdirs()) {
            throw new RuntimeException("Could not create dir: " + calibrationDir.getAbsolutePath());
        }
        return calibrationDir;
    }

    /**
     * Deletes the calibration images folder and all its contents.
     */
    private void deleteCalibrationFolder(){
        File dir = this.getCalibrationImagesDir();
        if(dir.exists())
        {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        Log.e(TAG, "Could not delete file: " + file.getAbsolutePath());
                    }
                }
            }
            if (!dir.delete()) {
                Log.e(TAG, "Could not delete calibration directory: " + dir.getAbsolutePath());
            }
        }
    }

    /**
     * Clears all saved calibration images from the storage and resets the saved image count and UI elements.
     */
    private void clearCalibrationImages(){
        File dir = this.getCalibrationImagesDir();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.delete()) {
                    Log.e(TAG, "Could not delete file: " + file.getAbsolutePath());
                }
            }
        }
        this.savedImageCount = 0;
        this.runOnUiThread(() -> {
            tvLabel.setText(savedImageCount + " / " + REQUIRED_IMAGE_COUNT);
            pbCalibration.setProgress(0);
            btnCalibrate.setEnabled(false);
        });
    }

    /**
     * On Destroy Activity lifecycle event
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.cameraController.stop();
        this.calibrationExecutor.shutdown();
        this.deleteCalibrationFolder();
    }

    /**
     * Handles the take picture button click event. Captures an image and saves it to the calibration images directory.
     */
    private void onTakeClicked(){
        File dir = this.getCalibrationImagesDir();
        File file = new File(dir, "img_" + System.currentTimeMillis() + ".jpg");
        this.cameraController.takePicture(file, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                ++savedImageCount;
                runOnUiThread(() -> {
                    tvLabel.setText(savedImageCount + " / " + REQUIRED_IMAGE_COUNT);
                    pbCalibration.setProgress((int) ((savedImageCount / (float) REQUIRED_IMAGE_COUNT) * 100));
                    if (savedImageCount >= REQUIRED_IMAGE_COUNT) {
                        btnCalibrate.setEnabled(true);
                    }
                });
            }
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Error saving image: " + exception.getMessage());
            }
        });
    }

    /**
     * Starts the calibration process using the saved images. Runs on a background thread,
     * shows a progress dialog while running, then displays the result and saves it to the database.
     */
    private void onCalibrateClicked() {
        btnCalibrate.setEnabled(false);

        // Read UI values on the main thread before launching background work
        int tmpRows = 0;
        try { tmpRows =Integer.parseInt(etChessRows.getText().toString()); }
        catch (NumberFormatException ignored) {}
        int tmpCols = 0;
        try { tmpCols = Integer.parseInt(etChessCols.getText().toString()); }
        catch (NumberFormatException ignored) {}
        int tmpSquareSize = 0;
        try { tmpSquareSize = Integer.parseInt(etSquareSize.getText().toString()); }
        catch (NumberFormatException ignored) {}

        final int chessRows = tmpRows;
        final int chessCols = tmpCols;
        final int squareSize = tmpSquareSize;

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.calibration_in_progress_title)
                .setMessage(R.string.calibration_in_progress_message)
                .setCancelable(false)
                .create();
        progressDialog.show();

        calibrationExecutor.execute(() -> {
            try {
                File imagesDir = getCalibrationImagesDir();
                ChessboardDatasetLoader loader = new ChessboardDatasetLoader();
                ChessboardDatasetLoader.Dataset dataset = loader.loadAndDectect(
                        imagesDir, chessRows, chessCols, squareSize);

                Log.d("Calibration", "Loaded dataset with " + dataset.imagePoints.size() + " valid images for calibration.");

                CalibrationRunner.Result calibResult = CalibrationRunner.Result.calibrate(
                        dataset.imagePoints, dataset.objectsPoints, dataset.imageSize);

                CalibrationResult dbResult = toCalibrationResult(
                        calibResult);
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

        CalibrationResult result = new CalibrationResult(fx, fy, cx, cy, k1, k2, p1, p2, k3,
                r.reprojectionError, System.currentTimeMillis());
        return result;
    }

    /**
     * Processes each live camera frame: detects chessboard corners and draws them on the preview overlay.
     * This allows the user to visually confirm whether the chessboard pattern is correctly detected before capturing.
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
            Calib3d.drawChessboardCorners(frame, patternSize, corners, true);
            overlayBmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(frame, overlayBmp);
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
