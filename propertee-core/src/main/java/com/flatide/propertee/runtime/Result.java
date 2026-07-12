package com.flatide.propertee.runtime;

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
        return of("running", Boolean.FALSE, new LinkedHashMap<String, Object>());
    }

    public static Map<String, Object> ok(Object value) {
        return of("done", Boolean.TRUE, value);
    }

    public static Map<String, Object> error(String message) {
        return of("error", Boolean.FALSE, message);
    }

    /** {@code ERR(value)} (spec v0.10.0) — an error Result whose value may be any type (structured errors). */
    public static Map<String, Object> errorValue(Object value) {
        return of("error", Boolean.FALSE, value);
    }

    private static Map<String, Object> of(String status, Boolean ok, Object value) {
        Map<String, Object> r = new TeeResult();   // genuine-Result origin brand (spec v0.10.0)
        r.put("status", status);
        r.put("ok", ok);
        r.put("value", value);
        return r;
    }
}
