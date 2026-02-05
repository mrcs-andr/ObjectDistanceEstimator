package com.mrcs.andr.objectdistanceestimatorapp.pipeline;

import java.util.concurrent.Executor;

public class ProcessingStage<I, O> {
    private final ProcessingNode<I, O> node;
    private final Executor executor;

    public ProcessingStage(ProcessingNode<I, O> node, Executor executor) {
        if (node == null) {
            throw new IllegalArgumentException("ProcessingNode is required");
        }
        this.node = node;
        this.executor = executor;
    }

    public ProcessingNode<I, O> getNode() {
        return node;
    }

    public Executor getExecutor() {
        return executor;
    }
}
