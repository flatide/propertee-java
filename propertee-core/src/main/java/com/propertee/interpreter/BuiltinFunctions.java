package com.propertee.interpreter;

import com.propertee.runtime.AsyncPendingException;
import com.propertee.runtime.ProperTeeError;
import com.propertee.runtime.Result;
import com.propertee.runtime.TypeChecker;
import com.propertee.scheduler.ThreadContext;
import com.propertee.stepper.SchedulerCommand;
import com.propertee.platform.PlatformProvider;
import com.propertee.platform.UnsupportedPlatformProvider;
import com.propertee.task.Task;
import com.propertee.task.TaskObservation;
import com.propertee.task.TaskRequest;
import com.propertee.task.TaskRunner;
import com.propertee.task.UnsupportedTaskRunner;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class BuiltinFunctions {


    public interface BuiltinFunction {
        Object call(List<Object> args);
    }

    public interface PrintFunction {
        void print(Object[] args);
    }

    private final Map<String, BuiltinFunction> functions = new LinkedHashMap<String, BuiltinFunction>();
    private PrintFunction stdout;
    private PrintFunction stderr;
    private ProperTeeInterpreter interpreter;
    private ExecutorService asyncExecutor;
    private boolean ownedExecutor = false;
    private final TaskRunner taskRunner;
    private final PlatformProvider platform;
    private final String runId;
    private static final ThreadLocal<Integer> asyncOriginThreadId = new ThreadLocal<Integer>();
    private static final ThreadLocal<String> asyncOriginThreadName = new ThreadLocal<String>();

    public BuiltinFunctions(PrintFunction stdout, PrintFunction stderr) {
        this(stdout, stderr, null, null, null);
    }

    public BuiltinFunctions(PrintFunction stdout, PrintFunction stderr, String runId, TaskRunner taskRunner) {
        this(stdout, stderr, runId, taskRunner, null);
    }

    public BuiltinFunctions(PrintFunction stdout, PrintFunction stderr, String runId, TaskRunner taskRunner, PlatformProvider platform) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.runId = runId != null ? runId : createRunId();
        this.taskRunner = taskRunner != null ? taskRunner : createDefaultTaskRunner();
        this.platform = platform != null ? platform : new UnsupportedPlatformProvider();
        registerDefaults();
    }

    public void setStdout(PrintFunction stdout) { this.stdout = stdout; }
    public PrintFunction getStdout() { return stdout; }
    public PrintFunction getStderr() { return stderr; }
    public String getRunId() { return runId; }
    public TaskRunner getTaskRunner() { return taskRunner; }

    /** @deprecated Use getTaskRunner() */
    @Deprecated
    public TaskRunner getTaskEngine() { return taskRunner; }

    private void registerDefaults() {
        functions.put("PRINT", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                Object[] formatted = new Object[args.size()];
                for (int i = 0; i < args.size(); i++) {
                    formatted[i] = TypeChecker.formatForPrint(args.get(i));
                }
                stdout.print(formatted);
                return new java.util.LinkedHashMap<String, Object>();
            }
        });

        functions.put("SUM", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                double sum = 0;
                for (Object a : args) sum += TypeChecker.toDouble(a);
                return TypeChecker.boxNumber(sum);
            }
        });

        functions.put("MAX", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                double max = Double.NEGATIVE_INFINITY;
                for (Object a : args) {
                    double d = TypeChecker.toDouble(a);
                    if (d > max) max = d;
                }
                return TypeChecker.boxNumber(max);
            }
        });

        functions.put("MIN", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                double min = Double.POSITIVE_INFINITY;
                for (Object a : args) {
                    double d = TypeChecker.toDouble(a);
                    if (d < min) min = d;
                }
                return TypeChecker.boxNumber(min);
            }
        });

        functions.put("ABS", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                return TypeChecker.boxNumber(Math.abs(TypeChecker.toDouble(args.get(0))));
            }
        });

        functions.put("FLOOR", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                return TypeChecker.boxNumber(Math.floor(TypeChecker.toDouble(args.get(0))));
            }
        });

        functions.put("CEIL", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                return TypeChecker.boxNumber(Math.ceil(TypeChecker.toDouble(args.get(0))));
            }
        });

        functions.put("ROUND", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                return TypeChecker.boxNumber(Math.round(TypeChecker.toDouble(args.get(0))));
            }
        });

        functions.put("LEN", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                Object a = args.get(0);
                if (a instanceof List) return ((List<?>) a).size();
                if (a instanceof String) return ((String) a).length();
                if (a instanceof Map) return ((Map<?, ?>) a).size();
                return 0;
            }
        });

        functions.put("TO_NUMBER", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                Object a = args.get(0);
                if (!(a instanceof String)) throw new ProperTeeError("Runtime Error: TO_NUMBER() requires a string argument");
                String s = ((String) a).trim();
                if (s.isEmpty()) throw new ProperTeeError("Runtime Error: TO_NUMBER() cannot convert empty string");
                try {
                    double d = Double.parseDouble(s);
                    return TypeChecker.boxNumber(d);
                } catch (NumberFormatException e) {
                    throw new ProperTeeError("Runtime Error: TO_NUMBER() cannot convert '" + a + "' to number");
                }
            }
        });

        functions.put("TO_STRING", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                return TypeChecker.toStringValue(args.get(0));
            }
        });

        functions.put("SLEEP", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                Object a = args.get(0);
                if (!TypeChecker.isNumber(a)) throw new ProperTeeError("Runtime Error: SLEEP() requires a number argument");
                double ms = TypeChecker.toDouble(a);
                if (ms < 0) throw new ProperTeeError("Runtime Error: SLEEP() duration cannot be negative");
                // Return a special marker that the interpreter will recognize
                return SchedulerCommand.sleep((long) ms);
            }
        });

        functions.put("PUSH", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                Object first = args.get(0);
                if (!(first instanceof List)) throw new ProperTeeError("Runtime Error: PUSH() first argument must be an array");
                List<Object> arr = new ArrayList<Object>((List<Object>) first);
                for (int i = 1; i < args.size(); i++) {
                    arr.add(args.get(i));
                }
                return arr;
            }
        });

        functions.put("POP", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                Object first = args.get(0);
                if (!(first instanceof List)) throw new ProperTeeError("Runtime Error: POP() argument must be an array");
                List<Object> arr = (List<Object>) first;
                if (arr.isEmpty()) throw new ProperTeeError("Runtime Error: POP() cannot pop from empty array");
                List<Object> result = new ArrayList<Object>(arr.subList(0, arr.size() - 1));
                return result;
            }
        });

        functions.put("CONCAT", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                List<Object> result = new ArrayList<Object>();
                for (Object a : args) {
                    if (!(a instanceof List)) throw new ProperTeeError("Runtime Error: CONCAT() all arguments must be arrays");
                    result.addAll((List<Object>) a);
                }
                return result;
            }
        });

        functions.put("SLICE", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                Object first = args.get(0);
                if (!(first instanceof List)) throw new ProperTeeError("Runtime Error: SLICE() first argument must be an array");
                if (!TypeChecker.isNumber(args.get(1))) throw new ProperTeeError("Runtime Error: SLICE() second argument must be a number");
                List<Object> arr = (List<Object>) first;
                int start = (int) TypeChecker.toDouble(args.get(1)) - 1; // 1-based to 0-based
                if (args.size() > 2) {
                    if (!TypeChecker.isNumber(args.get(2))) throw new ProperTeeError("Runtime Error: SLICE() third argument must be a number");
                    int end = (int) TypeChecker.toDouble(args.get(2));
                    return new ArrayList<Object>(arr.subList(Math.max(0, start), Math.min(arr.size(), end)));
                }
                return new ArrayList<Object>(arr.subList(Math.max(0, start), arr.size()));
            }
        });

        functions.put("CHARS", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                Object a = args.get(0);
                if (!(a instanceof String)) throw new ProperTeeError("Runtime Error: CHARS() requires a string argument");
                String s = (String) a;
                List<Object> result = new ArrayList<Object>();
                for (int i = 0; i < s.length(); i++) {
                    result.add(String.valueOf(s.charAt(i)));
                }
                return result;
            }
        });

        functions.put("SPLIT", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: SPLIT() first argument must be a string");
                if (!(args.get(1) instanceof String)) throw new ProperTeeError("Runtime Error: SPLIT() second argument must be a string");
                String s = (String) args.get(0);
                String delim = (String) args.get(1);
                String[] parts = s.split(java.util.regex.Pattern.quote(delim), -1);
                List<Object> result = new ArrayList<Object>();
                for (String part : parts) {
                    result.add(part);
                }
                return result;
            }
        });

        functions.put("JOIN", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof List)) throw new ProperTeeError("Runtime Error: JOIN() first argument must be an array");
                String sep = "";
                if (args.size() > 1) {
                    if (!(args.get(1) instanceof String)) throw new ProperTeeError("Runtime Error: JOIN() second argument must be a string");
                    sep = (String) args.get(1);
                }
                List<Object> arr = (List<Object>) args.get(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.size(); i++) {
                    if (i > 0) sb.append(sep);
                    sb.append(TypeChecker.formatValue(arr.get(i)));
                }
                return sb.toString();
            }
        });

        functions.put("SUBSTRING", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: SUBSTRING() first argument must be a string");
                if (!TypeChecker.isNumber(args.get(1))) throw new ProperTeeError("Runtime Error: SUBSTRING() second argument must be a number");
                String s = (String) args.get(0);
                int start = (int) TypeChecker.toDouble(args.get(1)) - 1; // 1-based
                if (args.size() > 2) {
                    if (!TypeChecker.isNumber(args.get(2))) throw new ProperTeeError("Runtime Error: SUBSTRING() third argument must be a number");
                    int length = (int) TypeChecker.toDouble(args.get(2));
                    return s.substring(start, Math.min(s.length(), start + length));
                }
                return s.substring(start);
            }
        });

        functions.put("UPPERCASE", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: UPPERCASE() requires a string argument");
                return ((String) args.get(0)).toUpperCase();
            }
        });

        functions.put("LOWERCASE", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: LOWERCASE() requires a string argument");
                return ((String) args.get(0)).toLowerCase();
            }
        });

        functions.put("TRIM", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: TRIM() requires a string argument");
                return ((String) args.get(0)).trim();
            }
        });

        functions.put("HAS_KEY", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                Object obj = args.get(0);
                Object key = args.get(1);
                if (!(obj instanceof Map))
                    throw new ProperTeeError("Runtime Error: HAS_KEY() first argument must be an object");
                if (!(key instanceof String))
                    throw new ProperTeeError("Runtime Error: HAS_KEY() second argument must be a string");
                return ((Map<String, Object>) obj).containsKey((String) key);
            }
        });

        functions.put("KEYS", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                Object obj = args.get(0);
                if (!(obj instanceof Map))
                    throw new ProperTeeError("Runtime Error: KEYS() argument must be an object");
                return new ArrayList<Object>(((Map<String, Object>) obj).keySet());
            }
        });

        functions.put("SORT", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                Object first = args.get(0);
                if (!(first instanceof List))
                    throw new ProperTeeError("Runtime Error: SORT() requires an array argument");
                List<Object> arr = new ArrayList<Object>((List<Object>) first);
                if (arr.size() <= 1) return arr;
                validateHomogeneous(arr, "SORT");
                Collections.sort(arr, new Comparator<Object>() {
                    @Override
                    public int compare(Object a, Object b) {
                        return compareValues(a, b);
                    }
                });
                return arr;
            }
        });

        functions.put("SORT_DESC", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                Object first = args.get(0);
                if (!(first instanceof List))
                    throw new ProperTeeError("Runtime Error: SORT_DESC() requires an array argument");
                List<Object> arr = new ArrayList<Object>((List<Object>) first);
                if (arr.size() <= 1) return arr;
                validateHomogeneous(arr, "SORT_DESC");
                Collections.sort(arr, new Comparator<Object>() {
                    @Override
                    public int compare(Object a, Object b) {
                        return compareValues(b, a);
                    }
                });
                return arr;
            }
        });

        functions.put("SORT_BY", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                Object first = args.get(0);
                if (!(first instanceof List))
                    throw new ProperTeeError("Runtime Error: SORT_BY() requires an array argument");
                if (!(args.get(1) instanceof String))
                    throw new ProperTeeError("Runtime Error: SORT_BY() second argument must be a string key");
                final String key = (String) args.get(1);
                List<Object> arr = new ArrayList<Object>((List<Object>) first);
                if (arr.size() <= 1) return arr;
                validateKeyExists(arr, key, "SORT_BY");
                Collections.sort(arr, new Comparator<Object>() {
                    @Override
                    public int compare(Object a, Object b) {
                        return compareValues(getMapValue(a, key), getMapValue(b, key));
                    }
                });
                return arr;
            }
        });

        functions.put("SORT_BY_DESC", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                Object first = args.get(0);
                if (!(first instanceof List))
                    throw new ProperTeeError("Runtime Error: SORT_BY_DESC() requires an array argument");
                if (!(args.get(1) instanceof String))
                    throw new ProperTeeError("Runtime Error: SORT_BY_DESC() second argument must be a string key");
                final String key = (String) args.get(1);
                List<Object> arr = new ArrayList<Object>((List<Object>) first);
                if (arr.size() <= 1) return arr;
                validateKeyExists(arr, key, "SORT_BY_DESC");
                Collections.sort(arr, new Comparator<Object>() {
                    @Override
                    public int compare(Object a, Object b) {
                        return compareValues(getMapValue(b, key), getMapValue(a, key));
                    }
                });
                return arr;
            }
        });

        functions.put("REVERSE", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                Object first = args.get(0);
                if (!(first instanceof List))
                    throw new ProperTeeError("Runtime Error: REVERSE() requires an array argument");
                List<Object> arr = new ArrayList<Object>((List<Object>) first);
                Collections.reverse(arr);
                return arr;
            }
        });

        final Random rng = new Random();
        functions.put("RANDOM", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.isEmpty()) {
                    return rng.nextDouble();
                } else if (args.size() == 1) {
                    if (!TypeChecker.isNumber(args.get(0)))
                        throw new ProperTeeError("Runtime Error: RANDOM() argument must be a number");
                    int max = (int) TypeChecker.toDouble(args.get(0));
                    if (max <= 0) throw new ProperTeeError("Runtime Error: RANDOM() max must be positive");
                    return rng.nextInt(max);
                } else {
                    if (!TypeChecker.isNumber(args.get(0)) || !TypeChecker.isNumber(args.get(1)))
                        throw new ProperTeeError("Runtime Error: RANDOM() arguments must be numbers");
                    int min = (int) TypeChecker.toDouble(args.get(0));
                    int max = (int) TypeChecker.toDouble(args.get(1));
                    if (min > max) throw new ProperTeeError("Runtime Error: RANDOM() min cannot be greater than max");
                    return min + rng.nextInt(max - min + 1);
                }
            }
        });

        functions.put("MILTIME", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                return TypeChecker.boxNumber((double) System.currentTimeMillis());
            }
        });

        functions.put("DATE", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            }
        });

        functions.put("TIME", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                return new SimpleDateFormat("HH:mm:ss").format(new Date());
            }
        });

        // --- String matching ---

        functions.put("CONTAINS", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: CONTAINS() first argument must be a string");
                if (!(args.get(1) instanceof String)) throw new ProperTeeError("Runtime Error: CONTAINS() second argument must be a string");
                return ((String) args.get(0)).contains((String) args.get(1));
            }
        });

        functions.put("STARTS_WITH", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: STARTS_WITH() first argument must be a string");
                if (!(args.get(1) instanceof String)) throw new ProperTeeError("Runtime Error: STARTS_WITH() second argument must be a string");
                return ((String) args.get(0)).startsWith((String) args.get(1));
            }
        });

        functions.put("ENDS_WITH", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: ENDS_WITH() first argument must be a string");
                if (!(args.get(1) instanceof String)) throw new ProperTeeError("Runtime Error: ENDS_WITH() second argument must be a string");
                return ((String) args.get(0)).endsWith((String) args.get(1));
            }
        });

        functions.put("MATCHES", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: MATCHES() first argument must be a string");
                if (!(args.get(1) instanceof String)) throw new ProperTeeError("Runtime Error: MATCHES() second argument must be a string");
                try {
                    return Pattern.compile((String) args.get(1)).matcher((String) args.get(0)).find();
                } catch (PatternSyntaxException e) {
                    throw new ProperTeeError("Runtime Error: MATCHES() invalid regex pattern: " + e.getMessage());
                }
            }
        });

        functions.put("REGEX_FIND", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: REGEX_FIND() first argument must be a string");
                if (!(args.get(1) instanceof String)) throw new ProperTeeError("Runtime Error: REGEX_FIND() second argument must be a string");
                try {
                    Matcher matcher = Pattern.compile((String) args.get(1)).matcher((String) args.get(0));
                    if (!matcher.find()) {
                        return new LinkedHashMap<String, Object>();
                    }
                    List<Object> groups = new ArrayList<Object>();
                    for (int i = 0; i <= matcher.groupCount(); i++) {
                        String g = matcher.group(i);
                        groups.add(g != null ? g : "");
                    }
                    return groups;
                } catch (PatternSyntaxException e) {
                    throw new ProperTeeError("Runtime Error: REGEX_FIND() invalid regex pattern: " + e.getMessage());
                }
            }
        });

        functions.put("REPLACE", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof String)) throw new ProperTeeError("Runtime Error: REPLACE() first argument must be a string");
                if (!(args.get(1) instanceof String)) throw new ProperTeeError("Runtime Error: REPLACE() second argument must be a string");
                if (!(args.get(2) instanceof String)) throw new ProperTeeError("Runtime Error: REPLACE() third argument must be a string");
                return ((String) args.get(0)).replace((String) args.get(1), (String) args.get(2));
            }
        });

        // --- Map extensions ---

        functions.put("VALUES", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                Object obj = args.get(0);
                if (!(obj instanceof Map)) throw new ProperTeeError("Runtime Error: VALUES() argument must be an object");
                return new ArrayList<Object>(((Map<String, Object>) obj).values());
            }
        });

        functions.put("ENTRIES", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                Object obj = args.get(0);
                if (!(obj instanceof Map)) throw new ProperTeeError("Runtime Error: ENTRIES() argument must be an object");
                Map<String, Object> map = (Map<String, Object>) obj;
                List<Object> entries = new ArrayList<Object>();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    Map<String, Object> e = new LinkedHashMap<String, Object>();
                    e.put("key", entry.getKey());
                    e.put("value", entry.getValue());
                    entries.add(e);
                }
                return entries;
            }
        });

        functions.put("MERGE", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof Map)) throw new ProperTeeError("Runtime Error: MERGE() first argument must be an object");
                if (!(args.get(1) instanceof Map)) throw new ProperTeeError("Runtime Error: MERGE() second argument must be an object");
                Map<String, Object> result = new LinkedHashMap<String, Object>((Map<String, Object>) args.get(0));
                result.putAll((Map<String, Object>) args.get(1));
                return result;
            }
        });

        functions.put("REMOVE_KEY", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                if (!(args.get(0) instanceof Map)) throw new ProperTeeError("Runtime Error: REMOVE_KEY() first argument must be an object");
                if (!(args.get(1) instanceof String)) throw new ProperTeeError("Runtime Error: REMOVE_KEY() second argument must be a string");
                Map<String, Object> result = new LinkedHashMap<String, Object>((Map<String, Object>) args.get(0));
                result.remove((String) args.get(1));
                return result;
            }
        });

        // --- Type ---

        functions.put("TYPE_OF", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                return TypeChecker.typeOf(args.get(0));
            }
        });

        // --- Environment (delegates to PlatformProvider) ---

        registerExternal("ENV", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.isEmpty() || !(args.get(0) instanceof String))
                    throw new ProperTeeError("Runtime Error: ENV() requires a string argument");
                String value = platform.getEnv((String) args.get(0));
                if (value != null) {
                    return value;
                }
                if (args.size() >= 2) {
                    return args.get(1);
                }
                return new LinkedHashMap<String, Object>();
            }
        });

        // --- JSON ---

        registerExternal("JSON_PARSE", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.isEmpty() || !(args.get(0) instanceof String))
                    return Result.error("JSON_PARSE() requires a string argument");
                JsonElement element = JsonParser.parseString((String) args.get(0));
                return Result.ok(jsonToProperTee(element));
            }
        });

        functions.put("JSON_FORMAT", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                return new Gson().toJson(properTeeToJson(args.get(0)));
            }
        });

        // --- File I/O (delegates to PlatformProvider) ---

        registerExternal("FILE_EXISTS", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.isEmpty() || !(args.get(0) instanceof String))
                    throw new ProperTeeError("Runtime Error: FILE_EXISTS() requires a string argument");
                return platform.fileExists((String) args.get(0));
            }
        });

        registerExternal("FILE_INFO", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.isEmpty() || !(args.get(0) instanceof String))
                    return Result.error("FILE_INFO() requires a string argument");
                PlatformProvider.FileInfo info = platform.fileInfo((String) args.get(0));
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                map.put("type", info.type);
                map.put("size", TypeChecker.boxNumber((double) info.size));
                map.put("modified", TypeChecker.boxNumber((double) info.modified));
                return Result.ok(map);
            }
        });

        registerExternal("READ_LINES", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.isEmpty() || !(args.get(0) instanceof String))
                    return Result.error("READ_LINES() first argument must be a string path");
                int start = 1;
                int count = Integer.MAX_VALUE;
                if (args.size() >= 2 && TypeChecker.isNumber(args.get(1))) {
                    start = (int) TypeChecker.toDouble(args.get(1));
                }
                if (args.size() >= 3 && TypeChecker.isNumber(args.get(2))) {
                    count = (int) TypeChecker.toDouble(args.get(2));
                }
                if (start < 1) return Result.error("READ_LINES() start must be >= 1");
                List<String> lines = platform.readLines((String) args.get(0), start, count);
                return Result.ok(new ArrayList<Object>(lines));
            }
        });

        registerExternal("WRITE_FILE", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.size() < 2 || !(args.get(0) instanceof String) || !(args.get(1) instanceof String))
                    return Result.error("WRITE_FILE() requires (path, content) string arguments");
                platform.writeFile((String) args.get(0), (String) args.get(1));
                return Result.ok(new LinkedHashMap<String, Object>());
            }
        });

        registerExternal("WRITE_LINES", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                if (args.size() < 2 || !(args.get(0) instanceof String) || !(args.get(1) instanceof List))
                    return Result.error("WRITE_LINES() requires (path, lines) arguments");
                List<String> lines = new ArrayList<String>();
                for (Object item : (List<Object>) args.get(1)) {
                    lines.add(TypeChecker.toStringValue(item));
                }
                platform.writeLines((String) args.get(0), lines);
                return Result.ok(new LinkedHashMap<String, Object>());
            }
        });

        registerExternal("APPEND_FILE", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.size() < 2 || !(args.get(0) instanceof String) || !(args.get(1) instanceof String))
                    return Result.error("APPEND_FILE() requires (path, content) string arguments");
                platform.appendFile((String) args.get(0), (String) args.get(1));
                return Result.ok(new LinkedHashMap<String, Object>());
            }
        });

        registerExternal("MKDIR", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.isEmpty() || !(args.get(0) instanceof String))
                    return Result.error("MKDIR() requires a string argument");
                platform.mkdir((String) args.get(0));
                return Result.ok(new LinkedHashMap<String, Object>());
            }
        });

        registerExternal("LIST_DIR", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.isEmpty() || !(args.get(0) instanceof String))
                    return Result.error("LIST_DIR() requires a string argument");
                List<PlatformProvider.FileEntry> entries = platform.listDir((String) args.get(0));
                List<Object> result = new ArrayList<Object>();
                for (PlatformProvider.FileEntry entry : entries) {
                    Map<String, Object> map = new LinkedHashMap<String, Object>();
                    map.put("name", entry.name);
                    map.put("type", entry.type);
                    map.put("size", TypeChecker.boxNumber((double) entry.size));
                    result.add(map);
                }
                return Result.ok(result);
            }
        });

        registerExternal("DELETE_FILE", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.isEmpty() || !(args.get(0) instanceof String))
                    return Result.error("DELETE_FILE() requires a string argument");
                platform.deleteFile((String) args.get(0));
                return Result.ok(new LinkedHashMap<String, Object>());
            }
        });

        // SHELL_CTX — sync, creates a context config object
        registerExternal("SHELL_CTX", new BuiltinFunction() {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(List<Object> args) {
                if (args.isEmpty()) {
                    return com.propertee.runtime.Result.error("SHELL_CTX() requires at least 1 argument (cwd)");
                }
                Object cwdArg = args.get(0);
                if (!(cwdArg instanceof String)) {
                    return com.propertee.runtime.Result.error("SHELL_CTX() first argument must be a string (directory path)");
                }
                String cwd = (String) cwdArg;
                File dir = new File(cwd);
                if (!dir.exists() || !dir.isDirectory()) {
                    return com.propertee.runtime.Result.error("Directory does not exist: " + cwd);
                }

                Map<String, Object> envMap = new LinkedHashMap<String, Object>();
                if (args.size() >= 2) {
                    Object envArg = args.get(1);
                    if (!(envArg instanceof Map)) {
                        return com.propertee.runtime.Result.error("SHELL_CTX() second argument must be an object (environment variables)");
                    }
                    Map<String, Object> rawEnv = (Map<String, Object>) envArg;
                    for (Map.Entry<String, Object> entry : rawEnv.entrySet()) {
                        envMap.put(entry.getKey(), TypeChecker.toStringValue(entry.getValue()));
                    }
                }

                Map<String, Object> ctx = new LinkedHashMap<String, Object>();
                ctx.put("cwd", cwd);
                ctx.put("env", envMap);
                if (args.size() >= 3) {
                    Object timeoutArg = args.get(2);
                    if (!TypeChecker.isNumber(timeoutArg)) {
                        return com.propertee.runtime.Result.error("SHELL_CTX() third argument must be a number (timeout ms)");
                    }
                    ctx.put("timeout", timeoutArg);
                }
                return com.propertee.runtime.Result.ok(ctx);
            }
        });

        // SHELL — async, executes shell commands via TaskEngine
        registerExternalAsync("SHELL", new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (args.isEmpty()) {
                    return Result.error("SHELL() requires at least 1 argument");
                }

                TaskRequest request = buildTaskRequestFromShellArgs(args);
                Task task = taskRunner.execute(request);

                try {
                    Task completed = taskRunner.waitForCompletion(task.taskId, 0);
                    if (completed == null) {
                        return Result.error("Unknown task: " + task.taskId);
                    }
                    return buildTaskResult(completed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Result.error("interrupted");
                }
            }
        });
    }

    public boolean has(String name) {
        return functions.containsKey(name);
    }

    public BuiltinFunction get(String name) {
        return functions.get(name);
    }

    public void register(String name, BuiltinFunction func) {
        functions.put(name, func);
    }

    /**
     * Register an external built-in function with automatic error wrapping.
     * The function can return Result.ok(value) or Result.error(msg) explicitly,
     * or throw an exception which is automatically caught and wrapped as
     * {ok: false, value: "error message"}.
     */
    private static void validateHomogeneous(List<Object> arr, String funcName) {
        boolean allNumbers = true;
        boolean allStrings = true;
        for (Object item : arr) {
            if (!(item instanceof Number)) allNumbers = false;
            if (!(item instanceof String)) allStrings = false;
        }
        if (!allNumbers && !allStrings) {
            throw new ProperTeeError("Runtime Error: " + funcName + "() requires all elements to be the same type (number or string)");
        }
    }

    private static void validateKeyExists(List<Object> arr, String key, String funcName) {
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (!(item instanceof Map)) {
                throw new ProperTeeError("Runtime Error: " + funcName + "() requires an array of objects");
            }
            if (!((Map<?, ?>) item).containsKey(key)) {
                throw new ProperTeeError("Runtime Error: Property '" + key + "' does not exist in array element at index " + (i + 1));
            }
        }
    }

    private static Object getMapValue(Object obj, String key) {
        return ((Map<?, ?>) obj).get(key);
    }

    private static int compareValues(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            double da = ((Number) a).doubleValue();
            double db = ((Number) b).doubleValue();
            return Double.compare(da, db);
        }
        return ((String) a).compareTo((String) b);
    }

    public void registerExternal(String name, final BuiltinFunction func) {
        functions.put(name, new BuiltinFunction() {
            @Override
            public Object call(java.util.List<Object> args) {
                try {
                    return func.call(args);
                } catch (Exception e) {
                    return com.propertee.runtime.Result.error(e.getMessage());
                }
            }
        });
    }

    // --- Async external function support ---

    public void setInterpreter(ProperTeeInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    public void setAsyncExecutor(ExecutorService executor) {
        this.asyncExecutor = executor;
    }

    private ExecutorService getOrCreateAsyncExecutor() {
        if (asyncExecutor == null) {
            asyncExecutor = Executors.newCachedThreadPool();
            ownedExecutor = true;
        }
        return asyncExecutor;
    }

    public void shutdown() {
        if (ownedExecutor && asyncExecutor != null) {
            asyncExecutor.shutdownNow();
            asyncExecutor = null;
            ownedExecutor = false;
        }
    }

    private static TaskRunner createDefaultTaskRunner() {
        return new UnsupportedTaskRunner();
    }

    private static String createRunId() {
        return "run-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date()) +
            "-" + Integer.toHexString((int) (System.nanoTime() & 0xffff));
    }

    @SuppressWarnings("unchecked")
    private TaskRequest buildTaskRequestFromShellArgs(List<Object> args) {
        TaskRequest request = createBaseTaskRequest();
        request.mergeErrorToStdout = true;

        if (args.size() == 1) {
            if (!(args.get(0) instanceof String)) {
                throw new ProperTeeError("SHELL() argument must be a string command");
            }
            request.command = (String) args.get(0);
            return request;
        }

        // SHELL(command, options) — command is String, options is Map with timeout/env/cwd
        if (args.get(0) instanceof String) {
            request.command = (String) args.get(0);
            if (!(args.get(1) instanceof Map)) {
                throw new ProperTeeError("SHELL() second argument must be an options object (e.g. {\"timeout\": 5000})");
            }
            applyContextToRequest((Map<String, Object>) args.get(1), request, "SHELL");
            return request;
        }

        // SHELL(ctx, command) — ctx is Map from SHELL_CTX(), command is String
        if (!(args.get(0) instanceof Map)) {
            throw new ProperTeeError("SHELL() first argument must be a string command or context object from SHELL_CTX()");
        }
        Map<String, Object> ctx = unwrapContextMap((Map<String, Object>) args.get(0), "SHELL");
        if (!(args.get(1) instanceof String)) {
            throw new ProperTeeError("SHELL() second argument must be a string command");
        }

        request.command = (String) args.get(1);
        applyContextToRequest(ctx, request, "SHELL");
        return request;
    }

    private TaskRequest createBaseTaskRequest() {
        TaskRequest request = new TaskRequest();
        request.runId = runId;
        Integer originId = asyncOriginThreadId.get();
        String originName = asyncOriginThreadName.get();
        if (originId != null) {
            request.threadId = originId;
            request.threadName = originName;
        } else if (interpreter != null && interpreter.activeThread != null) {
            request.threadId = Integer.valueOf(interpreter.activeThread.id);
            request.threadName = interpreter.activeThread.name;
        }
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapContextMap(Map<String, Object> ctx, String funcName) {
        if (ctx.containsKey("ok") && ctx.containsKey("value")) {
            Object okVal = ctx.get("ok");
            if (okVal instanceof Boolean && !((Boolean) okVal).booleanValue()) {
                throw new ProperTeeError(funcName + "() received a failed context: " + ctx.get("value"));
            }
            Object inner = ctx.get("value");
            if (inner instanceof Map) {
                return (Map<String, Object>) inner;
            }
        }
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private void applyContextToRequest(Map<String, Object> ctx, TaskRequest request, String funcName) {
        Object cwdVal = ctx.get("cwd");
        if (cwdVal instanceof String) {
            File dir = new File((String) cwdVal);
            if (!dir.exists() || !dir.isDirectory()) {
                throw new ProperTeeError(funcName + "() directory does not exist: " + cwdVal);
            }
            request.cwd = dir.getAbsolutePath();
        }

        Object timeoutVal = ctx.get("timeout");
        if (timeoutVal != null) {
            if (!TypeChecker.isNumber(timeoutVal)) {
                throw new ProperTeeError(funcName + "() timeout must be a number");
            }
            request.timeoutMs = (long) TypeChecker.toDouble(timeoutVal);
        }

        Object envVal = ctx.get("env");
        if (envVal != null) {
            if (!(envVal instanceof Map)) {
                throw new ProperTeeError(funcName + "() env must be an object");
            }
            Map<String, Object> rawEnv = (Map<String, Object>) envVal;
            for (Map.Entry<String, Object> entry : rawEnv.entrySet()) {
                request.env.put(entry.getKey(), TypeChecker.toStringValue(entry.getValue()));
            }
        }
    }

    private Object buildTaskResult(Task task) {
        TaskObservation observation = taskRunner.observe(task.taskId);
        if (observation == null) {
            return Result.error("Unknown task: " + task.taskId);
        }
        if (observation.alive) {
            return Result.running();
        }

        String output = normalizeOutput(taskRunner.getCombinedOutput(task.taskId));
        Integer exitCode = taskRunner.getExitCode(task.taskId);
        if (exitCode != null && exitCode.intValue() == 0) {
            return Result.ok(output);
        }
        if (output.length() == 0) {
            if ("killed".equals(observation.status)) {
                output = "killed";
            } else if (exitCode != null) {
                output = "Task failed with exitCode " + exitCode;
            } else {
                output = observation.status;
            }
        }
        return Result.error(output);
    }

    private String normalizeOutput(String output) {
        if (output == null) return "";
        while (output.endsWith("\n") || output.endsWith("\r")) {
            output = output.substring(0, output.length() - 1);
        }
        return output;
    }

    public void registerExternalAsync(final String name, final BuiltinFunction func) {
        registerExternalAsync(name, func, 0);
    }

    public void registerExternalAsync(final String name, final BuiltinFunction func, final long timeoutMs) {
        registerExternalAsync(name, func, timeoutMs, true);
    }

    public void registerExternalAsync(final String name, final BuiltinFunction func, final long timeoutMs,
                                      final boolean cacheEnabled) {
        functions.put(name, new BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                if (interpreter == null || interpreter.activeThread == null) {
                    throw new ProperTeeError("Runtime Error: Async function '" + name + "' can only be called within the scheduler");
                }
                ThreadContext thread = interpreter.activeThread;
                if (thread.inMonitorContext) {
                    throw new ProperTeeError("Runtime Error: Async function '" + name + "' cannot be called in monitor blocks");
                }

                // Build cache key
                String cacheKey = buildAsyncCacheKey(name, args);

                // Check cache first
                if (cacheEnabled && thread.asyncResultCache.containsKey(cacheKey)) {
                    return thread.asyncResultCache.get(cacheKey);
                }

                // Deep-copy args for thread safety
                final List<Object> safeCopyArgs = new ArrayList<Object>();
                for (Object arg : args) {
                    safeCopyArgs.add(TypeChecker.deepCopy(arg));
                }

                // Capture thread info on scheduler thread for use in executor
                final Integer originThreadId = Integer.valueOf(thread.id);
                final String originThreadName = thread.name;

                // Submit to executor
                ExecutorService executor = getOrCreateAsyncExecutor();
                Future<Object> future = executor.submit(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        asyncOriginThreadId.set(originThreadId);
                        asyncOriginThreadName.set(originThreadName);
                        try {
                            return func.call(safeCopyArgs);
                        } catch (Exception e) {
                            return com.propertee.runtime.Result.error(e.getMessage());
                        } finally {
                            asyncOriginThreadId.remove();
                            asyncOriginThreadName.remove();
                        }
                    }
                });

                thread.asyncFuture = future;
                thread.asyncCacheKey = cacheEnabled ? cacheKey : null;
                thread.asyncTimeoutMs = timeoutMs;
                thread.asyncSubmitTime = System.currentTimeMillis();
                throw new AsyncPendingException();
            }
        });
    }

    // --- JSON conversion helpers ---

    private static Object jsonToProperTee(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new LinkedHashMap<String, Object>();
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) return prim.getAsBoolean();
            if (prim.isNumber()) return TypeChecker.boxNumber(prim.getAsDouble());
            return prim.getAsString();
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            List<Object> result = new ArrayList<Object>();
            for (int i = 0; i < arr.size(); i++) {
                result.add(jsonToProperTee(arr.get(i)));
            }
            return result;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                result.put(entry.getKey(), jsonToProperTee(entry.getValue()));
            }
            return result;
        }
        return new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    private static JsonElement properTeeToJson(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Boolean) return new JsonPrimitive((Boolean) value);
        if (value instanceof Integer) return new JsonPrimitive((Integer) value);
        if (value instanceof Double) {
            double d = (Double) value;
            if (TypeChecker.isInteger(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return new JsonPrimitive((int) d);
            }
            return new JsonPrimitive(d);
        }
        if (value instanceof Number) return new JsonPrimitive((Number) value);
        if (value instanceof String) return new JsonPrimitive((String) value);
        if (value instanceof List) {
            JsonArray arr = new JsonArray();
            for (Object item : (List<Object>) value) {
                arr.add(properTeeToJson(item));
            }
            return arr;
        }
        if (value instanceof Map) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                obj.add(entry.getKey(), properTeeToJson(entry.getValue()));
            }
            return obj;
        }
        return JsonNull.INSTANCE;
    }

    private String buildAsyncCacheKey(String name, List<Object> args) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("|");
        if (interpreter != null) {
            String callSite = interpreter.getCurrentBuiltinCallSiteKey();
            if (callSite != null) {
                sb.append(callSite);
            }
        }
        sb.append("|").append(TypeChecker.formatValue(args));
        return sb.toString();
    }
}
