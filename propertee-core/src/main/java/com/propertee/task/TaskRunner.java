package com.propertee.task;

import java.util.Map;

/**
 * Lightweight process execution interface for the ProperTee eval/runtime.
 * Handles process execution, waiting, killing, and output capture.
 * Does NOT handle persistence, indexing, archival, or multi-instance management.
 */
public interface TaskRunner {
    Task execute(TaskRequest request);
    Task getTask(String taskId);  // Current process only (in-memory)
    Task waitForCompletion(String taskId, long timeoutMs) throws InterruptedException;
    boolean killTask(String taskId);
    TaskObservation observe(String taskId);
    String getStdout(String taskId);
    String getStderr(String taskId);
    String getCombinedOutput(String taskId);
    Integer getExitCode(String taskId);
    Map<String, Object> getStatusMap(String taskId);
    void shutdown();
}
