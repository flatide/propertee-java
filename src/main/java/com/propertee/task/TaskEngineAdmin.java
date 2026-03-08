package com.propertee.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskEngineAdmin {
    private final TaskEngine engine;

    public TaskEngineAdmin(TaskEngine engine) {
        this.engine = engine;
    }

    // --- Query ---

    public List<TaskInfo> listAll() {
        return toInfoList(engine.listTasks());
    }

    public List<TaskInfo> listRunning() {
        return toInfoList(engine.listRunning());
    }

    public List<TaskInfo> listDetached() {
        return toInfoList(engine.listDetached());
    }

    public List<TaskInfo> listByRun(String runId) {
        return toInfoList(engine.listByRun(runId));
    }

    public List<TaskInfo> listByThread(int threadId) {
        return toInfoList(engine.listByThread(threadId));
    }

    public TaskInfo getTask(String taskId) {
        Task task = engine.getTask(taskId);
        if (task == null) return null;
        return toInfo(task);
    }

    public TaskObservation observe(String taskId) {
        return engine.observe(taskId);
    }

    // --- Control ---

    public boolean killTask(String taskId) {
        return engine.killTask(taskId);
    }

    public int killRun(String runId) {
        return engine.killRun(runId);
    }

    // --- Conversion ---

    private List<TaskInfo> toInfoList(List<Task> tasks) {
        List<TaskInfo> result = new ArrayList<TaskInfo>();
        for (Task task : tasks) {
            result.add(toInfo(task));
        }
        return result;
    }

    private TaskInfo toInfo(Task task) {
        TaskObservation obs = engine.observe(task.taskId);

        TaskInfo info = new TaskInfo();
        info.taskId = task.taskId;
        info.runId = task.runId;
        info.threadId = task.threadId;
        info.threadName = task.threadName;
        info.command = task.command;
        info.pid = task.pid;
        info.pgid = task.pgid;
        info.status = task.status;
        info.alive = task.alive;
        info.elapsedMs = obs != null ? obs.elapsedMs : 0;
        info.lastStdoutAt = task.lastStdoutAt;
        info.lastStderrAt = task.lastStderrAt;
        info.lastOutputAgeMs = obs != null ? obs.lastOutputAgeMs : null;
        info.exitCode = task.exitCode;
        info.cwd = task.cwd;
        info.hostInstanceId = task.hostInstanceId;
        info.healthHints = obs != null ? obs.healthHints : new ArrayList<String>();
        return info;
    }
}
