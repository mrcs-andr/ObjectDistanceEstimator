package com.mrcs.andr.objectdistanceestimatorapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mrcs.andr.objectdistanceestimatorapp.camera.CameraController;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.ModelInterpreter;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.ModelObserver;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.TFLiteInterpreter;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.IDetectionUpdated;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.ILetterBoxObserver;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.ImageProcessor;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.LetterBoxParams;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.TFLitePreProcessor;
import com.mrcs.andr.objectdistanceestimatorapp.ui.DetectionOverlayView;

import org.opencv.android.OpenCVLoader;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements IDetectionUpdated, ModelObserver, ILetterBoxObserver {

    private CameraController cameraController;
    private ModelManager modelManager;
    private DetectionOverlayView detectionOverlayView;
    private TextView tvLog;
    private TextView tvHud;
    private boolean isStarted = false;
    private int frameCount = 0;
    private long windowStartNs = 0L;
    private float fps = 0f;
    private PreviewView previewView;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted && isStarted) {
                            ensureModelAndStartCamera();
                        }
                    });

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
        TextView tvHud = findViewById(R.id.tvHud);
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

    @Override
    protected void onStart() {
        super.onStart();
        isStarted = true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            ensureModelAndStartCamera();
        }
    }

    private void ensureModelAndStartCamera() {
        if (modelManager == null) {
            createModelManager();

            cameraController = new CameraController(this, this, previewView,
                    this.modelManager);
            previewView.post(()->this.detectionOverlayView.setFromPreviewView(previewView));
        }
        cameraController.start();
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(cameraController != null) {
            cameraController.stop();
        }
        if(this.modelManager != null){
            this.modelManager.close();
        }
    }

    private void createModelManager(){
        try{
            ImageProcessor preProcessor = new TFLitePreProcessor(this);
            ModelInterpreter modelInterpreter = new TFLiteInterpreter(this);
            modelInterpreter.setModelObservers(List.of(this));
            this.modelManager = new ModelManager(modelInterpreter, preProcessor,
                    this);
            this.modelManager.loadModel("yolo11_kitti_float16.tflite");
            Log.i("MainActivity", "Model Manager loaded successfully");
        } catch (Exception ex){
            Log.e("MainActivity", ex.getMessage(), ex);
        }
    }

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

    @Override
    public void onModelLoaded(String modelInfo) {
        this.runOnUiThread(() -> this.tvLog.setText(modelInfo));
    }

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