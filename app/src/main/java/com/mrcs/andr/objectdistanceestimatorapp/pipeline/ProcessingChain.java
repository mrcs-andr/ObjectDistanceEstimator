package com.mrcs.andr.objectdistanceestimatorapp.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class ProcessingChain<I, O> {
    private final List<ProcessingStage<?, ?>> stages;

    public ProcessingChain(List<ProcessingStage<?, ?>> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("Processing stages are required");
        }
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<O> processAsync(I input) {
        CompletableFuture<Object> future = CompletableFuture.completedFuture(input);
        for (ProcessingStage<?, ?> stage : stages) {
            List<ProcessingNode<?, ?>> stageNodes = stage.getNodes();
            Executor executor = stage.getExecutor();
            if (executor == null) {
                future = future.thenApply(data -> applyStage(stageNodes, data));
            } else {
                future = future.thenApplyAsync(data -> applyStage(stageNodes, data), executor);
            }
        }
        return (CompletableFuture<O>) future;
    }

    public O process(I input) throws Exception {
        try {
            return processAsync(input).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw ex;
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw ex;
        }
    }

    public List<ProcessingStage<?, ?>> getStages() {
        return stages;
    }

    private static Object applyNode(ProcessingNode<Object, Object> node, Object input) {
        try {
            return node.process(input);
        } catch (Exception ex) {
            throw new CompletionException(ex);
        }
    }

    private static Object applyStage(List<ProcessingNode<?, ?>> nodes, Object data) {
        for (ProcessingNode<?, ?> node : nodes) {
            @SuppressWarnings("unchecked")
            ProcessingNode<Object, Object> typed = (ProcessingNode<Object, Object>) node;
            data = applyNode(typed, data);
        }
        return data;
    }
}
