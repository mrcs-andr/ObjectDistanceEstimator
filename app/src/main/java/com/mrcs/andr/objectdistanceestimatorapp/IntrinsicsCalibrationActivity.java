package com.mrcs.andr.objectdistanceestimatorapp;

import android.os.Bundle;
import android.os.FileUtils;
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
        PreviewView previewView = findViewById(R.id.intrinsicsPreviewView);
        Button bntCapture = findViewById(R.id.buttonTakePicture);
        Button bntClear = findViewById(R.id.buttonClearPictures);
        this.tvLabel = findViewById(R.id.tvCount);
        this.pbCalibration = findViewById(R.id.progressCalibration);
        this.cameraController = new CameraController(this, this, null, previewView);
        this.cameraController.setMode(CameraController.Mode.CAPTURE);
        this.cameraController.start();
        bntCapture.setOnClickListener(v -> onTakeClicked());
        bntClear.setOnClickListener(v -> clearCalibrationImages());
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
        });
    }

    /**
     * On Destroy Activity lifecycle event
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.cameraController.stop();
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
                });
            }
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Error saving image: " + exception.getMessage());
            }
        });
    }


}
