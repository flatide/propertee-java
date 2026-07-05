package com.flatide.tests;

import com.flatide.core.ScriptParser;
import com.flatide.interpreter.BuiltinFunctions;
import com.flatide.interpreter.ProperTeeInterpreter;
import com.flatide.parser.ProperTeeParser;
import com.flatide.scheduler.Scheduler;
import com.flatide.stepper.Stepper;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Runtime-backstop regression: the function ignore list must block a `multi` thread spawn of a
 * <em>user-defined</em> function, not only builtins. The spawn-processing loop used to check
 * {@code ignoredFunctions} only in its builtin branch, so {@code setIgnoredFunctions({"foo"})}
 * blocked {@code foo()} but let {@code multi do thread : foo() end} run.
 */
public class IgnoredFunctionSpawnTest {

    private static final String SCRIPT =
        "function foo() do\n" +
        "    return 1\n" +
        "end\n" +
        "multi r do\n" +
        "    thread a: foo()\n" +
        "end\n" +
        "PRINT(r.a.value)\n";

    private static String run(String script, Set<String> ignored) {
        final StringBuilder out = new StringBuilder();
        BuiltinFunctions.PrintFunction sink = new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] args) {
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) out.append(' ');
                    out.append(args[i]);
                }
            }
        };
        List<String> errors = new ArrayList<String>();
        ProperTeeParser.RootContext tree = ScriptParser.parse(script, errors);
        Assert.assertNotNull("parse failed: " + errors, tree);

        ProperTeeInterpreter visitor = new ProperTeeInterpreter(
                new LinkedHashMap<String, Object>(), sink, sink, 1000, "error", null);
        if (ignored != null) {
            visitor.setIgnoredFunctions(ignored);
        }
        Scheduler scheduler = new Scheduler(visitor);
        Stepper mainStepper = visitor.createRootStepper(tree);
        try {
            scheduler.run(mainStepper);
        } finally {
            visitor.builtins.shutdown();
        }
        return out.toString();
    }

    @Test
    public void spawnOfIgnoredUserFunctionIsBlocked() {
        try {
            run(SCRIPT, new HashSet<String>(Arrays.asList("foo")));
            Assert.fail("expected the ignored function to be blocked in the thread spawn");
        } catch (RuntimeException e) {
            Assert.assertTrue("message: " + e.getMessage(),
                e.getMessage().contains("'foo' is not available in this environment"));
        }
    }

    @Test
    public void directCallOfIgnoredUserFunctionStaysBlocked() {
        try {
            run("function foo() do\n    return 1\nend\nfoo()\n",
                new HashSet<String>(Arrays.asList("foo")));
            Assert.fail("expected the ignored function to be blocked");
        } catch (RuntimeException e) {
            Assert.assertTrue("message: " + e.getMessage(),
                e.getMessage().contains("'foo' is not available in this environment"));
        }
    }

    @Test
    public void unrestrictedSpawnStillRuns() {
        Assert.assertEquals("1", run(SCRIPT, null));
    }
}
