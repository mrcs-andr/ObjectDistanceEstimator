package com.mrcs.andr.objectdistanceestimatorapp.camera;

import android.graphics.Bitmap;

import java.io.IOException;

/**
 * Listener interface for receiving available frames from the camera
 */
public interface IFrameAvailableListener {

    /**
     * Callback method when a new frame is available
     * @param bmp Bitmap of the available frame
     * @throws IOException if an error occurs during processing
     */
    void onFrameAvailable(Bitmap bmp) throws IOException;
}
