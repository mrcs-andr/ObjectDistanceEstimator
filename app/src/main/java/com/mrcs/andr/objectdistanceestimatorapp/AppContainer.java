package com.mrcs.andr.objectdistanceestimatorapp;

import android.content.Context;

import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import com.mrcs.andr.objectdistanceestimatorapp.camera.CameraController;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.ModelInterpreter;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.ModelObserver;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.TFLiteInterpreter;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.IDetectionUpdated;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.ILetterBoxObserver;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.ImageProcessor;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.TFLitePreProcessor;

import java.util.ArrayList;
import java.util.List;

public class AppContainer {

    public ModelManager modelManager;
    public CameraController cameraController;

    public AppContainer(ILetterBoxObserver letterBoxObserver, Context context,
                        LifecycleOwner lifecycleOwner,
                        IDetectionUpdated detectionUpdated, PreviewView previewView,
                        ModelObserver modelObserver) throws Exception {
        this.createModelManager(letterBoxObserver, context, detectionUpdated, modelObserver);
        this.createCameraController(context, lifecycleOwner, previewView);
    }

    /**
     * Get Model Manager instance
     * @return ModelManager instance
     */
    public ModelManager getModelManager() {
        return modelManager;
    }

    /**
     * Get Camera Controller instance
     * @return CameraController instance
     */
    public CameraController getCameraController() {
        return cameraController;
    }

    /**
     * Destroy resources
     */
    public void destroy() {
        if (cameraController != null) {
            cameraController.stop();
        }
        if (modelManager != null) {
            modelManager.close();
        }
    }

    /**
     * Create Model Manager instance
     * @param letterBoxObserver observer for letterbox params
     * @param context application context
     * @param detectionUpdated detection updated callback
     * @throws Exception if model loading fails
     */
    private void createModelManager(ILetterBoxObserver letterBoxObserver, Context context,
                                    IDetectionUpdated detectionUpdated, ModelObserver modelObserver) throws Exception {
        ImageProcessor preProcessor = new TFLitePreProcessor(letterBoxObserver);
        ModelInterpreter modelInterpreter = new TFLiteInterpreter(context);
        List<ModelObserver> observers = new ArrayList<>();
        observers.add(modelObserver);
        modelInterpreter.setModelObservers(observers);
        this.modelManager = new ModelManager(modelInterpreter, preProcessor, detectionUpdated);
        this.modelManager.loadModel("yolo11_kitti_float16.tflite");
    }

    /**
     * Create Camera Controller instance
     * @param context application context
     */
    private void createCameraController(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView){
        this.cameraController = new CameraController(context,
                lifecycleOwner, this.modelManager,previewView);
    }

    public static class Builder {
        private ILetterBoxObserver letterBoxObserver;
        private Context context;
        private IDetectionUpdated detectionUpdated;
        private PreviewView previewView;
        private ModelObserver modelObserver;
        private LifecycleOwner lifecycleOwner;

        public Builder setLetterBoxObserver(ILetterBoxObserver letterBoxObserver) {
            this.letterBoxObserver = letterBoxObserver;
            return this;
        }

        public Builder setContext(Context context) {
            this.context = context;
            return this;
        }

        public Builder setDetectionUpdated(IDetectionUpdated detectionUpdated) {
            this.detectionUpdated = detectionUpdated;
            return this;
        }

        public Builder setPreviewView(PreviewView previewView) {
            this.previewView = previewView;
            return this;
        }

        public Builder setModelObserver(ModelObserver modelObserver) {
            this.modelObserver = modelObserver;
            return this;
        }

        public Builder setLifecycleOwner(LifecycleOwner lifecycleOwner) {
            this.lifecycleOwner = lifecycleOwner;
            return this;
        }

        public AppContainer build() throws Exception {
            if(letterBoxObserver == null)
                throw new IllegalArgumentException("LetterBoxObserver is required");
            if(context == null)
                throw new IllegalArgumentException("Context is required");
            if(detectionUpdated == null)
                throw new IllegalArgumentException("DetectionUpdated is required");
            if(previewView == null)
                throw new IllegalArgumentException("PreviewView is required");
            if(modelObserver == null)
                throw new IllegalArgumentException("ModelObserver is required");
            if(lifecycleOwner == null)
                throw new IllegalArgumentException("LifecycleOwner is required");
            return new AppContainer(letterBoxObserver, context, lifecycleOwner, detectionUpdated,
                    previewView, modelObserver);
        }
    }


}
