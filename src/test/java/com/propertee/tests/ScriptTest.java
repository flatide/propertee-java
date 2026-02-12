package com.propertee.tests;

import com.propertee.cli.Main;
import com.propertee.interpreter.BuiltinFunctions;
import com.propertee.interpreter.ProperTeeInterpreter;
import com.propertee.parser.ProperTeeParser;
import com.propertee.runtime.TypeChecker;
import com.propertee.scheduler.Scheduler;
import com.propertee.stepper.Stepper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.propertee.runtime.Result;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

@RunWith(Parameterized.class)
public class ScriptTest {

    private final String testName;
    private final String scriptContent;
    private final String expectedOutput;
    private final Map<String, Object> properties;

    public ScriptTest(String testName, String scriptContent, String expectedOutput, Map<String, Object> properties) {
        this.testName = testName;
        this.scriptContent = scriptContent;
        this.expectedOutput = expectedOutput;
        this.properties = properties;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() throws IOException {
        List<Object[]> tests = new ArrayList<Object[]>();
        // Try classpath resources first
        String resourceBase = "/tests/";

        // Find all .pt test files
        // We need to enumerate them - classpath resources can't be listed easily,
        // so we use a known list
        String[] testNames = {
            "01_variables_types", "02_arithmetic", "03_comparisons_logic",
            "04_if_else", "05_condition_loop", "06_value_loop",
            "07_keyvalue_loop", "08_break_continue", "09_functions",
            "10_recursion", "11_arrays", "12_objects", "13_strings",
            "14_scope", "15_nested_loops", "16_thread_basic",
            "17_thread_results", "18_thread_global_snapshot", "19_thread_sleep",
            "20_thread_monitor", "21_thread_no_result", "22_thread_calling_thread",
            "23_error_type_mismatch", "24_error_undefined_var", "25_error_undefined_func",
            "26_error_div_zero", "27_error_null_access", "28_error_not_boolean",
            "29_error_loop_limit", "30_thread_local_scope",
            "32_error_monitor_assign", "33_complex_expressions", "34_builtin_properties",
            "35_object_computed_keys", "36_function_with_loops", "37_thread_with_loops",
            "38_many_threads", "39_escape_strings", "40_multi_after_multi",
            "41_result_pattern",
            "42_global_prefix", "43_global_prefix_error",
            "44_global_prefix_thread", "45_global_prefix_thread_error",
            "46_thread_error_result",
            "47_spawn_outside_multi",
            "48_has_key",
            "49_multi_result_collection",
            "50_multi_dynamic_spawn",
            "51_multi_auto_keys",
            "52_multi_duplicate_key_error",
            "53_len_on_maps",
            "54_map_positional_access",
            "55_thread_status_field",
            "56_monitor_reads_result",
            "57_dynamic_thread_keys",
            "58_dynamic_key_digit_error",
            "59_dynamic_key_type_error",
            "60_dynamic_key_duplicate",
            "61_duplicate_auto_key",
            "62_range_array",
            "63_range_step_zero",
            "64_time_functions",
            "65_keys",
            "66_sort",
            "67_sort_errors",
            "68_cow_semantics",
            "69_thread_isolation"
        };

        for (String name : testNames) {
            InputStream ptStream = ScriptTest.class.getResourceAsStream(resourceBase + name + ".pt");
            InputStream expStream = ScriptTest.class.getResourceAsStream(resourceBase + name + ".expected");

            if (ptStream == null || expStream == null) continue;

            String script = readStream(ptStream);
            String expected = readStream(expStream);

            Map<String, Object> props = new LinkedHashMap<String, Object>();
            if ("34_builtin_properties".equals(name)) {
                props.put("width", 100);
                props.put("height", 200);
                props.put("name", "test");
            }

            tests.add(new Object[]{name, script, expected, props});
        }

        return tests;
    }

    private static String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }

    @Test
    public void testScript() {
        final StringBuilder output = new StringBuilder();

        BuiltinFunctions.PrintFunction stdout = new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(args[i]);
                }
                if (output.length() > 0) output.append("\n");
                output.append(sb.toString());
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
                if (output.length() > 0) output.append("\n");
                output.append(sb.toString());
            }
        };

        List<String> errors = new ArrayList<String>();
        ProperTeeParser.RootContext tree = Main.parseScript(scriptContent, errors);

        if (tree == null) {
            for (String err : errors) {
                if (output.length() > 0) output.append("\n");
                output.append(err);
            }
        } else {
            ProperTeeInterpreter visitor = new ProperTeeInterpreter(properties, stdout, stderr, 1000, "error");

            // Register test external functions for test 41
            if ("41_result_pattern".equals(testName)) {
                visitor.builtins.registerExternal("GET_BALANCE", new BuiltinFunctions.BuiltinFunction() {
                    @Override
                    public Object call(List<Object> args) {
                        String user = (String) args.get(0);
                        if ("alice".equals(user)) return Result.ok(3000);
                        if ("bob".equals(user)) return Result.ok(0);
                        return Result.error("account not found");
                    }
                });
                visitor.builtins.registerExternal("DIVIDE_SAFE", new BuiltinFunctions.BuiltinFunction() {
                    @Override
                    public Object call(List<Object> args) {
                        double a = ((Number) args.get(0)).doubleValue();
                        double b = ((Number) args.get(1)).doubleValue();
                        if (b == 0) throw new RuntimeException("division by zero");
                        return Result.ok(TypeChecker.boxNumber(a / b));
                    }
                });
            }

            Scheduler scheduler = new Scheduler(visitor);
            Stepper mainStepper = visitor.createRootStepper(tree);

            try {
                Object result = scheduler.run(mainStepper);
                // Don't print result for tests (matching JS behavior which uses console.log via PRINT)
            } catch (Exception e) {
                if (output.length() > 0) output.append("\n");
                output.append("Runtime error: " + e.getMessage());
            }
        }

        String actual = output.toString();
        String expected = expectedOutput.endsWith("\n") ? expectedOutput.substring(0, expectedOutput.length() - 1) : expectedOutput;

        if (!actual.equals(expected)) {
            org.junit.Assert.fail("Test " + testName + " output mismatch.\nExpected:\n" + expected + "\nActual:\n" + actual);
        }
    }
}
