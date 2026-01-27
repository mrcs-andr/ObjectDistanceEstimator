package com.mrcs.andr.objectdistanceestimatorapp.interpreter;

public interface ModelObserver {

    /**
     * Callback when the model is loaded successfully
     */
    void onModelLoaded(String modelInfo);

}
