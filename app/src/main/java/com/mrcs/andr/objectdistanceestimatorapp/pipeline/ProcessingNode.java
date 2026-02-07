package com.mrcs.andr.objectdistanceestimatorapp.pipeline;

@FunctionalInterface
public interface ProcessingNode<I, O> {
    O process(I input) throws Exception;
}
