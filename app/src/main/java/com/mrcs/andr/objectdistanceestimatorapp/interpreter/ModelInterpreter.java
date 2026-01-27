package com.mrcs.andr.objectdistanceestimatorapp.interpreter;

import java.util.List;

/**
 * Interface for AI Model Interpreter
 */
public interface ModelInterpreter {

    /**
     * Loads an exported AI model
     * @param modelPath Path of the model to be loaded
     * @throws Exception Model not found or incompatible.
     */
    void loadModel(String modelPath) throws Exception;

    /**
     * Run the loaded model over an input
     * @param input data to run the model as byte[]
     * @return result as float[]
     */
    float[] runInference(byte[] input);

    /**
     * Run the loaded model over an input
     * @param input data to run the model as float[]
     * @return result as float[]
     */
    float[] runInference(float[] input);

    /**
     * Get the input shape of the model
     * @return int[] representing the shape of intput
     */
    int[] getInputShape();

    /**
     * Get the output shape of the model
     * @return  int[] representing the shape of output
     */
    int[] getOutputShape();

    /**
     * Clean the resource of the model
     */
    void close();

    /**
     * Set observers to the model interpreter
     * @param observers List of ModelObserver
     */
    void setModelObservers(List<ModelObserver> observers);
}
