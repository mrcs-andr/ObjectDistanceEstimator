package com.mrcs.andr.objectdistanceestimatorapp;


import android.graphics.Bitmap;

import com.mrcs.andr.objectdistanceestimatorapp.camera.IFrameAvailableListener;
import com.mrcs.andr.objectdistanceestimatorapp.interpreter.ModelInterpreter;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.Detection;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.IDetectionUpdated;
import com.mrcs.andr.objectdistanceestimatorapp.postprocessing.YoloDecoder;
import com.mrcs.andr.objectdistanceestimatorapp.preprocessing.ImageProcessor;

import java.io.IOException;
import java.util.List;

/**
 * Model Manager class to handle model loading, inference and post-processing
 */
public class ModelManager implements IFrameAvailableListener {
    private final ModelInterpreter interpreter;
    private final ImageProcessor preProcessor;
    private final IDetectionUpdated detectionUpdated;
    private final YoloDecoder yoloDecoder;

    /**
     * Constructor
     * @param interpreter Model Interpreter implementation
     * @param preProcessor Image Pre-processor implementation
     * @param detectionUpdated Detection update callback implementation
     */
    public ModelManager(ModelInterpreter interpreter, ImageProcessor preProcessor, IDetectionUpdated detectionUpdated) {
        this.interpreter = interpreter;
        this.preProcessor = preProcessor;
        this.detectionUpdated = detectionUpdated;
        //TODO: outC and outN can be read from model output shape, the confidence and iou thresholds can be parameters.
        this.yoloDecoder = new YoloDecoder(12, 5376, 0.5f, 0.4f);
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
        float[] processedData = this.preProcessor.preprocessBitmap(bmp, 512);
        return interpreter.runInference(processedData);
    }

    /**
     * Callback method when a new frame is available
     * @param bmp Bitmap of the available frame
     * @throws IOException if an error occurs during processing
     */
    @Override
    public void onFrameAvailable(Bitmap bmp) throws IOException {
        float[] inferenceData = this.runModelOnBitmap(bmp);
        //TODO: input size can be read from model input shape.
        int INPUT_SIZE = 512;
        List<Detection> detections = this.yoloDecoder.decode(inferenceData, INPUT_SIZE);
        this.detectionUpdated.onDetectionUpdated(detections);
    }
}
