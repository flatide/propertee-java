package com.propertee.interpreter;

import com.propertee.runtime.ProperTeeError;
import com.propertee.runtime.TypeChecker;
import com.propertee.stepper.SchedulerCommand;

import java.text.SimpleDateFormat;
import java.util.*;

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

    public BuiltinFunctions(PrintFunction stdout, PrintFunction stderr) {
        this.stdout = stdout;
        this.stderr = stderr;
        registerDefaults();
    }

    public void setStdout(PrintFunction stdout) { this.stdout = stdout; }
    public PrintFunction getStdout() { return stdout; }
    public PrintFunction getStderr() { return stderr; }

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
}
