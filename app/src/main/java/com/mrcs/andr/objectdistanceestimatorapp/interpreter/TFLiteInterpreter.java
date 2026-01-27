package com.mrcs.andr.objectdistanceestimatorapp.interpreter;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TensorFlow Lite Model Interpreter implementation
 */
public class TFLiteInterpreter implements  ModelInterpreter{
    private final Context context;
    private Interpreter tfLite;
    private final float[] deqLut = new float[256];
    private Tensor inputTensor;
    private ByteBuffer inputBuffer;
    private ByteBuffer outputBuffer;
    private byte[] outRaw;
    private float[] outDeq;
    private boolean isQuantized;
    private List<ModelObserver> modelObservers;

    /**
     * Initialize Dequantization Lookup Table used on quantized models
     * @param scale quantization scale
     * @param zeroPoint quantization zero point
     */
    private void initDeqLut(float scale, int zeroPoint) {
        for (int u = 0; u < 256; u++) {
            deqLut[u] = scale * (u - zeroPoint);
        }
    }

    /**
     * Constructor
     * @param context Android Context
     */
    public TFLiteInterpreter(Context context){
        this.context = context;
        this.isQuantized = false;
        this.modelObservers = new ArrayList<>();
    }

    /**
     * Load an exported TFLite model from assets, check the input and output tensor info and,
     * also determine if the model is quantized or not.
     * @param modelPath Path of the model to be loaded
     * @throws Exception Model not found or incompatible.
     */
    @Override
    public void loadModel(String modelPath) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream fileInputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY,
                fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
        this.tfLite= new Interpreter(mappedByteBuffer);
        this.logModelInfo();

        Tensor.QuantizationParams q = this.tfLite.getOutputTensor(0).quantizationParams();
        float scale = q.getScale();
        int zeroPoint = q.getZeroPoint();
        DataType type =  this.tfLite.getOutputTensor(0).dataType();

        this.isQuantized = (type == DataType.UINT8 || type == DataType.INT8);
        if(this.isQuantized) {
            this.initDeqLut(scale, zeroPoint);
        }

        this.inputTensor = this.tfLite.getInputTensor(0);
        Tensor outputTensor = this.tfLite.getOutputTensor(0);

        this.inputBuffer =  ByteBuffer.allocateDirect(this.inputTensor.numBytes());
        this.inputBuffer.order(ByteOrder.nativeOrder());

        this.outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes());
        this.outputBuffer.order(ByteOrder.nativeOrder());

        int outElements = 1;
        for (int d : outputTensor.shape()) outElements *= d;
        this.outRaw = new byte[outElements];
        this.outDeq = new float[outElements];

        fileDescriptor.close();
    }

    /**
     * Run inference on the loaded model with byte[] input, used for quantized models
     * @param input data to run the model as byte[]
     * @return result as float[]
     */
    @Override
    public float[] runInference(byte[] input) {

        if (input.length != this.inputTensor.numBytes()) {
            throw new IllegalArgumentException("Input data not in the expected format");
        }

        //Prepare input buffer
        this.inputBuffer.clear();
        this.inputBuffer.put(input);
        return this.run();
    }

    /**
     * Run inference on the loaded model with float[] input, used for float models
     * @param input data to run the model as float[]
     * @return result as float[]
     */
    @Override
    public float[] runInference(float[] input) {

        int expectedElems = 1;
        for (int d : this.inputTensor.shape()) expectedElems *= d;
        if (input.length != expectedElems) {
            throw new IllegalArgumentException("Input data not in the expected format: expected " + expectedElems +
                    " elements, got " + input.length);
        }

        //Prepare input buffer
        this.inputBuffer.clear();
        this.inputBuffer.asFloatBuffer().put(input);

        //prepare output buffer
        this.outputBuffer.clear();
        return this.run();
    }

    /**
     * Run the inference and dequantize the output if needed
     * @return result as float[]
     */
    private float[] run(){
        //prepare output buffer
        this.outputBuffer.clear();

        //run the inference
        this.tfLite.run(this.inputBuffer, this.outputBuffer);

        //transformer out tensor;
        this.outputBuffer.rewind();

        if(this.isQuantized) {
            this.outputBuffer.get(this.outRaw);
            for (int i = 0; i < this.outRaw.length; i++) {
                this.outDeq[i] = this.deqLut[this.outRaw[i] & 0xFF];
            }
        }
        else {
            this.outputBuffer.asFloatBuffer().get(this.outDeq);
        }

        //returns an object copy
        return Arrays.copyOf(this.outDeq, this.outDeq.length);
    }

    /**
     * Get the input shape of the model
     * @return int[] representing the shape of intput
     */
    @Override
    public int[] getInputShape() {
        return this.tfLite.getInputTensor(0).shape();
    }

    /**
     * Get the output shape of the model
     * @return  int[] representing the shape of output
     */
    @Override
    public int[] getOutputShape() {
        return this.tfLite.getOutputTensor(0).shape();
    }

    /**
     * Clean the resource of the model
     */
    @Override
    public void close() {
        if (this.tfLite != null) {
            this.tfLite.close();
        }
    }

    /**
     * Set observers to the model interpreter
     * @param observers List of ModelObserver
     */
    @Override
    public void setModelObservers(List<ModelObserver> observers) {
        this.modelObservers = observers;
    }

    /**
     * Log model input and output tensor info for debugging
     * Aso notify observers that model is loaded sending model info as string
     */
    public void logModelInfo() {
        Tensor inputTensor = this.tfLite.getInputTensor(0);
        Tensor outputTensor = this.tfLite.getOutputTensor(0);
        if(!this.modelObservers.isEmpty()){
            String modelInfo = "Model Loaded Successfully" +
                    "\r\n Input shape: " + Arrays.toString(inputTensor.shape()) +
                    "\r\n Output shape: " + Arrays.toString(outputTensor.shape());
            for(ModelObserver observer : this.modelObservers){
                observer.onModelLoaded(modelInfo);
            }
        }
    }
}
