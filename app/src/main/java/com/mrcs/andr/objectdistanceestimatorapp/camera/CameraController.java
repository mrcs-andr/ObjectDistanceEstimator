package com.mrcs.andr.objectdistanceestimatorapp.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraController {

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final IFrameAvailableListener frameAvailableListener;

    public CameraController(Context context, LifecycleOwner lifecycleOwner,
                            IFrameAvailableListener frameAvailableListener,
                            PreviewView previewView) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
        this.frameAvailableListener = frameAvailableListener;
    }

    public void start() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider =
                        cameraProviderFuture.get();
                if(cameraProvider == null){
                    Log.e("CameraController", "Could not get camera provider!");
                    return;
                }
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider()
                );
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalyser(this.frameAvailableListener));
                CameraSelector cameraSelector =
                        CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );
            } catch (Exception e) {
                Log.e("CameraController", e.getMessage(), e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void stop() {
        cameraExecutor.shutdown();
    }

    private static class ImageAnalyser implements ImageAnalysis.Analyzer{

        private final IFrameAvailableListener frameAvailableListener;

        public ImageAnalyser(IFrameAvailableListener frameAvailableListener)
        {
            this.frameAvailableListener = frameAvailableListener;
        }

        public static Bitmap imageProxyToBitmap(ImageProxy image) {
            int width = image.getWidth();
            int height = image.getHeight();

            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            // NV21 layout: Y + V + U
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            Mat yuv = new Mat(height + height / 2, width, CvType.CV_8UC1);
            yuv.put(0, 0, nv21);

            Mat rgb = new Mat();
            Imgproc.cvtColor(yuv, rgb, Imgproc.COLOR_YUV2RGB_NV21);

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rgb, bitmap);

            yuv.release();
            rgb.release();
            return bitmap;
        }

        @Override
        public void analyze(@NonNull ImageProxy image) {
            try (image) {
                Bitmap bitmap = imageProxyToBitmap(image);

                int rotation = image.getImageInfo().getRotationDegrees();
                if (rotation != 0) {
                    Matrix m = new Matrix();
                    m.postRotate(rotation);
                    bitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true
                    );
                }
                frameAvailableListener.onFrameAvailable(bitmap);
            } catch (Exception e) {
                Log.e("ImageAnalyser", "Error processing image: " + e.getMessage(), e);
            }
        }
    }
}
