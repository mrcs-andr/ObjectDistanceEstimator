package com.mrcs.andr.objectdistanceestimatorapp;


import android.graphics.Bitmap;
import android.util.Log;

import com.mrcs.andr.objectdistanceestimatorapp.camera.IFrameAvailableListener;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.ModelInterpreter;
import com.mrcs.andr.objectdistanceestimatorapp.pipeline.ProcessingChain;
import com.mrcs.andr.objectdistanceestimatorapp.pipeline.ProcessingNode;
import com.mrcs.andr.objectdistanceestimatorapp.pipeline.ProcessingStage;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.IDetectionUpdated;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.YoloDecoder;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.ImageProcessor;
import com.mrcs.andr.objectdistanceestimatorapp.tracking.IObjectTracker;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Model Manager class to handle model loading, inference and post-processing
 */
public class ModelManager implements IFrameAvailableListener {
    private final ModelInterpreter interpreter;
    private final ImageProcessor preProcessor;
    private final IDetectionUpdated detectionUpdated;
    private final YoloDecoder yoloDecoder;
    private final IObjectTracker tracker;
    private final ProcessingChain<Bitmap, List<Detection>> processingChain;
    private final int inputSize;
    private final Executor detectionExecutor;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicReference<Bitmap> pendingFrame = new AtomicReference<>();
    private final Object frameLock = new Object();

    /**
     * Constructor
     * @param interpreter Model Interpreter implementation
     * @param preProcessor Image Pre-processor implementation
     * @param detectionUpdated Detection update callback implementation
     */
    public ModelManager(ModelInterpreter interpreter, ImageProcessor preProcessor, IDetectionUpdated detectionUpdated) {
        this(interpreter, preProcessor, detectionUpdated, null, null, 512, null, null, null, null);
    }

    public ModelManager(ModelInterpreter interpreter, ImageProcessor preProcessor, IDetectionUpdated detectionUpdated,
                        ProcessingChain<Bitmap, List<Detection>> processingChain, int inputSize,
                        Executor preProcessExecutor, Executor inferenceExecutor,
                        Executor postProcessExecutor, Executor detectionExecutor) {
        this(interpreter, preProcessor, detectionUpdated, null, processingChain, inputSize,
                preProcessExecutor, inferenceExecutor, postProcessExecutor, detectionExecutor);
    }

    public ModelManager(ModelInterpreter interpreter, ImageProcessor preProcessor, IDetectionUpdated detectionUpdated,
                        IObjectTracker tracker,
                        ProcessingChain<Bitmap, List<Detection>> processingChain, int inputSize,
                        Executor preProcessExecutor, Executor inferenceExecutor,
                        Executor postProcessExecutor, Executor detectionExecutor) {
        this.interpreter = interpreter;
        this.preProcessor = preProcessor;
        this.detectionUpdated = detectionUpdated;
        this.yoloDecoder = new YoloDecoder(12, 5376, 0.5f, 0.4f);
        this.tracker = tracker;
        this.inputSize = inputSize > 0 ? inputSize : 512;
        this.processingChain = processingChain == null
                ? defaultChain(preProcessExecutor, inferenceExecutor, postProcessExecutor)
                : processingChain;
        this.detectionExecutor = detectionExecutor;
    }

    /**
     * Load an AI model
     * @param modelPath Path of the model to be loaded
     * @throws Exception Model not found or incompatible.
     */
    public void loadModel(String modelPath) throws Exception {
        interpreter.loadModel(modelPath);
    }

    /**
     * Clean the resource of the model
     * */
   public void close() {
       interpreter.close();
   }

    /**
     * Run the model on a bitmap
     * @param bmp Input Bitmap
     * @return the inference result as float[]
     */
    private float[] runModelOnBitmap(Bitmap bmp) {
        float[] processedData = this.preProcessor.preprocessBitmap(bmp, inputSize);
        return interpreter.runInference(processedData);
    }

    private ProcessingChain<Bitmap, List<Detection>> defaultChain(Executor preProcessExecutor,
                                                                  Executor inferenceExecutor,
                                                                  Executor postProcessExecutor) {

        ProcessingNode<Bitmap, float[]> preProcessNode = input -> this.preProcessor.preprocessBitmap(input, inputSize);
        ProcessingNode<float[], float[]> inferenceNode = this.interpreter::runInference;
        ProcessingNode<float[], List<Detection>> postProcessNode = data -> this.yoloDecoder.decode(data, inputSize);

        List<ProcessingStage<?, ?>> stages = new java.util.ArrayList<>(Arrays.asList(
                new ProcessingStage<>(preProcessNode, preProcessExecutor),
                new ProcessingStage<>(inferenceNode, inferenceExecutor),
                new ProcessingStage<>(postProcessNode, postProcessExecutor)));

        if (tracker != null) {
            ProcessingNode<List<Detection>, List<Detection>> trackingNode = tracker::update;
            stages.add(new ProcessingStage<>(trackingNode, postProcessExecutor));
        }

        return new ProcessingChain<>(stages);
    }

    /**
     * Callback method when a new frame is available
     * @param bmp Bitmap of the available frame
     * @throws IOException if an error occurs during processing
     */
    @Override
    public void onFrameAvailable(Bitmap bmp) throws IOException {
        synchronized (frameLock) {
            if (isProcessing.get()) {
                Bitmap dropped = pendingFrame.getAndSet(bmp);
                if (dropped != null) {
                    Log.d("ModelManager", "Dropping older pending frame");
                }
                return;
            }
            isProcessing.set(true);
        }
        startProcessing(bmp);
    }

    private void startProcessing(Bitmap bmp) {
        this.processingChain.processAsync(bmp)
                .thenAcceptAsync(this.detectionUpdated::onDetectionUpdated,
                        detectionExecutor == null ? command -> command.run() : detectionExecutor)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        Log.e("ModelManager", "Error processing frame", ex);
                    }
                    Bitmap next;
                    synchronized (frameLock) {
                        next = pendingFrame.getAndSet(null);
                        if (next == null) {
                            isProcessing.set(false);
                        }
                    }
                    if (next != null) {
                        startProcessing(next);
                    }
                });
    }
}
