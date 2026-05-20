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
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024; // 1 MB cap per stream
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<String, TaskState>();
    private int nextId = 1;

    private static class TaskState {
        final String taskId;
        final String command;
        final long startTime;
        Process process;
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();
        Thread stdoutDrainer;
        Thread stderrDrainer;
        Integer exitCode;
        volatile boolean alive = true;

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
            // Start drainer threads immediately to prevent OS pipe deadlock
            state.stdoutDrainer = startDrainer(state.process.getInputStream(), state.stdout, "drain-stdout-" + taskId);
            if (!request.mergeErrorToStdout) {
                state.stderrDrainer = startDrainer(state.process.getErrorStream(), state.stderr, "drain-stderr-" + taskId);
            }
        } catch (IOException e) {
            state.alive = false;
            state.exitCode = -1;
            state.stderr.append(e.getMessage());
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
        String err = readOutput(state, true);
        if (err.length() == 0) return out;
        if (out.length() == 0) return err;
        return out + "\n" + err;
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
    public void releaseTask(String taskId) {
        if (taskId == null) return;
        TaskState state = tasks.remove(taskId);
        if (state != null) {
            // Cancel drainers and clear process reference to release native resources
            joinQuietly(state.stdoutDrainer, 100);
            joinQuietly(state.stderrDrainer, 100);
            state.process = null;
        }
    }

    @Override
    public void shutdown() {
        for (TaskState state : tasks.values()) {
            if (state.process != null && state.alive) {
                state.process.destroyForcibly();
            }
            joinQuietly(state.stdoutDrainer, 200);
            joinQuietly(state.stderrDrainer, 200);
        }
        tasks.clear();
    }

    private void checkProcess(TaskState state) {
        if (!state.alive || state.process == null) return;
        try {
            if (!state.process.isAlive()) {
                state.exitCode = state.process.exitValue();
                state.alive = false;
                // Wait briefly for drainer threads to finish reading remaining output
                joinQuietly(state.stdoutDrainer, 500);
                joinQuietly(state.stderrDrainer, 500);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private String readOutput(TaskState state, boolean isStderr) {
        checkProcess(state);
        StringBuilder buf = isStderr ? state.stderr : state.stdout;
        synchronized (buf) {
            return buf.toString();
        }
    }

    private static Thread startDrainer(final InputStream is, final StringBuilder buf, String name) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] tmp = new byte[4096];
                    int len;
                    while ((len = is.read(tmp)) != -1) {
                        synchronized (buf) {
                            int room = MAX_OUTPUT_BYTES - buf.length();
                            if (room <= 0) continue; // drain & discard once cap hit
                            int writeLen = Math.min(len, room);
                            buf.append(new String(tmp, 0, writeLen, "UTF-8"));
                        }
                    }
                } catch (IOException ignore) {
                    // stream closed
                } finally {
                    try { is.close(); } catch (IOException ignore) {}
                }
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void joinQuietly(Thread t, long timeoutMs) {
        if (t == null) return;
        try {
            t.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

}
