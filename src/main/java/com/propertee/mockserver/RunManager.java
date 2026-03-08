package com.propertee.mockserver;

import com.propertee.cli.Main;
import com.propertee.interpreter.BuiltinFunctions;
import com.propertee.interpreter.ProperTeeInterpreter;
import com.propertee.parser.ProperTeeParser;
import com.propertee.runtime.TypeChecker;
import com.propertee.scheduler.Scheduler;
import com.propertee.scheduler.SchedulerListener;
import com.propertee.scheduler.ThreadContext;
import com.propertee.task.Task;
import com.propertee.task.TaskEngine;
import com.propertee.task.TaskInfo;
import com.propertee.task.TaskObservation;
import com.propertee.stepper.Stepper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

public class RunManager {
    private static final int MAX_LOG_LINES = 200;

    private final File scriptsRoot;
    private final File dataDir;
    private final RunStore runStore;
    private final TaskEngine taskEngine;
    private final ThreadPoolExecutor runExecutor;
    private final Map<String, RunInfo> runs = new ConcurrentHashMap<String, RunInfo>();
    private final Map<String, Future<?>> activeRuns = new ConcurrentHashMap<String, Future<?>>();

    public RunManager(File scriptsRoot, File dataDir, int maxConcurrentRuns) {
        this.scriptsRoot = scriptsRoot;
        this.dataDir = dataDir;
        if (!this.scriptsRoot.exists() || !this.scriptsRoot.isDirectory()) {
            throw new IllegalArgumentException("scriptsRoot must be an existing directory: " + this.scriptsRoot.getAbsolutePath());
        }
        if (!this.dataDir.exists()) {
            this.dataDir.mkdirs();
        }
        this.runStore = new RunStore(this.dataDir);
        this.taskEngine = new TaskEngine(this.dataDir.getAbsolutePath(), createHostInstanceId());
        this.taskEngine.init();
        this.runExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(1, maxConcurrentRuns));
        loadPersistedRuns();
    }

    public RunInfo submit(final RunRequest request) {
        final File scriptFile = resolveScriptPath(request.scriptPath);
        final RunInfo run = new RunInfo();
        run.runId = createRunId();
        run.scriptPath = request.scriptPath;
        run.scriptAbsolutePath = scriptFile.getAbsolutePath();
        run.status = RunStatus.QUEUED;
        run.createdAt = System.currentTimeMillis();
        run.maxIterations = request.maxIterations > 0 ? request.maxIterations : 1000;
        run.iterationLimitBehavior = request.warnLoops ? "warn" : "error";
        run.properties = sanitizeProperties(request.props);
        runs.put(run.runId, run);
        saveRun(run);

        Future<?> future = runExecutor.submit(new Runnable() {
            @Override
            public void run() {
                executeRun(run, scriptFile);
            }
        });
        activeRuns.put(run.runId, future);
        return copyRun(run);
    }

    public List<RunInfo> listRuns() {
        List<RunInfo> list = new ArrayList<RunInfo>();
        for (RunInfo run : runs.values()) {
            list.add(copyRun(run));
        }
        Collections.sort(list, new Comparator<RunInfo>() {
            @Override
            public int compare(RunInfo a, RunInfo b) {
                if (a.createdAt == b.createdAt) return a.runId.compareTo(b.runId);
                return a.createdAt < b.createdAt ? 1 : -1;
            }
        });
        return list;
    }

    public RunInfo getRun(String runId) {
        RunInfo run = runs.get(runId);
        return run != null ? copyRun(run) : null;
    }

    public List<RunThreadInfo> listThreads(String runId) {
        RunInfo run = runs.get(runId);
        if (run == null) {
            return new ArrayList<RunThreadInfo>();
        }
        synchronized (run) {
            return copyThreads(run.threads);
        }
    }

    public List<TaskInfo> listTasksForRun(String runId) {
        return toInfoList(taskEngine.listByRun(runId));
    }

    public List<TaskInfo> listAllTasks() {
        return toInfoList(taskEngine.listTasks());
    }

    public List<TaskInfo> listDetachedTasks() {
        return toInfoList(taskEngine.listDetached());
    }

    public TaskInfo getTask(String taskId) {
        Task task = taskEngine.getTask(taskId);
        if (task == null) return null;
        return toInfo(task);
    }

    public TaskObservation observeTask(String taskId) {
        return taskEngine.observe(taskId);
    }

    public String getTaskStdout(String taskId) {
        return taskEngine.getStdout(taskId);
    }

    public String getTaskStderr(String taskId) {
        return taskEngine.getStderr(taskId);
    }

    public boolean killTask(String taskId) {
        return taskEngine.killTask(taskId);
    }

    public int killRunTasks(String runId) {
        return taskEngine.killRun(runId);
    }

    public int getQueuedCount() {
        return runExecutor.getQueue().size();
    }

    public int getActiveCount() {
        return runExecutor.getActiveCount();
    }

    public void shutdown() {
        runExecutor.shutdownNow();
    }

    private void loadPersistedRuns() {
        List<RunInfo> existing = runStore.loadAll();
        long now = System.currentTimeMillis();
        for (RunInfo run : existing) {
            if (run.status != null && !isTerminal(run.status)) {
                run.status = RunStatus.SERVER_RESTARTED;
                if (run.endedAt == null) {
                    run.endedAt = Long.valueOf(now);
                }
                if (run.errorMessage == null || run.errorMessage.length() == 0) {
                    run.errorMessage = "Server restarted before run finished";
                }
                runStore.save(run);
            }
            runs.put(run.runId, run);
        }
    }

    private void executeRun(final RunInfo run, File scriptFile) {
        ProperTeeInterpreter visitor = null;
        try {
            markRunStarted(run);
            String scriptText = readFile(scriptFile);
            List<String> errors = new ArrayList<String>();
            ProperTeeParser.RootContext tree = Main.parseScript(scriptText, errors);
            if (tree == null) {
                markRunFailed(run, joinErrors(errors));
                return;
            }

            BuiltinFunctions.PrintFunction stdout = new BuiltinFunctions.PrintFunction() {
                @Override
                public void print(Object[] args) {
                    appendLog(run, true, joinPrintArgs(args));
                }
            };
            BuiltinFunctions.PrintFunction stderr = new BuiltinFunctions.PrintFunction() {
                @Override
                public void print(Object[] args) {
                    appendLog(run, false, joinPrintArgs(args));
                }
            };

            BuiltinFunctions builtins = new BuiltinFunctions(stdout, stderr, run.runId, taskEngine);
            visitor = new ProperTeeInterpreter(run.properties, stdout, stderr, run.maxIterations, run.iterationLimitBehavior, builtins);
            Scheduler scheduler = new Scheduler(visitor, new RunSchedulerListener(run));
            Stepper mainStepper = visitor.createRootStepper(tree);
            Object result = scheduler.run(mainStepper);
            markRunCompleted(run, result);
        } catch (Throwable error) {
            markRunFailed(run, error != null ? error.getMessage() : "Unknown error");
        } finally {
            activeRuns.remove(run.runId);
            if (visitor != null) {
                visitor.builtins.shutdown();
            }
        }
    }

    private void markRunStarted(RunInfo run) {
        synchronized (run) {
            run.status = RunStatus.RUNNING;
            run.startedAt = Long.valueOf(System.currentTimeMillis());
            if (run.endedAt != null) {
                run.endedAt = null;
            }
            saveRunLocked(run);
        }
    }

    private void markRunCompleted(RunInfo run, Object result) {
        synchronized (run) {
            run.status = RunStatus.COMPLETED;
            run.endedAt = Long.valueOf(System.currentTimeMillis());
            run.resultSummary = safeSummary(result);
            saveRunLocked(run);
        }
    }

    private void markRunFailed(RunInfo run, String message) {
        synchronized (run) {
            run.status = RunStatus.FAILED;
            run.endedAt = Long.valueOf(System.currentTimeMillis());
            run.errorMessage = message != null ? message : "Unknown error";
            saveRunLocked(run);
        }
    }

    private void appendLog(RunInfo run, boolean stdout, String line) {
        synchronized (run) {
            List<String> target = stdout ? run.stdoutLines : run.stderrLines;
            target.add(line);
            while (target.size() > MAX_LOG_LINES) {
                target.remove(0);
            }
            saveRunLocked(run);
        }
    }

    private void upsertThread(RunInfo run, RunThreadInfo threadInfo) {
        synchronized (run) {
            int idx = findThreadIndex(run.threads, threadInfo.threadId);
            if (idx >= 0) {
                run.threads.set(idx, threadInfo);
            } else {
                run.threads.add(threadInfo);
                Collections.sort(run.threads, new Comparator<RunThreadInfo>() {
                    @Override
                    public int compare(RunThreadInfo a, RunThreadInfo b) {
                        return a.threadId < b.threadId ? -1 : (a.threadId == b.threadId ? 0 : 1);
                    }
                });
            }
            saveRunLocked(run);
        }
    }

    private int findThreadIndex(List<RunThreadInfo> threads, int threadId) {
        for (int i = 0; i < threads.size(); i++) {
            if (threads.get(i).threadId == threadId) {
                return i;
            }
        }
        return -1;
    }

    private RunThreadInfo createThreadSnapshot(ThreadContext thread) {
        RunThreadInfo info = new RunThreadInfo();
        info.threadId = thread.id;
        info.name = thread.name;
        info.state = thread.state != null ? thread.state.name() : null;
        info.parentId = thread.parentId;
        info.inThreadContext = thread.inThreadContext;
        info.sleepUntil = thread.sleepUntil;
        info.asyncPending = thread.asyncFuture != null;
        info.resultKeyName = thread.resultKeyName;
        info.updatedAt = System.currentTimeMillis();
        if (thread.result != null) {
            info.resultSummary = safeSummary(thread.result);
        }
        if (thread.error != null) {
            info.errorMessage = thread.error.getMessage();
        }
        return info;
    }

    private String safeSummary(Object value) {
        try {
            String formatted = TypeChecker.formatValue(value);
            if (formatted != null && formatted.length() > 300) {
                return formatted.substring(0, 300) + "...";
            }
            return formatted;
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private List<RunThreadInfo> copyThreads(List<RunThreadInfo> threads) {
        List<RunThreadInfo> copy = new ArrayList<RunThreadInfo>();
        for (RunThreadInfo thread : threads) {
            copy.add(thread.copy());
        }
        return copy;
    }

    private RunInfo copyRun(RunInfo run) {
        synchronized (run) {
            return run.copy();
        }
    }

    private void saveRun(RunInfo run) {
        synchronized (run) {
            saveRunLocked(run);
        }
    }

    private void saveRunLocked(RunInfo run) {
        runStore.save(run.copy());
    }

    private boolean isTerminal(RunStatus status) {
        return status == RunStatus.COMPLETED || status == RunStatus.FAILED || status == RunStatus.SERVER_RESTARTED;
    }

    private File resolveScriptPath(String scriptPath) {
        if (scriptPath == null || scriptPath.trim().length() == 0) {
            throw new IllegalArgumentException("scriptPath is required");
        }
        if (scriptPath.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("scriptPath contains invalid characters");
        }
        try {
            File root = scriptsRoot.getCanonicalFile();
            File resolved = new File(root, scriptPath).getCanonicalFile();
            if (!resolved.getPath().startsWith(root.getPath() + File.separator) && !resolved.equals(root)) {
                throw new IllegalArgumentException("scriptPath must stay inside scriptsRoot");
            }
            if (!resolved.isFile()) {
                throw new IllegalArgumentException("Script not found: " + scriptPath);
            }
            if (!resolved.getName().endsWith(".pt")) {
                throw new IllegalArgumentException("Only .pt scripts are allowed");
            }
            return resolved;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to resolve scriptPath: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeProperties(Map<String, Object> props) {
        if (props == null) {
            return new LinkedHashMap<String, Object>();
        }
        return (Map<String, Object>) TypeChecker.deepCopy(props);
    }

    private String joinErrors(List<String> errors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(errors.get(i));
        }
        return sb.toString();
    }

    private String joinPrintArgs(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private String readFile(File file) throws IOException {
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return new String(bytes, 0, offset, Charset.forName("UTF-8"));
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    private static String createRunId() {
        return "run-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ENGLISH).format(new Date()) +
            "-" + Integer.toHexString((int) (System.nanoTime() & 0xffff));
    }

    private static String createHostInstanceId() {
        return "mock-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ENGLISH).format(new Date()) +
            "-" + Integer.toHexString((int) (System.nanoTime() & 0xffff));
    }

    private class RunSchedulerListener implements SchedulerListener {
        private final RunInfo run;

        private RunSchedulerListener(RunInfo run) {
            this.run = run;
        }

        @Override
        public void onThreadCreated(ThreadContext thread) {
            upsertThread(run, createThreadSnapshot(thread));
        }

        @Override
        public void onThreadUpdated(ThreadContext thread) {
            upsertThread(run, createThreadSnapshot(thread));
        }

        @Override
        public void onThreadCompleted(ThreadContext thread) {
            upsertThread(run, createThreadSnapshot(thread));
        }

        @Override
        public void onThreadError(ThreadContext thread) {
            upsertThread(run, createThreadSnapshot(thread));
        }
    }

    private List<TaskInfo> toInfoList(List<Task> tasks) {
        List<TaskInfo> result = new ArrayList<TaskInfo>();
        for (Task task : tasks) {
            result.add(toInfo(task));
        }
        return result;
    }

    private TaskInfo toInfo(Task task) {
        TaskObservation obs = taskEngine.observe(task.taskId);
        TaskInfo info = new TaskInfo();
        info.taskId = task.taskId;
        info.runId = task.runId;
        info.threadId = task.threadId;
        info.threadName = task.threadName;
        info.command = task.command;
        info.pid = task.pid;
        info.pgid = task.pgid;
        info.status = obs != null ? obs.status : task.status;
        info.alive = obs != null ? obs.alive : task.alive;
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
