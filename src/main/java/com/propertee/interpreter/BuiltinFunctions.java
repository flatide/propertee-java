package com.propertee.interpreter;

import com.propertee.runtime.ProperTeeError;
import com.propertee.runtime.TypeChecker;
import com.propertee.stepper.SchedulerCommand;

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
                return null;
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
}
