package com.propertee.runtime;

import java.util.*;

public class TypeChecker {

    public static String typeOf(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Number) return "number";
        if (value instanceof String) return "string";
        if (value instanceof List) return "array";
        if (value instanceof Map) return "object";
        return "unknown";
    }

    public static boolean isNumber(Object value) {
        return value instanceof Number;
    }

    public static boolean isString(Object value) {
        return value instanceof String;
    }

    public static boolean isBoolean(Object value) {
        return value instanceof Boolean;
    }

    public static boolean isList(Object value) {
        return value instanceof List;
    }

    public static boolean isMap(Object value) {
        return value instanceof Map;
    }

    public static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        return true;
    }

    public static double toDouble(Object value) {
        if (value instanceof Integer) return ((Integer) value).doubleValue();
        if (value instanceof Long) return ((Long) value).doubleValue();
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        throw new ProperTeeError("Cannot convert " + typeOf(value) + " to number");
    }

    public static boolean isInteger(double d) {
        return d == Math.floor(d) && !Double.isInfinite(d);
    }

    /** Box a numeric result: return Integer if whole, Double otherwise */
    public static Object boxNumber(double d) {
        if (isInteger(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
            return (int) d;
        }
        return d;
    }

    /** Format a value for PRINT output, matching JS behavior */
    public static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Integer) return value.toString();
        if (value instanceof Double) {
            double d = (Double) value;
            if (isInteger(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return String.valueOf((int) d);
            }
            return String.valueOf(d);
        }
        if (value instanceof Number) return value.toString();
        if (value instanceof String) return (String) value;
        if (value instanceof List) return formatList((List<?>) value);
        if (value instanceof Map) return formatMap((Map<?, ?>) value);
        return value.toString();
    }

    /** Format for PRINT - strings print without quotes at top level */
    public static String formatForPrint(Object value) {
        return formatValue(value);
    }

    /** Format list like JSON: [ 1, 2, 3 ] or [ 'a', 'b' ] */
    @SuppressWarnings("unchecked")
    public static String formatList(List<?> list) {
        if (list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[ ");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatJsonValue(list.get(i)));
        }
        sb.append(" ]");
        return sb.toString();
    }

    /** Format map like JSON: { "key": value } or {} */
    @SuppressWarnings("unchecked")
    public static String formatMap(Map<?, ?> map) {
        if (map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{ ");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            // Keys are always strings in our representation
            sb.append("\"").append(entry.getKey()).append("\": ");
            sb.append(formatJsonValue(entry.getValue()));
        }
        sb.append(" }");
        return sb.toString();
    }

    /** Format a value as it would appear inside JSON (strings get quotes) */
    public static String formatJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "'" + value + "'";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Integer) return value.toString();
        if (value instanceof Double) {
            double d = (Double) value;
            if (isInteger(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return String.valueOf((int) d);
            }
            return String.valueOf(d);
        }
        if (value instanceof Number) return value.toString();
        if (value instanceof List) return formatList((List<?>) value);
        if (value instanceof Map) return formatMap((Map<?, ?>) value);
        return value.toString();
    }

    /** Format for TO_STRING - like JSON.stringify */
    public static String toStringValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Integer) return value.toString();
        if (value instanceof Double) {
            double d = (Double) value;
            if (isInteger(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return String.valueOf((int) d);
            }
            return String.valueOf(d);
        }
        if (value instanceof Number) return value.toString();
        if (value instanceof String) return (String) value;
        if (value instanceof List) return formatJsonList((List<?>) value);
        if (value instanceof Map) return formatJsonMap((Map<?, ?>) value);
        return value.toString();
    }

    /** JSON stringify for list: ["a","b"] with double quotes */
    public static String formatJsonList(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(jsonStringify(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    /** JSON stringify for map */
    public static String formatJsonMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(jsonStringify(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    public static String jsonStringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + value + "\"";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Integer) return value.toString();
        if (value instanceof Double) {
            double d = (Double) value;
            if (isInteger(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return String.valueOf((int) d);
            }
            return String.valueOf(d);
        }
        if (value instanceof Number) return value.toString();
        if (value instanceof List) return formatJsonList((List<?>) value);
        if (value instanceof Map) return formatJsonMap((Map<?, ?>) value);
        return "\"" + value.toString() + "\"";
    }

    /** Deep copy a value (for global snapshots) */
    @SuppressWarnings("unchecked")
    public static Object deepCopy(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof List) {
            List<Object> original = (List<Object>) value;
            List<Object> copy = new ArrayList<Object>();
            for (Object item : original) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        if (value instanceof Map) {
            Map<String, Object> original = (Map<String, Object>) value;
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> entry : original.entrySet()) {
                copy.put(entry.getKey(), deepCopy(entry.getValue()));
            }
            return copy;
        }
        return value;
    }
}
