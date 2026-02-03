package com.propertee.cli;

import com.propertee.interpreter.BuiltinFunctions;
import com.propertee.interpreter.ProperTeeInterpreter;
import com.propertee.parser.ProperTeeLexer;
import com.propertee.parser.ProperTeeParser;
import com.propertee.runtime.TypeChecker;
import com.propertee.scheduler.Scheduler;
import com.propertee.stepper.Stepper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.antlr.v4.runtime.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

public class Main {

    private static class ErrorCollector extends BaseErrorListener {
        List<String> errors = new ArrayList<String>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
            errors.add("Line " + line + ":" + charPositionInLine + " - " + msg);
        }
    }

    public static ProperTeeParser.RootContext parseScript(String scriptText, List<String> errors) {
        ANTLRInputStream chars = new ANTLRInputStream(scriptText);
        ProperTeeLexer lexer = new ProperTeeLexer(chars);
        ErrorCollector lexerErrors = new ErrorCollector();
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerErrors);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ProperTeeParser parser = new ProperTeeParser(tokens);
        ErrorCollector parserErrors = new ErrorCollector();
        parser.removeErrorListeners();
        parser.addErrorListener(parserErrors);

        ProperTeeParser.RootContext tree = parser.root();

        if (!lexerErrors.errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Lexer errors:\n");
            for (String e : lexerErrors.errors) sb.append("  ").append(e).append("\n");
            errors.add(sb.toString());
            return null;
        }
        if (!parserErrors.errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Parser errors:\n");
            for (String e : parserErrors.errors) sb.append("  ").append(e).append("\n");
            errors.add(sb.toString());
            return null;
        }

        return tree;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        String propsJson = "{}";
        int maxIterations = 1000;
        String iterationLimitBehavior = "error";
        String scriptFile = null;

        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) || "--props".equals(args[i])) {
                propsJson = args[++i];
            } else if ("-f".equals(args[i]) || "--props-file".equals(args[i])) {
                try {
                    propsJson = new String(Files.readAllBytes(Paths.get(args[++i])), Charset.forName("UTF-8"));
                } catch (IOException e) {
                    System.err.println("Error: Cannot read properties file: " + e.getMessage());
                    System.exit(1);
                }
            } else if ("--max-iterations".equals(args[i])) {
                maxIterations = Integer.parseInt(args[++i]);
            } else if ("--warn-loops".equals(args[i])) {
                iterationLimitBehavior = "warn";
            } else if ("-h".equals(args[i]) || "--help".equals(args[i])) {
                System.out.println("Usage: java -jar propertee.jar [options] [script.pt]");
                System.out.println();
                System.out.println("Options:");
                System.out.println("  -p, --props <json>    Built-in properties as JSON string");
                System.out.println("  -f, --props-file <f>  Built-in properties from JSON file");
                System.out.println("  --max-iterations <n>  Max loop iterations (default: 1000)");
                System.out.println("  --warn-loops          Warn instead of error on loop limit");
                System.out.println("  -h, --help            Show this help");
                System.exit(0);
            } else {
                scriptFile = args[i];
            }
        }

        // Parse properties
        Map<String, Object> properties;
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            properties = gson.fromJson(propsJson, type);
            if (properties == null) properties = new LinkedHashMap<String, Object>();
            // Gson parses numbers as doubles; convert whole numbers to ints
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (entry.getValue() instanceof Double) {
                    double d = (Double) entry.getValue();
                    if (TypeChecker.isInteger(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                        entry.setValue((int) d);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error: Invalid properties JSON: " + e.getMessage());
            System.exit(1);
            return;
        }

        BuiltinFunctions.PrintFunction stdout = new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(args[i]);
                }
                System.out.println(sb.toString());
            }
        };

        BuiltinFunctions.PrintFunction stderr = new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(args[i]);
                }
                System.err.println(sb.toString());
            }
        };

        // File mode
        if (scriptFile != null && Files.exists(Paths.get(scriptFile))) {
            String scriptText;
            try {
                scriptText = new String(Files.readAllBytes(Paths.get(scriptFile)), Charset.forName("UTF-8"));
            } catch (IOException e) {
                System.err.println("Error: Cannot read file '" + scriptFile + "': " + e.getMessage());
                System.exit(1);
                return;
            }

            List<String> errors = new ArrayList<String>();
            ProperTeeParser.RootContext tree = parseScript(scriptText, errors);
            if (tree == null) {
                for (String err : errors) System.err.println(err);
                System.exit(1);
                return;
            }

            ProperTeeInterpreter visitor = new ProperTeeInterpreter(properties, stdout, stderr, maxIterations, iterationLimitBehavior);
            Scheduler scheduler = new Scheduler(visitor);
            Stepper mainStepper = visitor.createRootStepper(tree);

            try {
                Object result = scheduler.run(mainStepper);
                if (result != null) {
                    System.out.println("Result: " + TypeChecker.formatValue(result));
                }
            } catch (Exception e) {
                System.err.println("Runtime error: " + e.getMessage());
                System.exit(1);
            }
        } else {
            // REPL mode
            if (scriptFile != null) {
                System.err.println("File '" + scriptFile + "' not found. Entering interactive mode.\n");
            }

            Repl repl = new Repl(properties, stdout, stderr, maxIterations, iterationLimitBehavior);
            repl.run();
        }
    }
}
