package com.propertee.tests;

import com.propertee.cli.Main;
import com.propertee.interpreter.BuiltinFunctions;
import com.propertee.interpreter.ProperTeeInterpreter;
import com.propertee.parser.ProperTeeParser;
import com.propertee.runtime.Result;
import com.propertee.runtime.TypeChecker;
import com.propertee.scheduler.Scheduler;
import com.propertee.stepper.Stepper;

import java.io.*;
import java.util.*;

/**
 * Host application demo for async external functions.
 * Usage: ./gradlew runAsyncDemo --args="path/to/script.pt"
 *    or: ./gradlew runAsyncDemo   (runs the built-in test script)
 */
public class AsyncDemo {

    public static void main(String[] args) {
        String scriptText;

        if (args.length > 0) {
            try {
                byte[] bytes = readFile(args[0]);
                scriptText = new String(bytes, "UTF-8");
            } catch (Exception e) {
                System.err.println("Error reading file: " + e.getMessage());
                return;
            }
        } else {
            // Built-in demo script
            scriptText =
                "// Basic async call\n" +
                "result = SLOW_FETCH(\"test\")\n" +
                "PRINT(result.ok, result.value)\n" +
                "\n" +
                "// Async error\n" +
                "result2 = SLOW_FETCH(\"error\")\n" +
                "PRINT(result2.ok, result2.value)\n" +
                "\n" +
                "// Sequential async calls\n" +
                "r1 = SLOW_COMPUTE(5)\n" +
                "r2 = SLOW_COMPUTE(10)\n" +
                "PRINT(r1.ok, r1.value, r2.ok, r2.value)\n" +
                "\n" +
                "// Async inside multi block\n" +
                "function worker(name) do\n" +
                "    result = SLOW_FETCH(name)\n" +
                "    return result.value\n" +
                "end\n" +
                "\n" +
                "multi r do\n" +
                "    thread a: worker(\"A\")\n" +
                "    thread b: worker(\"B\")\n" +
                "end\n" +
                "PRINT(r.a.value, r.b.value)\n" +
                "\n" +
                "// Async with timeout\n" +
                "result3 = SLOW_TIMEOUT(\"x\")\n" +
                "PRINT(result3.ok, result3.value)\n";
        }

        BuiltinFunctions.PrintFunction stdout = new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] a) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < a.length; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(a[i]);
                }
                System.out.println(sb.toString());
            }
        };
        BuiltinFunctions.PrintFunction stderr = new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] a) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < a.length; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(a[i]);
                }
                System.err.println(sb.toString());
            }
        };

        List<String> errors = new ArrayList<String>();
        ProperTeeParser.RootContext tree = Main.parseScript(scriptText, errors);
        if (tree == null) {
            for (String err : errors) System.err.println(err);
            return;
        }

        ProperTeeInterpreter visitor = new ProperTeeInterpreter(null, stdout, stderr, 1000, "error");

        // Register async external functions
        visitor.builtins.registerExternalAsync("SLOW_FETCH", new BuiltinFunctions.BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                String key = (String) args.get(0);
                try { Thread.sleep(50); } catch (InterruptedException e) { /* ignore */ }
                if ("error".equals(key)) return Result.error("fetch error: error");
                return Result.ok(key + "_data");
            }
        });

        visitor.builtins.registerExternalAsync("SLOW_COMPUTE", new BuiltinFunctions.BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                int n = ((Number) args.get(0)).intValue();
                try { Thread.sleep(30); } catch (InterruptedException e) { /* ignore */ }
                return Result.ok(n * 10);
            }
        });

        visitor.builtins.registerExternalAsync("SLOW_TIMEOUT", new BuiltinFunctions.BuiltinFunction() {
            @Override
            public Object call(List<Object> args) {
                try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
                return Result.ok("should not reach");
            }
        }, 100);

        Scheduler scheduler = new Scheduler(visitor);
        Stepper mainStepper = visitor.createRootStepper(tree);

        try {
            scheduler.run(mainStepper);
        } catch (Exception e) {
            System.err.println("Runtime error: " + e.getMessage());
        } finally {
            visitor.builtins.shutdown();
        }
    }

    private static byte[] readFile(String path) throws IOException {
        FileInputStream fis = new FileInputStream(path);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            fis.close();
        }
    }
}
