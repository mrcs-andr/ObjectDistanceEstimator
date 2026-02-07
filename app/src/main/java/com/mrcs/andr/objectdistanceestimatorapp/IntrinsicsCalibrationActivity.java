package com.mrcs.andr.objectdistanceestimatorapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.view.PreviewView;

import com.mrcs.andr.objectdistanceestimatorapp.camera.CameraController;

import java.io.File;

public class IntrinsicsCalibrationActivity extends AppCompatActivity {

    private static final String TAG = "IntrinsicsCalibration";
    private static final int REQUIRED_IMAGE_COUNT = 20;
    private int savedImageCount = 0;
    private PreviewView previewView;
    private Button bntCapture;
    private Button bntClear;
    private TextView tvLabel;
    private ProgressBar pbCalibration;
    private CameraController cameraController;

    /**
     * On Create method for the IntrinsicsCalibrationActivity. Sets the content view to the activity_intrinsics_calibration layout.
     * @param savedInstanceState The saved instance state bundle, if any.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intrinsics_calibration);
        this.previewView = findViewById(R.id.intrinsicsPreviewView);
        this.bntCapture = findViewById(R.id.buttonTakePicture);
        this.bntClear = findViewById(R.id.buttonClearPictures);
        this.tvLabel = findViewById(R.id.tvCount);
        this.pbCalibration = findViewById(R.id.progressCalibration);
        this.cameraController = new CameraController(this, this, null, previewView);
        this.cameraController.setMode(CameraController.Mode.CAPTURE);
        this.cameraController.start();
        this.bntCapture.setOnClickListener(v -> onTakeClicked());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.cameraController.stop();
    }

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
                });
            }
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Error saving image: " + exception.getMessage());
            }
        });
    }


}
