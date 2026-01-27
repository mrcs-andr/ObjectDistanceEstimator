package com.mrcs.andr.objectdistanceestimatorapp.preprocessing;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class OpenCvByteArrayConverter {

    /**
     * Converte um byte[] YUV (NV21) para Bitmap RGB.
     *
     * @param yuvBytes  Array de bytes que representa a imagem no formato NV21 (YUV).
     * @param width     Largura da imagem.
     * @param height    Altura da imagem.
     * @return Bitmap RGB convertido do YUV.
     */
    public static Bitmap convertYuvByteArrayToBitmap(byte[] yuvBytes, int width, int height) {
        try {
            Mat yuvMat = new Mat(height + height / 2, width, CvType.CV_8UC1); // YUV 420 tem 1.5 * altura
            yuvMat.put(0, 0, yuvBytes);

            Mat rgbMat = new Mat(height, width, CvType.CV_8UC3);

            Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21);

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rgbMat, bitmap);

            yuvMat.release();
            rgbMat.release();

            return bitmap;

        } catch (Exception e) {
            Log.e("OpenCvConverter", "Erro ao converter byte[] YUV para Bitmap!", e);
            return null;
        }
    }
}