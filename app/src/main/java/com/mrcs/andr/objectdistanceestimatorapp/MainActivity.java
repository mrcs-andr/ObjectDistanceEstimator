package com.mrcs.andr.objectdistanceestimatorapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mrcs.andr.objectdistanceestimatorapp.interpreter.ModelObserver;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.IDetectionUpdated;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.ILetterBoxObserver;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.LetterBoxParams;
import com.mrcs.andr.objectdistanceestimatorapp.ui.DetectionOverlayView;

import org.opencv.android.OpenCVLoader;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements IDetectionUpdated, ModelObserver, ILetterBoxObserver {

    private DetectionOverlayView detectionOverlayView;
    private TextView tvLog;
    private TextView tvHud;
    private boolean isStarted = false;
    private int frameCount = 0;
    private long windowStartNs = 0L;
    private float fps = 0f;
    private PreviewView previewView;
    private AppContainer appContainer;

    /**
     * Camera Permission Launcher: ask for camera permission
     */
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted && isStarted) {
                            ensureModelAndStartCamera();
                        }
                    });

    /**
     * On Create Activity lifecycle event
     * @param savedInstanceState saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("MainActivity", "Supported ABIs: " + Arrays.toString(Build.SUPPORTED_ABIS));
        if (!OpenCVLoader.initLocal()) {
            Log.e("MainActivity", "Could not load OpenCV Library!");
        } else {
            Log.d("MainActivity", "OpenCV successfully loaded!");
        }

        setContentView(R.layout.activity_main);
        this.detectionOverlayView = findViewById(R.id.overlayView);
        this.tvLog = findViewById(R.id.tvLog);
        this.tvHud = findViewById(R.id.tvHud);
        this.previewView = findViewById(R.id.previewView);
        ViewCompat.setOnApplyWindowInsetsListener(tvHud, (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );
            v.setTranslationY(systemBars.top + 16);
            return insets;
        });
        requestCameraPermission();
    }

    /**
     * Inflate the menu, adds items to the action bar if it is present.
     * @param menu the options menu in which you place your items.
     * @return true for the menu to be displayed; false to suppress it.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * Handle menu item selections
     * @param item the menu item that was selected.
     * @return false to allow normal menu processing to proceed, true to consume it here.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.action_intrinsics_calibration){
            startActivity(new Intent(this, IntrinsicsCalibrationActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * On Start Activity lifecycle event
     */
    @Override
    protected void onStart() {
        super.onStart();
        isStarted = true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            ensureModelAndStartCamera();
        }
    }

    /**
     * Ensure model is loaded and start camera
     * This method instantiates the AppContainer if it is null,
     * sets up the detection overlay view, and starts the camera controller.
     */
    private void ensureModelAndStartCamera() {
        if(this.appContainer == null){
            try{
                this.appContainer = new AppContainer.Builder()
                        .setContext(this)
                        .setLetterBoxObserver(this)
                        .setModelObserver(this)
                        .setPreviewView(previewView)
                        .setDetectionUpdated(this)
                        .setLifecycleOwner(this)
                        .build();
            } catch (Exception ex){
                Log.e("MainActivity", "Could not create AppContainer", ex);
                return;
            }
            previewView.post(()->this.detectionOverlayView.setFromPreviewView(previewView));
        }
        this.appContainer.getCameraController().start();
    }

    /**
     * Request camera permission if not already granted
     */
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /**
     * On Destroy Activity lifecycle event
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(this.appContainer!= null){
            this.appContainer.destroy();
        }
    }

    /**
     * Detection updated callback, updates overlay view and FPS counter
     * @param detections list of detections returned by the model.
     */
    @Override
    public void onDetectionUpdated(List<Detection> detections) {
        this.runOnUiThread(() -> this.detectionOverlayView.setDetections(detections));

        long now = System.nanoTime();
        if (windowStartNs == 0L) {
            windowStartNs = now;
        }
        frameCount++;

        long elapsedNs = now - windowStartNs;
        if (elapsedNs >= 1_000_000_000L) {
            fps = frameCount * (1_000_000_000f / (float) elapsedNs);
            frameCount = 0;
            windowStartNs = now;
            this.runOnUiThread(() -> tvHud.setText("FPS: " + String.format("%.1f", fps)));
        }

    }

    /**
     * Model loaded callback, updates log text view when model is loaded.
     * @param modelInfo information about the loaded model.
     */
    @Override
    public void onModelLoaded(String modelInfo) {
        this.runOnUiThread(() -> this.tvLog.setText(modelInfo));
    }

    /**
     * Letterbox computed callback, updates overlay view with letterbox parameters.
     * @param params letterbox parameters, used to adjust overlay rendering.
     */
    @Override
    public void onLetterBoxComputed(LetterBoxParams params) {
        if(this.detectionOverlayView != null){
            this.runOnUiThread(() -> {
                this.detectionOverlayView.setLetterbox(params);
                this.detectionOverlayView.setFromPreviewView(previewView);
            });

        }
    }
}
