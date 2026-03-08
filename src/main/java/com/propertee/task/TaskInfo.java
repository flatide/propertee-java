package com.propertee.task;

import java.util.List;

public class TaskInfo {
    public String taskId;
    public String runId;
    public Integer threadId;
    public String threadName;
    public String command;
    public int pid;
    public int pgid;
    public String status;
    public boolean alive;
    public boolean archived;
    public long elapsedMs;
    public Long lastStdoutAt;
    public Long lastStderrAt;
    public Long lastOutputAgeMs;
    public Integer exitCode;
    public String cwd;
    public String hostInstanceId;
    public List<String> healthHints;
}
