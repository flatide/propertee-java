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
 * Regression tests for <em>cooperative</em> suspension when SLEEP appears in statement position
 * inside a control-flow body (Strategy C). Unlike {@link SleepNestingTest}, which only asserts that
 * a nested SLEEP actually waits, these assert that the wait <em>yields to the scheduler</em> so
 * other threads make progress during it.
 *
 * <p>The probe: two workers that each sleep {@code SLEEP_MS} inside an {@code if} body. When the
 * nested SLEEP is cooperative the two sleeps overlap, so the run finishes in ~1x SLEEP_MS. Under the
 * old blocking fallback the first worker's sleep froze the scheduler thread, forcing the second to
 * wait its turn — ~2x SLEEP_MS. The threshold sits between 1x and 2x so it distinguishes the two.
 */
public class CooperativeNestingTest {

    private static final long SLEEP_MS = 300L;
    // Cooperative (overlapped) ~= 1x SLEEP_MS; blocking (sequential) ~= 2x. Split the difference,
    // leaving slack above 1x for scheduling overhead and below 2x to still catch a regression.
    private static final long OVERLAP_CEILING = (long) (1.6 * SLEEP_MS); // 480ms
    private static final long ACTUALLY_SLEPT_FLOOR = 250L;

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

    /** Two workers each SLEEP inside an if body — the sleeps must overlap, not serialize. */
    @Test
    public void ifBodySleepYieldsCooperatively() {
        String script =
            "function w() do\n" +
            "  if 1 < 2 then SLEEP(" + SLEEP_MS + ") end\n" +
            "  return 1\n" +
            "end\n" +
            "multi r do\n" +
            "  thread a: w()\n" +
            "  thread b: w()\n" +
            "end\n";
        long ms = runAndTimeMs(script);
        Assert.assertTrue("nested-if SLEEP should actually wait, was " + ms + "ms", ms >= ACTUALLY_SLEPT_FLOOR);
        Assert.assertTrue(
            "two if-body SLEEPs should overlap cooperatively (~1x), not serialize (~2x); was " + ms + "ms",
            ms < OVERLAP_CEILING);
    }

    /** SLEEP nested two if-levels deep must still suspend cooperatively. */
    @Test
    public void nestedIfBodySleepYieldsCooperatively() {
        String script =
            "function w() do\n" +
            "  if 1 < 2 then\n" +
            "    if 2 < 3 then SLEEP(" + SLEEP_MS + ") end\n" +
            "  end\n" +
            "  return 1\n" +
            "end\n" +
            "multi r do\n" +
            "  thread a: w()\n" +
            "  thread b: w()\n" +
            "end\n";
        long ms = runAndTimeMs(script);
        Assert.assertTrue("deep-nested if SLEEP should actually wait, was " + ms + "ms", ms >= ACTUALLY_SLEPT_FLOOR);
        Assert.assertTrue(
            "two doubly-nested if-body SLEEPs should overlap cooperatively; was " + ms + "ms",
            ms < OVERLAP_CEILING);
    }

    /** Two workers each SLEEP inside a value-loop body — iterations must overlap, not serialize. */
    @Test
    public void valueLoopBodySleepYieldsCooperatively() {
        // 2 iterations x 150ms = 300ms of sleep per worker; overlapped ~300ms, serialized ~600ms.
        String script =
            "function w() do\n" +
            "  loop i in [1, 2] do SLEEP(150) end\n" +
            "  return 1\n" +
            "end\n" +
            "multi r do\n" +
            "  thread a: w()\n" +
            "  thread b: w()\n" +
            "end\n";
        long ms = runAndTimeMs(script);
        Assert.assertTrue("value-loop SLEEP should actually wait, was " + ms + "ms", ms >= ACTUALLY_SLEPT_FLOOR);
        Assert.assertTrue(
            "two value-loop-body SLEEPs should overlap cooperatively (~1x), not serialize (~2x); was " + ms + "ms",
            ms < OVERLAP_CEILING);
    }

    /** Same probe but with a condition loop (re-evaluates its condition each iteration). */
    @Test
    public void conditionLoopBodySleepYieldsCooperatively() {
        String script =
            "function w() do\n" +
            "  i = 0\n" +
            "  loop i < 2 infinite do\n" +
            "    SLEEP(150)\n" +
            "    i = i + 1\n" +
            "  end\n" +
            "  return 1\n" +
            "end\n" +
            "multi r do\n" +
            "  thread a: w()\n" +
            "  thread b: w()\n" +
            "end\n";
        long ms = runAndTimeMs(script);
        Assert.assertTrue("condition-loop SLEEP should actually wait, was " + ms + "ms", ms >= ACTUALLY_SLEPT_FLOOR);
        Assert.assertTrue(
            "two condition-loop-body SLEEPs should overlap cooperatively; was " + ms + "ms",
            ms < OVERLAP_CEILING);
    }

    /**
     * Two workers each call a helper as a bare statement; the helper sleeps. The bare call must run
     * cooperatively so the two helpers' sleeps overlap, rather than serializing under the old eager
     * callUserFunction blocking fallback.
     */
    @Test
    public void bareCallBodySleepYieldsCooperatively() {
        String script =
            "function inner() do SLEEP(" + SLEEP_MS + ") end\n" +
            "function w() do\n" +
            "  inner()\n" +
            "  return 1\n" +
            "end\n" +
            "multi r do\n" +
            "  thread a: w()\n" +
            "  thread b: w()\n" +
            "end\n";
        long ms = runAndTimeMs(script);
        Assert.assertTrue("bare-call SLEEP should actually wait, was " + ms + "ms", ms >= ACTUALLY_SLEPT_FLOOR);
        Assert.assertTrue(
            "two bare-call helper SLEEPs should overlap cooperatively (~1x), not serialize (~2x); was " + ms + "ms",
            ms < OVERLAP_CEILING);
    }

    /** Sanity: results still flow back correctly when SLEEP suspends inside an if body. */
    @Test
    public void resultsIntactAcrossCooperativeIfSleep() {
        String script =
            "function w(n) do\n" +
            "  if 1 < 2 then SLEEP(" + SLEEP_MS + ") end\n" +
            "  return n * 10\n" +
            "end\n" +
            "multi r do\n" +
            "  thread a: w(2)\n" +
            "  thread b: w(3)\n" +
            "end\n" +
            "PRINT(r.a.value)\n" +
            "PRINT(r.b.value)\n";
        final List<Object> out = new ArrayList<Object>();
        List<String> errors = new ArrayList<String>();
        ProperTeeParser.RootContext tree = ScriptParser.parse(script, errors);
        Assert.assertNotNull("parse failed: " + errors, tree);
        BuiltinFunctions.PrintFunction capture = new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] args) { for (Object a : args) out.add(a); }
        };
        ProperTeeInterpreter visitor = new ProperTeeInterpreter(
            new LinkedHashMap<String, Object>(), capture, capture, 1000, "error", null);
        Scheduler scheduler = new Scheduler(visitor);
        try {
            scheduler.run(visitor.createRootStepper(tree));
        } finally {
            visitor.builtins.shutdown();
        }
        Assert.assertEquals("worker a result", "20", String.valueOf(out.get(0)));
        Assert.assertEquals("worker b result", "30", String.valueOf(out.get(1)));
    }

    // --- Scope hygiene on exceptional exit from a scoped (function) body ---
    //
    // A scoped StatementListStepper pushes the callee's local scope; finish() pops it on the normal
    // / return path. When break/continue (or a runtime error) unwinds OUT of the function instead,
    // the scope must still be popped — mirroring the try/finally in the eager callUserFunction.
    // Otherwise an outer loop that catches the control-flow exception keeps running with the callee's
    // locals leaked onto the scope stack, so a later reference resolves to a name that should be gone.

    /** Returns the runtime error message from running {@code script}, or null if it completed. */
    private String runExpectingError(String script) {
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
        try {
            scheduler.run(visitor.createRootStepper(tree));
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        } finally {
            visitor.builtins.shutdown();
        }
    }

    @Test
    public void breakOutOfCalledFunctionDoesNotLeakScope() {
        // `break` escapes f() to the call-site loop; f()'s local `x` must not survive into PRINT(x).
        String script =
            "function f() do\n" +
            "    x = \"local\"\n" +
            "    break\n" +
            "end\n" +
            "loop i in [1] do\n" +
            "    f()\n" +
            "end\n" +
            "PRINT(x)\n";
        String err = runExpectingError(script);
        Assert.assertNotNull("expected an undefined-variable error, but the script completed", err);
        Assert.assertTrue("expected 'x' undefined error, got: " + err, err.contains("'x' is not defined"));
    }

    @Test
    public void continueOutOfCalledFunctionDoesNotLeakScope() {
        String script =
            "function f() do\n" +
            "    x = \"local\"\n" +
            "    continue\n" +
            "end\n" +
            "loop i in [1] do\n" +
            "    f()\n" +
            "end\n" +
            "PRINT(x)\n";
        String err = runExpectingError(script);
        Assert.assertNotNull("expected an undefined-variable error, but the script completed", err);
        Assert.assertTrue("expected 'x' undefined error, got: " + err, err.contains("'x' is not defined"));
    }

    @Test
    public void breakUnwindingThroughTwoFramesDoesNotLeakScope() {
        // `break` unwinds inner() -> mid() -> loop; both frames' locals must be popped.
        String script =
            "function inner() do\n" +
            "    y = \"deep\"\n" +
            "    break\n" +
            "end\n" +
            "function mid() do\n" +
            "    z = \"mid\"\n" +
            "    inner()\n" +
            "end\n" +
            "loop i in [1] do\n" +
            "    mid()\n" +
            "end\n" +
            "PRINT(y)\n";
        String err = runExpectingError(script);
        Assert.assertNotNull("expected an undefined-variable error, but the script completed", err);
        Assert.assertTrue("expected 'y' undefined error, got: " + err, err.contains("'y' is not defined"));
    }
}
