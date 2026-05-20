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
    /**
     * Signal that the caller no longer needs the task's in-memory state.
     * Implementations that don't persist tasks (e.g. SimpleTaskRunner) MAY
     * remove the task immediately. Implementations that need to keep task
     * metadata for later inspection (e.g. ManagedTaskEngine for admin UI)
     * should treat this as a no-op.
     */
    void releaseTask(String taskId);
    void shutdown();
}
