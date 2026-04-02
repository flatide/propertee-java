package com.propertee.task;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory TaskRunner for CLI/standalone use.
 * No disk persistence, no process group management, no archival.
 * Runs shell commands via /bin/sh -c and captures stdout/stderr.
 */
public class SimpleTaskRunner implements TaskRunner {
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<String, TaskState>();
    private int nextId = 1;

    private static class TaskState {
        final String taskId;
        final String command;
        final long startTime;
        Process process;
        String stdout = "";
        String stderr = "";
        Integer exitCode;
        boolean alive = true;

        TaskState(String taskId, String command) {
            this.taskId = taskId;
            this.command = command;
            this.startTime = System.currentTimeMillis();
        }
    }

    @Override
    public synchronized Task execute(TaskRequest request) {
        String taskId = "task-" + (nextId++);
        TaskState state = new TaskState(taskId, request.command);

        try {
            List<String> cmd = new ArrayList<String>();
            cmd.add("/bin/sh");
            cmd.add("-c");
            cmd.add(request.command);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (request.cwd != null) {
                pb.directory(new File(request.cwd));
            }
            if (request.env != null && !request.env.isEmpty()) {
                pb.environment().putAll(request.env);
            }
            if (request.mergeErrorToStdout) {
                pb.redirectErrorStream(true);
            }
            state.process = pb.start();
        } catch (IOException e) {
            state.alive = false;
            state.exitCode = -1;
            state.stderr = e.getMessage();
        }

        tasks.put(taskId, state);

        Task task = new Task();
        task.taskId = taskId;
        task.command = request.command;
        task.status = TaskStatus.RUNNING;
        task.alive = true;
        task.startTime = state.startTime;
        return task;
    }

    @Override
    public Task getTask(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) return null;
        return toTask(state);
    }

    @Override
    public Task waitForCompletion(String taskId, long timeoutMs) throws InterruptedException {
        TaskState state = tasks.get(taskId);
        if (state == null || state.process == null) return toTask(state);

        if (timeoutMs > 0) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (state.alive && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                checkProcess(state);
            }
        } else {
            state.process.waitFor();
            checkProcess(state);
        }
        return toTask(state);
    }

    @Override
    public boolean killTask(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null || state.process == null || !state.alive) return false;
        state.process.destroyForcibly();
        checkProcess(state);
        return true;
    }

    @Override
    public TaskObservation observe(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) return null;
        checkProcess(state);
        TaskObservation obs = new TaskObservation();
        obs.taskId = taskId;
        obs.status = state.alive ? "running" : (state.exitCode != null && state.exitCode == 0 ? "completed" : "failed");
        obs.alive = state.alive;
        obs.elapsedMs = System.currentTimeMillis() - state.startTime;
        return obs;
    }

    @Override
    public String getStdout(String taskId) {
        TaskState state = tasks.get(taskId);
        return state != null ? readOutput(state, false) : "";
    }

    @Override
    public String getStderr(String taskId) {
        TaskState state = tasks.get(taskId);
        return state != null ? readOutput(state, true) : "";
    }

    @Override
    public String getCombinedOutput(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) return "";
        String out = readOutput(state, false);
        if (!state.stderr.isEmpty()) {
            String err = readOutput(state, true);
            if (!err.isEmpty()) {
                out = out.isEmpty() ? err : out + "\n" + err;
            }
        }
        return out;
    }

    @Override
    public Integer getExitCode(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) return null;
        checkProcess(state);
        return state.exitCode;
    }

    @Override
    public Map<String, Object> getStatusMap(String taskId) {
        TaskObservation obs = observe(taskId);
        return obs != null ? obs.toMap() : new LinkedHashMap<String, Object>();
    }

    @Override
    public void shutdown() {
        for (TaskState state : tasks.values()) {
            if (state.process != null && state.alive) {
                state.process.destroyForcibly();
            }
        }
        tasks.clear();
    }

    private void checkProcess(TaskState state) {
        if (!state.alive || state.process == null) return;
        try {
            if (!state.process.isAlive()) {
                state.exitCode = state.process.exitValue();
                state.alive = false;
                state.stdout = drain(state.process.getInputStream());
                if (state.process.getErrorStream() != null) {
                    state.stderr = drain(state.process.getErrorStream());
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private String readOutput(TaskState state, boolean isStderr) {
        checkProcess(state);
        return isStderr ? state.stderr : state.stdout;
    }

    private Task toTask(TaskState state) {
        if (state == null) return null;
        checkProcess(state);
        Task task = new Task();
        task.taskId = state.taskId;
        task.command = state.command;
        task.alive = state.alive;
        task.exitCode = state.exitCode;
        task.startTime = state.startTime;
        task.status = state.alive ? TaskStatus.RUNNING : (state.exitCode != null && state.exitCode == 0 ? TaskStatus.COMPLETED : TaskStatus.FAILED);
        return task;
    }

    private static String drain(InputStream is) {
        try {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) {
                sb.append(new String(buf, 0, len, "UTF-8"));
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }
}
