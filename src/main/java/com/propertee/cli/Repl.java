package com.propertee.cli;

import com.propertee.interpreter.BuiltinFunctions;
import com.propertee.interpreter.ProperTeeInterpreter;
import com.propertee.parser.ProperTeeParser;
import com.propertee.runtime.TypeChecker;
import com.propertee.scheduler.Scheduler;
import com.propertee.stepper.Stepper;

import java.io.*;
import java.util.*;

public class Repl {
    private final Map<String, Object> properties;
    private final BuiltinFunctions.PrintFunction stdout;
    private final BuiltinFunctions.PrintFunction stderr;
    private final int maxIterations;
    private final String iterationLimitBehavior;

    public Repl(Map<String, Object> properties, BuiltinFunctions.PrintFunction stdout,
                BuiltinFunctions.PrintFunction stderr, int maxIterations, String iterationLimitBehavior) {
        this.properties = properties;
        this.stdout = stdout;
        this.stderr = stderr;
        this.maxIterations = maxIterations;
        this.iterationLimitBehavior = iterationLimitBehavior;
    }

    public void run() {
        System.out.println("ProperTee Interactive Mode");
        System.out.println("Type ProperTee statements. Multi-line blocks auto-detected (do/end, if/end, multi/end).");
        System.out.println("Type .exit to quit, .vars to show variables.\n");

        ProperTeeInterpreter visitor = new ProperTeeInterpreter(properties, stdout, stderr, maxIterations, iterationLimitBehavior);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder buffer = new StringBuilder();
        int depth = 0;

        try {
            while (true) {
                System.out.print(depth > 0 ? "... " : "pt> ");
                System.out.flush();
                String line = reader.readLine();
                if (line == null) break;

                String trimmed = line.trim();
                if (".exit".equals(trimmed)) break;
                if (".vars".equals(trimmed)) {
                    if (visitor.variables.isEmpty()) {
                        System.out.println("(no variables)");
                    } else {
                        for (Map.Entry<String, Object> entry : visitor.variables.entrySet()) {
                            System.out.println("  " + entry.getKey() + " = " + TypeChecker.jsonStringify(entry.getValue()));
                        }
                    }
                    continue;
                }

                if (buffer.length() > 0) buffer.append("\n");
                buffer.append(line);
                depth += countDepth(line);

                if (depth > 0) continue;

                depth = 0;
                String text = buffer.toString();
                buffer = new StringBuilder();

                if (text.trim().isEmpty()) continue;

                List<String> errors = new ArrayList<String>();
                ProperTeeParser.RootContext tree = Main.parseScript(text, errors);
                if (tree == null) {
                    for (String err : errors) System.err.println(err);
                    continue;
                }

                Scheduler scheduler = new Scheduler(visitor);
                Stepper mainStepper = visitor.createRootStepper(tree);

                try {
                    Object result = scheduler.run(mainStepper);
                    if (result != null) {
                        System.out.println("=> " + TypeChecker.formatValue(result));
                    }
                } catch (Exception e) {
                    System.err.println("Runtime error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // exit
        }

        System.out.println("\nBye.");
    }

    private int countDepth(String line) {
        String[] tokens = line.trim().split("\\s+");
        int delta = 0;
        for (String tok : tokens) {
            if ("do".equals(tok) || "if".equals(tok)) delta++;
            if ("end".equals(tok)) delta--;
        }
        return delta;
    }
}
