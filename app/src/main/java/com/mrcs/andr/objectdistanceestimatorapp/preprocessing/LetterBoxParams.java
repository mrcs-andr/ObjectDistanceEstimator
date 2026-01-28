package com.mrcs.andr.objectdistanceestimatorapp.preprocessing;

/**
 * Class to hold letterbox parameters
 */
public class LetterBoxParams{
    public  final float scale;
    public  final float padX;
    public  final float padY;
    public final int analysisImageWidth;
    public final int analysisImageHeight;

    /**
     * Constructor to initialize letterbox parameters
     * @param scale scaling factor applied during letterboxing
     * @param padX padding added on the X axis
     * @param padY padding added on the Y axis
     */
    public LetterBoxParams(float scale, float padX, float padY, int analysisImageWidth, int analysisImageHeight){
        this.analysisImageWidth = analysisImageWidth;
        this.analysisImageHeight = analysisImageHeight;
        this.scale = scale;
        this.padX = padX;
        this.padY = padY;
    }
}
