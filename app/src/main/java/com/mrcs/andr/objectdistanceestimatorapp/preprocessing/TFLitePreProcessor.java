package com.mrcs.andr.objectdistanceestimatorapp.preprocessing;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.TensorImage;

public class TFLitePreProcessor implements ImageProcessor {

    private ILetterBoxObserver letterBoxObserver;

    public TFLitePreProcessor(ILetterBoxObserver letterBoxObserver){
        this.letterBoxObserver = letterBoxObserver;
    }

    /**
     * Letterbox to (inputWidth, inputHeight) with padding color (114,114,114).
     */
    private Bitmap letterbox(Bitmap src, int inputWidth, int inputHeight) {
        int w0 = src.getWidth();
        int h0 = src.getHeight();

        float r = Math.min((float) inputHeight / h0, (float) inputWidth / w0);
        int newW = Math.round(w0 * r);
        int newH = Math.round(h0 * r);

        float dw = (inputWidth - newW) / 2.0f;
        float dh = (inputHeight - newH) / 2.0f;

        // Resize with aspect ratio
        Bitmap resized = Bitmap.createScaledBitmap(src, newW, newH, true);

        // Create target bitmap filled with gray (114,114,114)
        Bitmap out = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.rgb(114, 114, 114));

        if(this.letterBoxObserver != null){
            LetterBoxParams params = new LetterBoxParams(r, dw, dh,w0,h0);
            this.letterBoxObserver.onLetterBoxComputed(params);
        }

        // Draw resized image centered with padding
        canvas.drawBitmap(resized, dw, dh, (Paint) null);

        return out;
    }

    /**
     * Preprocess the input Bitmap to match the model input requirements
     * @param bmp            Input Bitmap
     * @param modelInputSize Size of the model input (assumed square)
     * @return Preprocessed data as float[]
     */
    @Override
    public float[] preprocessBitmap(Bitmap bmp, int modelInputSize) {
        //2 - Letterbox the bitmap to model input size
        Bitmap letterboxed = letterbox(bmp, modelInputSize, modelInputSize);
        //2 - Create a tensor image from bitmap
        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(letterboxed);
        //3 - Apply the pre-process.
        org.tensorflow.lite.support.image.ImageProcessor imageProcessor =
                new org.tensorflow.lite.support.image.ImageProcessor.Builder()
                        .add(new NormalizeOp(0.0f, 255.0f))
                        .build();
        //4 - Process the image and return the float array.
        tensorImage = imageProcessor.process(tensorImage);
        return tensorImage.getTensorBuffer().getFloatArray();
    }
}
