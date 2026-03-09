package com.propertee.stepper;

import java.util.*;

public class SchedulerCommand {
    public enum CommandType {
        SLEEP,
        SPAWN_THREADS,
        AWAIT_ASYNC
    }

    private final CommandType type;
    private long duration; // for SLEEP

    // For SPAWN_THREADS
    private List<ThreadSpec> specs;
    private MonitorSpec monitorSpec;
    private Map<String, Object> globalSnapshot;
    private List<String> resultKeyNames;
    private String resultVarName; // the [result] collection variable name (null for fire-and-forget)

    private SchedulerCommand(CommandType type) {
        this.type = type;
    }

    public static SchedulerCommand sleep(long duration) {
        SchedulerCommand cmd = new SchedulerCommand(CommandType.SLEEP);
        cmd.duration = duration;
        return cmd;
    }

    public static SchedulerCommand awaitAsync() {
        return new SchedulerCommand(CommandType.AWAIT_ASYNC);
    }

    public static SchedulerCommand spawnThreads(List<ThreadSpec> specs, MonitorSpec monitorSpec,
                                                  Map<String, Object> globalSnapshot, List<String> resultKeyNames,
                                                  String resultVarName) {
        SchedulerCommand cmd = new SchedulerCommand(CommandType.SPAWN_THREADS);
        cmd.specs = specs;
        cmd.monitorSpec = monitorSpec;
        cmd.globalSnapshot = globalSnapshot;
        cmd.resultKeyNames = resultKeyNames;
        cmd.resultVarName = resultVarName;
        return cmd;
    }

    public CommandType getType() { return type; }
    public long getDuration() { return duration; }
    public List<ThreadSpec> getSpecs() { return specs; }
    public MonitorSpec getMonitorSpec() { return monitorSpec; }
    public Map<String, Object> getGlobalSnapshot() { return globalSnapshot; }
    public List<String> getResultKeyNames() { return resultKeyNames; }
    public String getResultVarName() { return resultVarName; }

    public static class ThreadSpec {
        private final String name;
        private final Stepper stepper;
        private final Map<String, Object> localScope;

        public ThreadSpec(String name, Stepper stepper, Map<String, Object> localScope) {
            this.name = name;
            this.stepper = stepper;
            this.localScope = localScope;
        }

        public String getName() { return name; }
        public Stepper getStepper() { return stepper; }
        public Map<String, Object> getLocalScope() { return localScope; }
    }

    public static class MonitorSpec {
        private final int interval;
        private final Object blockCtx; // ProperTeeParser.BlockContext

        public MonitorSpec(int interval, Object blockCtx) {
            this.interval = interval;
            this.blockCtx = blockCtx;
        }

        public int getInterval() { return interval; }
        public Object getBlockCtx() { return blockCtx; }
    }
}
