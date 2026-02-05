package com.mrcs.andr.objectdistanceestimatorapp.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public class ProcessingStage<I, O> {
    private final List<ProcessingNode<?, ?>> nodes;
    private final Executor executor;

    public ProcessingStage(ProcessingNode<I, O> node, Executor executor) {
        this(Collections.singletonList(node), executor);
    }

    public ProcessingStage(List<ProcessingNode<?, ?>> nodes, Executor executor) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("At least one ProcessingNode is required");
        }
        for (ProcessingNode<?, ?> node : nodes) {
            if (node == null) {
                throw new IllegalArgumentException("ProcessingNode list contains null entries");
            }
        }
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.executor = executor;
    }

    public List<ProcessingNode<?, ?>> getNodes() {
        return nodes;
    }

    public Executor getExecutor() {
        return executor;
    }
}
