package com.mrcs.andr.objectdistanceestimatorapp;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import com.mrcs.andr.objectdistanceestimatorapp.camera.CameraController;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.ModelInterpreter;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.ModelObserver;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.TFLiteInterpreter;
import com.mrcs.andr.objectdistanceestimatorapp.pipeline.ProcessingChain;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.IDetectionUpdated;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.ILetterBoxObserver;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.ImageProcessor;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.TFLitePreProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppContainer {

    public ModelManager modelManager;
    public CameraController cameraController;
    private final List<ExecutorService> managedExecutors = new ArrayList<>();

    public AppContainer(ILetterBoxObserver letterBoxObserver, Context context,
                        LifecycleOwner lifecycleOwner,
                        IDetectionUpdated detectionUpdated, PreviewView previewView,
                        ModelObserver modelObserver,
                        ProcessingChain<Bitmap, List<Detection>> processingChain,
                        ExecutorService preProcessExecutor,
                        ExecutorService inferenceExecutor,
                        ExecutorService postProcessExecutor,
                        ExecutorService detectionExecutor) throws Exception {
        this.createModelManager(letterBoxObserver, context, detectionUpdated, modelObserver,
                processingChain, preProcessExecutor, inferenceExecutor, postProcessExecutor, detectionExecutor);
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
        for (ExecutorService executor : managedExecutors) {
            executor.shutdown();
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
                                    IDetectionUpdated detectionUpdated, ModelObserver modelObserver,
                                    ProcessingChain<Bitmap, List<Detection>> processingChain,
                                    ExecutorService preProcessExecutor,
                                    ExecutorService inferenceExecutor,
                                    ExecutorService postProcessExecutor,
                                    ExecutorService detectionExecutor) throws Exception {
        ImageProcessor preProcessor = new TFLitePreProcessor(letterBoxObserver);
        ModelInterpreter modelInterpreter = new TFLiteInterpreter(context);
        List<ModelObserver> observers = new ArrayList<>();
        observers.add(modelObserver);
        modelInterpreter.setModelObservers(observers);
        ExecutorService resolvedPreProcess = preProcessExecutor;
        ExecutorService resolvedInference = inferenceExecutor;
        ExecutorService resolvedPostProcess = postProcessExecutor;
        ExecutorService resolvedDetection = detectionExecutor;
        if (processingChain == null) {
            resolvedPreProcess = resolveExecutor(preProcessExecutor);
            resolvedInference = resolveExecutor(inferenceExecutor);
            resolvedPostProcess = resolveExecutor(postProcessExecutor);
        }
        if (resolvedDetection == null) {
            resolvedDetection = resolvedPostProcess;
        }
        this.modelManager = new ModelManager(modelInterpreter, preProcessor, detectionUpdated,
                processingChain, 512, resolvedPreProcess, resolvedInference, resolvedPostProcess, resolvedDetection);
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

    private ExecutorService resolveExecutor(ExecutorService executor) {
        if (executor != null) {
            return executor;
        }
        ExecutorService created = Executors.newSingleThreadExecutor();
        managedExecutors.add(created);
        return created;
    }

    public static class Builder {
        private ILetterBoxObserver letterBoxObserver;
        private Context context;
        private IDetectionUpdated detectionUpdated;
        private PreviewView previewView;
        private ModelObserver modelObserver;
        private LifecycleOwner lifecycleOwner;
        private ProcessingChain<Bitmap, List<Detection>> processingChain;
        private ExecutorService preProcessExecutor;
        private ExecutorService inferenceExecutor;
        private ExecutorService postProcessExecutor;
        private ExecutorService detectionExecutor;

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

        public Builder setProcessingChain(
                ProcessingChain<Bitmap, List<Detection>> processingChain) {
            this.processingChain = processingChain;
            return this;
        }

        public Builder setProcessingExecutors(ExecutorService preProcessExecutor,
                                              ExecutorService inferenceExecutor,
                                              ExecutorService postProcessExecutor,
                                              ExecutorService detectionExecutor) {
            this.preProcessExecutor = preProcessExecutor;
            this.inferenceExecutor = inferenceExecutor;
            this.postProcessExecutor = postProcessExecutor;
            this.detectionExecutor = detectionExecutor;
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
                    previewView, modelObserver, processingChain,
                    preProcessExecutor, inferenceExecutor, postProcessExecutor, detectionExecutor);
        }
    }


}
