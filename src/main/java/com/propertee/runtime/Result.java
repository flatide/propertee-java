package com.propertee.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper for creating result objects used by external built-in functions.
 * Returns {ok: true, value: ...} on success or {ok: false, value: "..."} on error.
 */
public final class Result {

    private Result() {}

    public static Map<String, Object> ok(Object value) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true);
        r.put("value", value);
        return r;
    }

    public static Map<String, Object> error(String message) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", false);
        r.put("value", message);
        return r;
    }
}
