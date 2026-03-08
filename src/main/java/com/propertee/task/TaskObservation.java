package com.propertee.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskObservation {
    public String taskId;
    public String status;
    public boolean alive;
    public long elapsedMs;
    public Long lastStdoutAt;
    public Long lastStderrAt;
    public Long lastOutputAgeMs;
    public boolean timeoutExceeded;
    public List<String> healthHints = new ArrayList<String>();

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("taskId", taskId);
        map.put("status", status);
        map.put("alive", alive);
        map.put("elapsedMs", elapsedMs);
        map.put("lastStdoutAt", lastStdoutAt);
        map.put("lastStderrAt", lastStderrAt);
        map.put("lastOutputAgeMs", lastOutputAgeMs);
        map.put("timeoutExceeded", timeoutExceeded);
        map.put("healthHints", new ArrayList<String>(healthHints));
        return map;
    }
}
