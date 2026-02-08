package com.propertee.scheduler;

import com.propertee.interpreter.ProperTeeInterpreter;
import com.propertee.parser.ProperTeeParser;
import com.propertee.runtime.Result;
import com.propertee.stepper.*;

import java.util.*;

public class Scheduler {
    private final ProperTeeInterpreter visitor;
    private final Map<Integer, ThreadContext> threads = new LinkedHashMap<Integer, ThreadContext>();
    private int nextThreadId = 0;
    private Integer currentThreadId = null;

    // Monitor state
    private final List<MonitorState> monitors = new ArrayList<MonitorState>();

    private static class MonitorState {
        int interval;
        ProperTeeParser.BlockContext blockCtx;
        long lastRun;
        int parentThreadId;
        List<Integer> childIds;
    }

    public Scheduler(ProperTeeInterpreter visitor) {
        this.visitor = visitor;
    }

    public ThreadContext createThread(String name, Stepper stepper, Map<String, Object> globalSnapshot) {
        int id = nextThreadId++;
        ThreadContext thread = new ThreadContext(id, name, stepper, globalSnapshot);
        threads.put(id, thread);
        return thread;
    }

    private ThreadContext selectNextThread() {
        List<Integer> ids = new ArrayList<Integer>(threads.keySet());
        Collections.sort(ids);
        if (ids.isEmpty()) return null;

        int startIdx = currentThreadId != null ? ids.indexOf(currentThreadId) : -1;

        for (int i = 1; i <= ids.size(); i++) {
            int idx = (startIdx + i) % ids.size();
            ThreadContext thread = threads.get(ids.get(idx));
            if (thread.state == ThreadState.READY) {
                return thread;
            }
        }
        return null;
    }

    private void wakeThreads(long now) {
        for (ThreadContext thread : threads.values()) {
            if (thread.shouldWake(now)) {
                thread.sleepUntil = null;
                thread.markReady();
            }
        }
    }

    private boolean hasActiveThreads() {
        for (ThreadContext thread : threads.values()) {
            if (thread.state != ThreadState.COMPLETED && thread.state != ThreadState.ERROR) {
                return true;
            }
        }
        return false;
    }

    private Long getMinSleepRemaining(long now) {
        long min = Long.MAX_VALUE;
        for (ThreadContext thread : threads.values()) {
            if (thread.state == ThreadState.SLEEPING && thread.sleepUntil != null) {
                long remaining = thread.sleepUntil - now;
                if (remaining < min) min = remaining;
            }
        }
        return min == Long.MAX_VALUE ? null : Math.max(0, min);
    }

    private void processStepResult(ThreadContext thread, StepResult stepResult) {
        if (stepResult.isBoundary()) {
            thread.markReady();
            return;
        }

        if (stepResult.isCommand()) {
            SchedulerCommand cmd = (SchedulerCommand) stepResult.getValue();
            switch (cmd.getType()) {
                case SLEEP: {
                    long now = System.currentTimeMillis();
                    thread.markSleeping(now + cmd.getDuration());
                    return;
                }
                case SPAWN_THREADS: {
                    handleSpawnThreads(thread, cmd);
                    return;
                }
            }
        }

        if (stepResult.isDone()) {
            thread.markCompleted(stepResult.getValue());
            notifyChildCompleted(thread);
            return;
        }

        thread.markReady();
    }

    private void handleSpawnThreads(ThreadContext parentThread, SchedulerCommand command) {
        List<SchedulerCommand.ThreadSpec> specs = command.getSpecs();
        List<String> resultKeyNames = command.getResultKeyNames();
        Map<String, Object> globalSnapshot = command.getGlobalSnapshot();
        List<Integer> childIds = new ArrayList<Integer>();

        for (int i = 0; i < specs.size(); i++) {
            SchedulerCommand.ThreadSpec spec = specs.get(i);
            ThreadContext childThread = createThread(
                spec.getName(),
                spec.getStepper(),
                globalSnapshot
            );
            childThread.parentId = parentThread.id;
            childThread.inThreadContext = true;
            childThread.resultKeyName = resultKeyNames.get(i);
            childThread.localScope = spec.getLocalScope();
            childIds.add(childThread.id);
        }

        // Set up monitor
        if (command.getMonitorSpec() != null) {
            SchedulerCommand.MonitorSpec ms = command.getMonitorSpec();
            MonitorState monitor = new MonitorState();
            monitor.interval = ms.getInterval();
            monitor.blockCtx = (ProperTeeParser.BlockContext) ms.getBlockCtx();
            monitor.lastRun = System.currentTimeMillis();
            monitor.parentThreadId = parentThread.id;
            monitor.childIds = new ArrayList<Integer>(childIds);
            monitors.add(monitor);
        }

        parentThread.markWaiting(childIds);
        parentThread.childIds = childIds;
        parentThread.resultKeyNames = resultKeyNames;
        parentThread.resultCollectionVarName = command.getResultVarName();

        // Pre-build the live result collection with Result.running() entries
        // All keys are pre-resolved by the interpreter (no nulls)
        if (command.getResultVarName() != null) {
            Map<String, Object> collection = new LinkedHashMap<String, Object>();
            for (int i = 0; i < childIds.size(); i++) {
                String keyName = resultKeyNames.get(i);
                collection.put(keyName, Result.running());
            }
            parentThread.resultCollection = collection;
        }
    }

    private void notifyChildCompleted(ThreadContext childThread) {
        if (childThread.parentId == null) return;

        ThreadContext parent = threads.get(childThread.parentId);
        if (parent == null) return;

        // Update the live result collection in-place
        // All keys are pre-resolved by the interpreter (no nulls)
        if (parent.resultCollection != null) {
            int idx = parent.childIds.indexOf(childThread.id);
            if (idx >= 0) {
                String key = parent.resultKeyNames.get(idx);
                if (childThread.state == ThreadState.ERROR) {
                    parent.resultCollection.put(key, Result.error(
                        childThread.error != null ? childThread.error.getMessage() : "Unknown thread error"));
                } else {
                    parent.resultCollection.put(key, Result.ok(childThread.result));
                }
            }
        }

        boolean allDone = parent.childCompleted(childThread.id);
        if (allDone) {
            // Build payload with the live collection
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("resultVarName", parent.resultCollectionVarName);
            payload.put("collection", parent.resultCollection);

            // Run final monitor tick
            runFinalMonitor(childThread.parentId);

            // Remove monitor for this parent
            Iterator<MonitorState> it = monitors.iterator();
            while (it.hasNext()) {
                if (it.next().parentThreadId == childThread.parentId) {
                    it.remove();
                }
            }

            // Send collected results back to parent stepper
            parent.collectedResults = payload;
        }
    }

    private void runMonitors() {
        long now = System.currentTimeMillis();
        for (MonitorState monitor : monitors) {
            if (now - monitor.lastRun >= monitor.interval) {
                monitor.lastRun = now;
                try {
                    executeMonitorSync(monitor);
                } catch (Exception e) {
                    visitor.stderr.print(new Object[]{"[MONITOR ERROR] " + e.getMessage()});
                }
            }
        }
    }

    private void executeMonitorSync(MonitorState monitor) {
        ThreadContext parentThread = threads.get(monitor.parentThreadId);
        if (parentThread == null) return;

        // Create a monitor scope with the result collection injected
        Map<String, Object> monitorScope = new LinkedHashMap<String, Object>(
            parentThread.globalSnapshot != null ? parentThread.globalSnapshot : visitor.variables);
        if (parentThread.resultCollectionVarName != null && parentThread.resultCollection != null) {
            monitorScope.put(parentThread.resultCollectionVarName, parentThread.resultCollection);
        }

        // Create a temporary thread context for monitor execution
        ThreadContext monitorThread = new ThreadContext(-1, "monitor", null, monitorScope);
        monitorThread.inMonitorContext = true;

        ThreadContext prevActiveThread = visitor.activeThread;
        visitor.activeThread = monitorThread;

        try {
            visitor.evalBlock(monitor.blockCtx);
        } finally {
            visitor.activeThread = prevActiveThread;
        }
    }

    private void runFinalMonitor(int parentThreadId) {
        for (MonitorState monitor : monitors) {
            if (monitor.parentThreadId == parentThreadId) {
                try {
                    executeMonitorSync(monitor);
                } catch (Exception e) {
                    visitor.stderr.print(new Object[]{"[MONITOR ERROR] " + e.getMessage()});
                }
                return;
            }
        }
    }

    /** Main scheduler loop */
    public Object run(Stepper mainStepper) {
        ThreadContext mainThread = createThread("main", mainStepper, visitor.variables);

        while (hasActiveThreads()) {
            long now = System.currentTimeMillis();

            wakeThreads(now);
            runMonitors();

            ThreadContext thread = selectNextThread();

            if (thread == null) {
                Long sleepTime = getMinSleepRemaining(System.currentTimeMillis());
                if (sleepTime != null) {
                    try {
                        Thread.sleep(Math.min(sleepTime, 50));
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }

                boolean hasWaiting = false;
                for (ThreadContext t : threads.values()) {
                    if (t.state == ThreadState.WAITING) {
                        hasWaiting = true;
                        break;
                    }
                }
                if (hasWaiting) {
                    try { Thread.sleep(1); } catch (InterruptedException e) { break; }
                    continue;
                }

                break;
            }

            currentThreadId = thread.id;
            thread.markRunning();

            // Set active thread on visitor
            visitor.activeThread = thread;

            try {
                // If thread was waiting and just resumed, send collected results
                if (thread.collectedResults != null) {
                    thread.stepper.setSendValue(thread.collectedResults);
                    thread.collectedResults = null;
                }

                StepResult stepResult = thread.stepper.step();
                processStepResult(thread, stepResult);
            } catch (Throwable error) {
                thread.markError(error);
                notifyChildCompleted(thread);

                if (thread.id == 0) {
                    throw (error instanceof RuntimeException) ? (RuntimeException) error : new RuntimeException(error);
                }
                // Print child thread errors to stderr
                visitor.stderr.print(new Object[]{"[THREAD ERROR] " + error.getMessage()});
            }
        }

        // Clear active thread
        visitor.activeThread = null;

        ThreadContext main = threads.get(0);
        if (main != null && main.state == ThreadState.ERROR) {
            Throwable e = main.error;
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
        }
        return main != null ? main.result : null;
    }
}
