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
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Regression tests for SLEEP timing across nesting contexts. SLEEP must actually wait — not no-op —
 * whether at the top level or inside a loop / function / if body, or in a spawned worker.
 *
 * <p>Before the fix, a SLEEP command produced inside an eagerly-evaluated block (loop/function/if
 * body) or by a builtin spawned directly as a worker was silently discarded, so the script returned
 * instantly. The Java runtime now honors a nested SLEEP via a blocking fallback on the eager path
 * (see {@code ProperTeeInterpreter.evalBlock}) and yields a directly-spawned builtin SLEEP to the
 * scheduler (see {@code CommandThenDoneStepper}). These tests assert each context actually sleeps.
 */
public class SleepNestingTest {

    private static final long SLEEP_MS = 300L;
    // The broken behavior returned in ~0ms; a real sleep takes ~SLEEP_MS. Use a threshold safely
    // above 0 and below SLEEP_MS so the test is robust to scheduling slack but still catches a no-op.
    private static final long MIN_ONE = 250L;

    private long runAndTimeMs(String script) {
        List<String> errors = new ArrayList<String>();
        ProperTeeParser.RootContext tree = ScriptParser.parse(script, errors);
        Assert.assertNotNull("parse failed: " + errors, tree);
        BuiltinFunctions.PrintFunction noop = new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] args) { /* discard */ }
        };
        ProperTeeInterpreter visitor = new ProperTeeInterpreter(
            new LinkedHashMap<String, Object>(), noop, noop, 1000, "error", null);
        Scheduler scheduler = new Scheduler(visitor);
        Stepper main = visitor.createRootStepper(tree);
        long start = System.currentTimeMillis();
        try {
            scheduler.run(main);
        } finally {
            visitor.builtins.shutdown();
        }
        return System.currentTimeMillis() - start;
    }

    @Test
    public void topLevelSleepWaits() {
        long ms = runAndTimeMs("SLEEP(" + SLEEP_MS + ")\n");
        Assert.assertTrue("top-level SLEEP should wait, was " + ms + "ms", ms >= MIN_ONE);
    }

    @Test
    public void loopBodySleepWaitsEachIteration() {
        long ms = runAndTimeMs("loop i in [1, 2, 3] do SLEEP(" + SLEEP_MS + ") end\n");
        Assert.assertTrue("loop x3 should wait ~3x, was " + ms + "ms", ms >= 3 * MIN_ONE);
    }

    @Test
    public void functionBodySleepWaits() {
        long ms = runAndTimeMs("function f() do SLEEP(" + SLEEP_MS + ") end\nf()\n");
        Assert.assertTrue("SLEEP in function body should wait, was " + ms + "ms", ms >= MIN_ONE);
    }

    @Test
    public void ifBodySleepWaits() {
        long ms = runAndTimeMs("if 1 < 2 then SLEEP(" + SLEEP_MS + ") end\n");
        Assert.assertTrue("SLEEP in if body should wait, was " + ms + "ms", ms >= MIN_ONE);
    }

    @Test
    public void multiWorkerDirectSleepWaits() {
        long ms = runAndTimeMs("multi r do\n thread a: SLEEP(" + SLEEP_MS + ")\nend\n");
        Assert.assertTrue("SLEEP directly in a worker should wait, was " + ms + "ms", ms >= MIN_ONE);
    }

    @Test
    public void multiWorkerIfBodySleepWaits() {
        long ms = runAndTimeMs(
            "function w() do\n if 1 < 2 then SLEEP(" + SLEEP_MS + ") end\n return 1\nend\n"
            + "multi r do\n thread a: w()\nend\n");
        Assert.assertTrue("SLEEP in a worker's if body should wait, was " + ms + "ms", ms >= MIN_ONE);
    }
}
