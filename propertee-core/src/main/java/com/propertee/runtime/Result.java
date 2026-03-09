package com.propertee.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper for creating result objects used by external built-in functions and thread results.
 * Returns {status: "done", ok: true, value: ...} on success,
 * {status: "error", ok: false, value: "..."} on error,
 * or {status: "running", ok: false, value: {}} for running threads.
 */
public final class Result {

    private Result() {}

    public static Map<String, Object> running() {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("status", "running");
        r.put("ok", false);
        r.put("value", new LinkedHashMap<String, Object>());
        return r;
    }

    public static Map<String, Object> ok(Object value) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("status", "done");
        r.put("ok", true);
        r.put("value", value);
        return r;
    }

    public static Map<String, Object> error(String message) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("status", "error");
        r.put("ok", false);
        r.put("value", message);
        return r;
    }
}
