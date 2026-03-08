package com.propertee.task;

import java.util.LinkedHashMap;
import java.util.Map;

public class TaskRequest {
    public String runId;
    public Integer threadId;
    public String threadName;
    public String command;
    public long timeoutMs;
    public String cwd;
    public Map<String, String> env = new LinkedHashMap<String, String>();
    public boolean mergeErrorToStdout;
}
