package com.mrcs.andr.objectdistanceestimatorapp.preprocessing;

public interface ILetterBoxObserver {
    /**
     * Callback method invoked when letterbox parameters are computed.
     * @param params LetterBoxParams containing scale and padding information
     */
    void onLetterBoxComputed(LetterBoxParams params);
}
