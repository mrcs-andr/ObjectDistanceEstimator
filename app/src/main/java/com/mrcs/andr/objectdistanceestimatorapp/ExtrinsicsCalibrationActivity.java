package com.mrcs.andr.objectdistanceestimatorapp;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.mrcs.andr.objectdistanceestimatorapp.calibration.ArucoMarkerDetector;
import com.mrcs.andr.objectdistanceestimatorapp.calibration.CalibrationDatabase;
import com.mrcs.andr.objectdistanceestimatorapp.calibration.CalibrationResult;
import com.mrcs.andr.objectdistanceestimatorapp.calibration.ExtrinsicsCalibrationResult;
import com.mrcs.andr.objectdistanceestimatorapp.camera.CameraController;
import com.mrcs.andr.objectdistanceestimatorapp.camera.IFrameAvailableListener;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Activity for calibrating the camera extrinsics (height and pitch) using ArUco markers.
 *
 * <p>Place a printed ArUco marker (from the {@code DICT_4X4_50} dictionary) flat on the
 * ground, enter the physical side length of the marker, then point the camera at it.
 * The activity continuously estimates the camera pose and shows the result on screen.
 * Press <em>Save</em> to persist the latest estimate to the database.</p>
 *
 * <p>Requires that intrinsics calibration has already been performed (a {@link CalibrationResult}
 * must exist in the database).</p>
 */
public class ExtrinsicsCalibrationActivity extends AppCompatActivity
        implements IFrameAvailableListener {

    private CameraController cameraController;
    private ArucoMarkerDetector arucoDetector;
    private CalibrationResult calibration;

    private TextView tvPoseStatus;
    private EditText etMarkerSize;
    private Button btnSavePose;

    /** Latest successfully estimated pose, or {@code null} if none. */
    private final AtomicReference<ArucoMarkerDetector.PoseResult> latestPose =
            new AtomicReference<>(null);

    /** Marker side length in metres, updated via TextWatcher on the main thread. */
    private volatile double markerSizeM = 0.15;

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extrinsics_calibration);

        PreviewView previewView = findViewById(R.id.extrinsicsPreviewView);
        this.tvPoseStatus = findViewById(R.id.tvPoseStatus);
        this.etMarkerSize = findViewById(R.id.etMarkerSize);
        this.btnSavePose = findViewById(R.id.btnSavePose);

        this.arucoDetector = new ArucoMarkerDetector();
        this.cameraController = new CameraController(this, this, this, previewView);
        this.cameraController.setMode(CameraController.Mode.ANALYSIS);

        btnSavePose.setOnClickListener(v -> onSaveClicked());

        etMarkerSize.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    double val = Double.parseDouble(s.toString());
                    if (val > 0) markerSizeM = val;
                } catch (NumberFormatException ignored) {}
            }
        });

        // Load intrinsics calibration from the database before starting the camera
        dbExecutor.execute(() -> {
            CalibrationResult cal = CalibrationDatabase.getInstance(getApplicationContext())
                    .calibrationDao().getLatest();
            runOnUiThread(() -> {
                if (cal == null) {
                    tvPoseStatus.setText(R.string.extrinsics_no_intrinsics);
                } else {
                    this.calibration = cal;
                    this.cameraController.start();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.cameraController.stop();
        this.dbExecutor.shutdown();
    }

    /**
     * Processes each camera frame: converts it to a {@link Mat}, runs ArUco detection,
     * and updates the UI with the latest estimated pose.
     */
    @Override
    public void onFrameAvailable(Bitmap bmp) {
        CalibrationResult cal = this.calibration;
        if (cal == null) {
            return;
        }

        double markerSize = this.markerSizeM;
        if (markerSize <= 0) {
            return;
        }

        Mat frame = new Mat();
        Utils.bitmapToMat(bmp, frame);

        ArucoMarkerDetector.PoseResult pose;
        try {
            pose = arucoDetector.detectAndEstimatePose(frame, markerSize, cal);
        } finally {
            frame.release();
        }

        if (pose != null) {
            latestPose.set(pose);
            final ArucoMarkerDetector.PoseResult finalPose = pose;
            runOnUiThread(() -> {
                tvPoseStatus.setText(getString(
                        R.string.extrinsics_status_detected,
                        finalPose.markerId,
                        finalPose.cameraHeight,
                        finalPose.cameraPitch));
                btnSavePose.setEnabled(true);
            });
        } else {
            runOnUiThread(() -> {
                tvPoseStatus.setText(R.string.extrinsics_status_searching);
                btnSavePose.setEnabled(false);
            });
        }
    }

    /**
     * Saves the latest pose estimate to the database.
     */
    private void onSaveClicked() {
        ArucoMarkerDetector.PoseResult pose = latestPose.get();
        if (pose == null) {
            Toast.makeText(this, R.string.extrinsics_no_pose, Toast.LENGTH_SHORT).show();
            return;
        }

        ExtrinsicsCalibrationResult result = new ExtrinsicsCalibrationResult(
                pose.cameraHeight, pose.cameraPitch, System.currentTimeMillis());

        dbExecutor.execute(() -> {
            CalibrationDatabase.getInstance(getApplicationContext())
                    .extrinsicsCalibrationDao().insert(result);
            runOnUiThread(() -> Toast.makeText(
                    this, R.string.extrinsics_saved, Toast.LENGTH_SHORT).show());
        });
    }
}
