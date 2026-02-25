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
 * Activity for calibrating the camera extrinsics (full 6-DOF pose) using ArUco markers.
 *
 * <p>Place a printed ArUco marker (from the {@code DICT_4X4_50} dictionary) flat on the
 * ground, enter its physical side length and world-frame position/yaw, then point the
 * camera at it. The activity continuously estimates the full 6-DOF camera pose
 * (x, y, z, yaw, pitch, roll) and shows the result on screen. Press <em>Save Pose</em>
 * to persist the latest estimate to the database.</p>
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

    // Marker world-frame parameters – updated safely via TextWatchers on the main thread
    private volatile double markerSizeM    = 0.15;
    private volatile double markerWorldX   = 0.0;
    private volatile double markerWorldY   = 0.0;
    private volatile double markerWorldZ   = 0.0;
    private volatile double markerWorldYaw = 0.0;

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extrinsics_calibration);

        PreviewView previewView = findViewById(R.id.extrinsicsPreviewView);
        this.tvPoseStatus = findViewById(R.id.tvPoseStatus);
        this.etMarkerSize = findViewById(R.id.etMarkerSize);
        this.btnSavePose  = findViewById(R.id.btnSavePose);

        this.arucoDetector = new ArucoMarkerDetector();
        this.cameraController = new CameraController(this, this, this, previewView);
        this.cameraController.setMode(CameraController.Mode.ANALYSIS);

        btnSavePose.setOnClickListener(v -> onSaveClicked());

        bindDoubleField(etMarkerSize,              v -> markerSizeM    = v, 0.15);
        bindDoubleField(findViewById(R.id.etMarkerWorldX), v -> markerWorldX   = v, 0.0);
        bindDoubleField(findViewById(R.id.etMarkerWorldY), v -> markerWorldY   = v, 0.0);
        bindDoubleField(findViewById(R.id.etMarkerWorldZ), v -> markerWorldZ   = v, 0.0);
        bindDoubleField(findViewById(R.id.etMarkerWorldYaw), v -> markerWorldYaw = v, 0.0);

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
     * and updates the UI with the latest estimated 6-DOF pose.
     */
    @Override
    public void onFrameAvailable(Bitmap bmp) {
        CalibrationResult cal = this.calibration;
        if (cal == null) return;

        double size = this.markerSizeM;
        if (size <= 0) return;

        Mat frame = new Mat();
        Utils.bitmapToMat(bmp, frame);

        ArucoMarkerDetector.PoseResult pose;
        try {
            pose = arucoDetector.detectAndEstimatePose(
                    frame, size,
                    markerWorldX, markerWorldY, markerWorldZ, markerWorldYaw,
                    cal);
        } finally {
            frame.release();
        }

        if (pose != null) {
            latestPose.set(pose);
            final ArucoMarkerDetector.PoseResult p = pose;
            runOnUiThread(() -> {
                tvPoseStatus.setText(getString(
                        R.string.extrinsics_status_detected,
                        p.markerId,
                        p.cameraX, p.cameraY, p.cameraZ,
                        p.cameraYaw, p.cameraPitch, p.cameraRoll));
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
     * Saves the latest 6-DOF pose estimate to the database.
     */
    private void onSaveClicked() {
        ArucoMarkerDetector.PoseResult pose = latestPose.get();
        if (pose == null) {
            Toast.makeText(this, R.string.extrinsics_no_pose, Toast.LENGTH_SHORT).show();
            return;
        }

        ExtrinsicsCalibrationResult result = new ExtrinsicsCalibrationResult(
                pose.cameraX, pose.cameraY, pose.cameraZ,
                pose.cameraYaw, pose.cameraPitch, pose.cameraRoll,
                System.currentTimeMillis());

        dbExecutor.execute(() -> {
            CalibrationDatabase.getInstance(getApplicationContext())
                    .extrinsicsCalibrationDao().insert(result);
            runOnUiThread(() -> Toast.makeText(
                    this, R.string.extrinsics_saved, Toast.LENGTH_SHORT).show());
        });
    }

    /**
     * Attaches a {@link TextWatcher} to an {@link EditText} that writes the parsed double
     * value to the given consumer. Falls back to {@code defaultValue} on parse errors.
     */
    private void bindDoubleField(EditText et, DoubleConsumer consumer, double defaultValue) {
        consumer.accept(defaultValue);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    consumer.accept(Double.parseDouble(s.toString()));
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    private interface DoubleConsumer {
        void accept(double value);
    }
}
