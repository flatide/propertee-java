package com.propertee.task;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.propertee.interpreter.ProperTeeInterpreter;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskEngine {
    private static final long WAIT_POLL_MS = 100L;

    private final File taskBaseDir;
    private final File tasksDir;
    private final AtomicInteger taskCounter = new AtomicInteger(0);
    private final String hostInstanceId;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final boolean setsidAvailable;
    private final String taskIdPrefix;
    private final Set<String> ownedTaskIds = Collections.synchronizedSet(new HashSet<String>());

    public TaskEngine(String baseDir, String hostInstanceId) {
        this.taskBaseDir = new File(baseDir);
        this.tasksDir = new File(taskBaseDir, "tasks");
        this.hostInstanceId = hostInstanceId;
        if (!tasksDir.exists()) {
            tasksDir.mkdirs();
        }
        this.setsidAvailable = isCommandAvailable("setsid");
        this.taskIdPrefix = sanitizeTaskIdPrefix(hostInstanceId);
        initCounter();
    }

    public synchronized Task execute(TaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Task request is required");
        }
        if (request.command == null || request.command.trim().isEmpty()) {
            throw new IllegalArgumentException("Task command is required");
        }

        String taskId = nextTaskId();
        File taskDir = new File(tasksDir, "task-" + taskId);
        if (!taskDir.exists() && !taskDir.mkdirs()) {
            throw new RuntimeException("Failed to create task directory: " + taskDir.getAbsolutePath());
        }

        Task task = new Task();
        task.taskId = taskId;
        task.runId = request.runId;
        task.threadId = request.threadId;
        task.threadName = request.threadName;
        task.command = request.command;
        task.status = "starting";
        task.alive = false;
        task.startTime = System.currentTimeMillis();
        task.timeoutMs = request.timeoutMs;
        task.cwd = request.cwd;
        task.hostInstanceId = hostInstanceId;
        task.bindFiles(taskDir);
        writeCommandFiles(task, request);

        int launcherPid = launchDetached(task, request);
        task.pid = resolveTrackedPid(task, launcherPid);
        task.pidStartTime = getProcessStartTime(task.pid);
        task.pgid = getProcessGroupId(task.pid);
        task.status = "running";
        ownedTaskIds.add(task.taskId);
        task.alive = isOurProcess(task);
        refreshOutputTimestamps(task);
        saveMeta(task);
        return task;
    }

    public Task getTask(String taskId) {
        if (taskId == null) return null;
        File taskDir = new File(tasksDir, "task-" + taskId);
        if (!taskDir.exists()) return null;
        return loadTask(taskDir);
    }

    public List<Task> listTasks() {
        List<Task> tasks = loadAllTasks();
        for (Task task : tasks) {
            refreshTask(task);
        }
        return tasks;
    }

    public List<Task> listRunning() {
        List<Task> matches = new ArrayList<Task>();
        for (Task task : listTasks()) {
            if ("running".equals(task.status)) {
                matches.add(task);
            }
        }
        return matches;
    }

    public List<Task> listDetached() {
        List<Task> matches = new ArrayList<Task>();
        for (Task task : listTasks()) {
            if ("detached".equals(task.status)) {
                matches.add(task);
            }
        }
        return matches;
    }

    public List<Task> listByRun(String runId) {
        List<Task> matches = new ArrayList<Task>();
        for (Task task : listTasks()) {
            if (equalsValue(runId, task.runId)) {
                matches.add(task);
            }
        }
        return matches;
    }

    public List<Task> listByThread(int threadId) {
        List<Task> matches = new ArrayList<Task>();
        for (Task task : listTasks()) {
            if (task.threadId != null && task.threadId.intValue() == threadId) {
                matches.add(task);
            }
        }
        return matches;
    }

    public TaskObservation observe(String taskId) {
        Task task = getTask(taskId);
        if (task == null) return null;
        refreshTask(task);
        return toObservation(task);
    }

    public boolean isAlive(String taskId) {
        TaskObservation observation = observe(taskId);
        return observation != null && observation.alive;
    }

    public boolean killTask(String taskId) {
        Task task = getTask(taskId);
        if (task == null) return false;

        refreshTask(task);
        if (!task.alive) {
            return false;
        }

        terminateTask(task);
        refreshTask(task);
        if (task.alive) {
            return false;
        }

        task.status = "killed";
        task.exitCode = Integer.valueOf(-9);
        task.endTime = Long.valueOf(System.currentTimeMillis());
        saveMeta(task);
        return true;
    }

    public int killRun(String runId) {
        int killed = 0;
        for (Task task : listByRun(runId)) {
            if (killTask(task.taskId)) {
                killed++;
            }
        }
        return killed;
    }

    public String getStdout(String taskId) {
        Task task = getTask(taskId);
        if (task == null) return "";
        return readFile(task.stdoutFile);
    }

    public String getStderr(String taskId) {
        Task task = getTask(taskId);
        if (task == null) return "";
        return readFile(task.stderrFile);
    }

    public String getCombinedOutput(String taskId) {
        Task task = getTask(taskId);
        if (task == null) return "";
        return combineOutputs(task);
    }

    public Integer getExitCode(String taskId) {
        Task task = getTask(taskId);
        if (task == null) return null;
        refreshTask(task);
        return task.exitCode;
    }

    public Task waitForCompletion(String taskId, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while waiting for task");
            }

            Task task = getTask(taskId);
            if (task == null) {
                return null;
            }

            refreshTask(task);
            if (!task.alive) {
                return task;
            }

            if (timeoutMs > 0 && (System.currentTimeMillis() - start) > timeoutMs) {
                return task;
            }

            Thread.sleep(WAIT_POLL_MS);
        }
    }

    public void init() {
        for (Task task : loadAllTasks()) {
            if (!"starting".equals(task.status) && !"running".equals(task.status) && !"detached".equals(task.status)) {
                refreshTask(task);
                continue;
            }

            refreshOutputTimestamps(task);
            if (isOurProcess(task)) {
                if (task.hostInstanceId != null && !task.hostInstanceId.equals(hostInstanceId)) {
                    task.status = "detached";
                } else {
                    task.status = "running";
                }
                task.alive = true;
            } else {
                finalizeExitedTask(task);
            }
            saveMeta(task);
        }
    }

    public Map<String, Object> getStatusMap(String taskId) {
        Task task = getTask(taskId);
        if (task == null) return null;

        refreshTask(task);
        TaskObservation observation = toObservation(task);
        Map<String, Object> map = observation.toMap();
        map.put("runId", task.runId);
        map.put("threadId", task.threadId);
        map.put("threadName", task.threadName);
        map.put("pid", Integer.valueOf(task.pid));
        map.put("pgid", Integer.valueOf(task.pgid));
        map.put("exitCode", task.exitCode);
        map.put("cwd", task.cwd);
        map.put("hostInstanceId", task.hostInstanceId);
        return map;
    }

    private void initCounter() {
        int maxId = 0;
        File[] dirs = tasksDir.listFiles();
        if (dirs == null) return;
        for (File dir : dirs) {
            String name = dir.getName();
            if (!name.startsWith("task-")) continue;
            try {
                String taskId = name.substring("task-".length());
                if (!taskId.startsWith(taskIdPrefix + "-")) continue;
                int sep = taskId.lastIndexOf('-');
                if (sep < 0 || sep == taskId.length() - 1) continue;
                maxId = Math.max(maxId, Integer.parseInt(taskId.substring(sep + 1)));
            } catch (NumberFormatException e) {
                // ignore malformed directories
            }
        }
        taskCounter.set(maxId);
    }

    private String nextTaskId() {
        return taskIdPrefix + "-" + String.format(Locale.ENGLISH, "%04d", Integer.valueOf(taskCounter.incrementAndGet()));
    }

    private List<Task> loadAllTasks() {
        File[] dirs = tasksDir.listFiles();
        if (dirs == null) return new ArrayList<Task>();

        Arrays.sort(dirs, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });

        List<Task> tasks = new ArrayList<Task>();
        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            Task task = loadTask(dir);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    private Task loadTask(File taskDir) {
        File metaFile = new File(taskDir, "meta.json");
        if (!metaFile.exists()) return null;

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(metaFile);
            String json = readStream(fis);
            Task task = gson.fromJson(json, Task.class);
            if (task == null) return null;
            task.bindFiles(taskDir);
            return task;
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(fis);
        }
    }

    private void saveMeta(Task task) {
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(task.metaFile), "UTF-8");
            gson.toJson(task, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write task metadata: " + e.getMessage(), e);
        } finally {
            closeQuietly(writer);
        }
    }

    private void writeCommandFiles(Task task, TaskRequest request) {
        StringBuilder command = new StringBuilder();
        command.append("#!/bin/sh\n");
        command.append("printf '%s\\n' \"$$\" > ").append(shellQuote(task.commandPidFile.getAbsolutePath())).append("\n");
        if (request.mergeErrorToStdout) {
            command.append(": > ").append(shellQuote(task.stderrFile.getAbsolutePath())).append("\n");
        }
        command.append(request.command).append("\n");
        command.append("status=$?\n");
        command.append("printf '%s\\n' \"$status\" > ").append(shellQuote(task.exitCodeFile.getAbsolutePath())).append("\n");
        command.append("exit \"$status\"\n");

        writeFile(task.commandFile, command.toString());
    }

    private int launchDetached(Task task, TaskRequest request) {
        Process process = null;
        try {
            StringBuilder wrapped = new StringBuilder();
            if (setsidAvailable) {
                wrapped.append("setsid ");
            }
            wrapped.append("nohup /bin/sh ").append(shellQuote(task.commandFile.getAbsolutePath()));
            wrapped.append(" > ").append(shellQuote(task.stdoutFile.getAbsolutePath()));
            if (request.mergeErrorToStdout) {
                wrapped.append(" 2>&1");
            } else {
                wrapped.append(" 2> ").append(shellQuote(task.stderrFile.getAbsolutePath()));
            }
            wrapped.append(" & echo $!");
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", wrapped.toString());
            if (request.cwd != null && request.cwd.length() > 0) {
                pb.directory(new File(request.cwd));
            }
            if (request.env != null && !request.env.isEmpty()) {
                pb.environment().putAll(request.env);
            }

            process = pb.start();
            String output = readStream(process.getInputStream()).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Failed to launch detached task");
            }
            return Integer.parseInt(output);
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch task: " + e.getMessage(), e);
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    private void refreshTask(Task task) {
        // Already finalized — no disk I/O needed
        if (!task.alive && !"starting".equals(task.status) && !"running".equals(task.status)) {
            return;
        }

        refreshOutputTimestamps(task);

        if (isOurProcess(task)) {
            task.alive = true;
            if ("starting".equals(task.status)) {
                task.status = "running";
                saveMeta(task);
            }
            return;
        }

        finalizeExitedTask(task);
        // Only persist clean exits (completed/failed/killed).
        // "lost" is deferred to init() on server restart.
        if (!"lost".equals(task.status)) {
            saveMeta(task);
        }
    }

    private void finalizeExitedTask(Task task) {
        task.alive = false;
        refreshOutputTimestamps(task);

        if ("killed".equals(task.status)) {
            if (task.endTime == null) {
                task.endTime = Long.valueOf(System.currentTimeMillis());
            }
            return;
        }

        Integer exitCode = readExitCodeWithGrace(task, 500L);
        if (exitCode != null) {
            task.exitCode = exitCode;
            if (task.endTime == null) {
                task.endTime = Long.valueOf(System.currentTimeMillis());
            }
            task.status = exitCode.intValue() == 0 ? "completed" : "failed";
            return;
        }

        if (task.endTime == null) {
            task.endTime = Long.valueOf(System.currentTimeMillis());
        }
        task.status = "lost";
    }

    private void refreshOutputTimestamps(Task task) {
        task.lastStdoutAt = task.stdoutFile.exists() ? Long.valueOf(task.stdoutFile.lastModified()) : null;
        task.lastStderrAt = task.stderrFile.exists() ? Long.valueOf(task.stderrFile.lastModified()) : null;
    }

    private TaskObservation toObservation(Task task) {
        TaskObservation observation = new TaskObservation();
        observation.taskId = task.taskId;
        observation.status = task.status;
        observation.alive = task.alive;
        observation.elapsedMs = (task.endTime != null ? task.endTime.longValue() : System.currentTimeMillis()) - task.startTime;
        observation.lastStdoutAt = task.lastStdoutAt;
        observation.lastStderrAt = task.lastStderrAt;
        observation.lastOutputAgeMs = getLastOutputAge(task);
        observation.timeoutExceeded = task.timeoutMs > 0 && observation.elapsedMs > task.timeoutMs;

        if (observation.timeoutExceeded) {
            observation.healthHints.add("TIMEOUT_EXCEEDED");
        }
        if ("lost".equals(task.status)) {
            observation.healthHints.add("PROCESS_NOT_FOUND");
        }
        if (task.alive && task.pidStartTime <= 0) {
            observation.healthHints.add("IDENTITY_UNVERIFIED");
        }
        return observation;
    }

    private Long getLastOutputAge(Task task) {
        Long mostRecent = null;
        if (task.lastStdoutAt != null) {
            mostRecent = task.lastStdoutAt;
        }
        if (task.lastStderrAt != null && (mostRecent == null || task.lastStderrAt.longValue() > mostRecent.longValue())) {
            mostRecent = task.lastStderrAt;
        }
        if (mostRecent == null) {
            return Long.valueOf(System.currentTimeMillis() - task.startTime);
        }
        return Long.valueOf(System.currentTimeMillis() - mostRecent.longValue());
    }

    private Integer readExitCode(Task task) {
        if (!task.exitCodeFile.exists()) return null;
        try {
            String value = readFile(task.exitCodeFile).trim();
            if (value.length() == 0) return null;
            return Integer.valueOf(Integer.parseInt(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer readExitCodeWithGrace(Task task, long graceMs) {
        long start = System.currentTimeMillis();
        Integer exitCode = readExitCode(task);
        while (exitCode == null && (System.currentTimeMillis() - start) < graceMs) {
            try {
                Thread.sleep(25L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            exitCode = readExitCode(task);
        }
        return exitCode;
    }

    private int resolveTrackedPid(Task task, int launcherPid) {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < 500L) {
            Integer commandPid = readCommandPid(task);
            if (commandPid != null && commandPid.intValue() > 0) {
                return commandPid.intValue();
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return launcherPid;
    }

    private Integer readCommandPid(Task task) {
        if (!task.commandPidFile.exists()) return null;
        try {
            String value = readFile(task.commandPidFile).trim();
            if (value.length() == 0) return null;
            return Integer.valueOf(Integer.parseInt(value));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isOurProcess(Task task) {
        if (task == null || task.pid <= 0) return false;
        if (!isProcessAlive(task.pid)) return false;

        // Strict verification when both start times are available
        if (task.pidStartTime > 0) {
            long currentStartTime = getProcessStartTime(task.pid);
            if (currentStartTime > 0) {
                return Math.abs(currentStartTime - task.pidStartTime) < 1000;
            }
        }

        // Start time unavailable — policy depends on task origin
        if (ownedTaskIds.contains(task.taskId)) {
            // Same-instance: we launched it, PID is trustworthy
            return true;
        }

        // Adopted task (init): cannot verify identity, refuse to claim
        return false;
    }

    private boolean isProcessAlive(int pid) {
        Process process = null;
        try {
            process = new ProcessBuilder("kill", "-0", String.valueOf(pid)).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    private int getProcessGroupId(int pid) {
        Process process = null;
        try {
            process = new ProcessBuilder("ps", "-o", "pgid=", "-p", String.valueOf(pid)).start();
            String value = readStream(process.getInputStream()).trim();
            if (process.waitFor() != 0 || value.length() == 0) {
                return pid;
            }
            return Integer.parseInt(value);
        } catch (Exception e) {
            return pid;
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    private long getProcessStartTime(int pid) {
        Process process = null;
        try {
            process = new ProcessBuilder("ps", "-p", String.valueOf(pid), "-o", "lstart=").start();
            String value = readStream(process.getInputStream()).trim();
            if (process.waitFor() != 0 || value.length() == 0) {
                return -1L;
            }
            SimpleDateFormat format = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH);
            Date parsed = format.parse(value.replaceAll("\\s+", " ").trim());
            return parsed.getTime();
        } catch (IOException e) {
            return -1L;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1L;
        } catch (ParseException e) {
            return -1L;
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    private void terminateTask(Task task) {
        // Only kill the full process group when the task owns its group.
        if (task.pgid > 0 && task.pgid == task.pid) {
            sendSignalToGroup(task.pgid, "TERM");
            waitForExit(task, 1000L);
            if (isOurProcess(task)) {
                sendSignalToGroup(task.pgid, "KILL");
                waitForExit(task, 1000L);
            }
            return;
        }

        List<Integer> descendants = listDescendantPids(task.pid);
        sendSignal(task.pid, "KILL");
        for (int i = descendants.size() - 1; i >= 0; i--) {
            sendSignal(descendants.get(i).intValue(), "KILL");
        }
        waitForExit(task, 500L);
        if (isOurProcess(task)) {
            sendSignal(task.pid, "KILL");
        }
        waitForExit(task, 1000L);
    }

    private void waitForExit(Task task, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (isOurProcess(task) && (System.currentTimeMillis() - start) < timeoutMs) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void sendSignalToGroup(int pgid, String signal) {
        Process process = null;
        try {
            process = new ProcessBuilder("kill", "-" + signal, "-" + pgid).start();
            process.waitFor();
        } catch (Exception e) {
            // ignore best-effort kill
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    private void sendSignal(int pid, String signal) {
        Process process = null;
        try {
            process = new ProcessBuilder("kill", "-" + signal, String.valueOf(pid)).start();
            process.waitFor();
        } catch (Exception e) {
            // ignore best-effort kill
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    private List<Integer> listDescendantPids(int rootPid) {
        Process process = null;
        Map<Integer, List<Integer>> children = new java.util.LinkedHashMap<Integer, List<Integer>>();
        try {
            process = new ProcessBuilder("ps", "-eo", "pid=,ppid=").start();
            String output = readStream(process.getInputStream());
            if (process.waitFor() != 0) {
                return new ArrayList<Integer>();
            }

            String[] lines = output.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.length() == 0) continue;
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 2) continue;
                try {
                    Integer pid = Integer.valueOf(Integer.parseInt(parts[0]));
                    Integer ppid = Integer.valueOf(Integer.parseInt(parts[1]));
                    List<Integer> childList = children.get(ppid);
                    if (childList == null) {
                        childList = new ArrayList<Integer>();
                        children.put(ppid, childList);
                    }
                    childList.add(pid);
                } catch (NumberFormatException e) {
                    // ignore malformed rows
                }
            }
        } catch (Exception e) {
            return new ArrayList<Integer>();
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }

        List<Integer> descendants = new ArrayList<Integer>();
        collectDescendants(rootPid, children, descendants, new HashSet<Integer>());
        return descendants;
    }

    private void collectDescendants(int pid, Map<Integer, List<Integer>> children, List<Integer> descendants, Set<Integer> visited) {
        List<Integer> childList = children.get(Integer.valueOf(pid));
        if (childList == null) return;

        for (Integer child : childList) {
            if (!visited.add(child)) continue;
            descendants.add(child);
            collectDescendants(child.intValue(), children, descendants, visited);
        }
    }

    private String combineOutputs(Task task) {
        String stdout = readFile(task.stdoutFile);
        String stderr = readFile(task.stderrFile);

        if (stderr.length() == 0) {
            return stdout;
        }
        if (stdout.length() == 0) {
            return stderr;
        }
        return stdout + "\n" + stderr;
    }

    private void writeFile(File file, String content) {
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + file.getAbsolutePath(), e);
        } finally {
            closeQuietly(writer);
        }
    }

    private String readFile(File file) {
        if (file == null || !file.exists()) return "";
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            return readStream(fis);
        } catch (IOException e) {
            return "";
        } finally {
            closeQuietly(fis);
        }
    }

    private String readStream(InputStream input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = input.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toString("UTF-8");
    }

    private String shellQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String sanitizeTaskIdPrefix(String raw) {
        if (raw == null || raw.length() == 0) {
            return "task";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if ((ch >= 'a' && ch <= 'z') ||
                (ch >= 'A' && ch <= 'Z') ||
                (ch >= '0' && ch <= '9')) {
                sb.append(ch);
            }
        }
        if (sb.length() == 0) {
            return "task";
        }
        return sb.toString();
    }

    private boolean isCommandAvailable(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("/bin/sh", "-c", "command -v " + command).start();
            String output = readStream(process.getInputStream()).trim();
            return process.waitFor() == 0 && output.length() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    private boolean equalsValue(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
