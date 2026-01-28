package com.mrcs.andr.objectdistanceestimatorapp.preprocessing;

import android.graphics.Bitmap;

/**
 * Interface for Image Pre-processing
 */
public interface ImageProcessor {

    /**
     * Preprocess the input Bitmap to match the model input requirements
     *
     * @param bmp            Input Bitmap
     * @param modelInputSize Size of the model input (assumed square)
     * @return Preprocessed data as float[]
     */
    float[] preprocessBitmap(Bitmap bmp, int modelInputSize);

}
