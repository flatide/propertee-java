package com.flatide.interpreter;

import com.flatide.parser.ProperTeeBaseVisitor;
import com.flatide.parser.ProperTeeParser;
import com.flatide.runtime.*;
import com.flatide.stepper.*;
import com.flatide.scheduler.ThreadContext;

import java.math.BigDecimal;
import java.util.*;

/**
 * The ProperTee interpreter. Extends ANTLR's BaseVisitor with Object return type.
 *
 * In the JS version, every visit* method is a generator (function*).
 * In this Java version, expression visitors evaluate eagerly and return values directly.
 * Statement visitors return Stepper objects for the scheduler to step through.
 *
 * The interpreter has two modes of evaluation:
 * 1. eval() - Eagerly evaluates an expression parse tree node, returning the value.
 *    Used for all expressions (arithmetic, comparisons, function calls, etc.)
 * 2. createStepper() - Creates a Stepper for a statement-level node.
 *    Used for blocks, loops, if statements, etc.
 */
public class ProperTeeInterpreter extends ProperTeeBaseVisitor<Object> {

    // Global variables
    public Map<String, Object> variables = new LinkedHashMap<String, Object>();

    // User-defined functions
    public Map<String, FunctionDef> userDefinedFunctions = new LinkedHashMap<String, FunctionDef>();

    // Scope stack for single-threaded execution
    public ScopeStack scopeStack = new ScopeStack();

    // Threading context flags (for main thread when no scheduler)
    public boolean inMultiContext = false;
    public Map<String, Object> multiResultVars = new LinkedHashMap<String, Object>();
    public boolean inMonitorContext = false;
    public boolean inThreadContext = false;

    // SPAWN collection (used during multi block setup)
    public boolean inMultiSetup = false;
    public List<SpawnSpec> collectedSpawns = null;

    /** Spec for a SPAWN statement collected during multi block setup */
    public static class SpawnSpec {
        public final String funcName;
        public final List<Object> args;
        public final String resultKey; // null for fire-and-forget (key in collection)
        public final org.antlr.v4.runtime.ParserRuleContext ctx;

        public SpawnSpec(String funcName, List<Object> args, String resultKey,
                         org.antlr.v4.runtime.ParserRuleContext ctx) {
            this.funcName = funcName;
            this.args = args;
            this.resultKey = resultKey;
            this.ctx = ctx;
        }
    }

    // Active thread context (set by scheduler)
    public ThreadContext activeThread = null;

    // Built-in properties
    public Map<String, Object> properties;

    // Built-in functions
    public BuiltinFunctions builtins;

    // Options
    public int maxIterations = 1000;
    public String iterationLimitBehavior = "error";

    // Restriction lists
    private Set<String> hiddenKeywords = new HashSet<String>();
    private Set<String> ignoredFunctions = new HashSet<String>();

    // I/O
    public BuiltinFunctions.PrintFunction stdout;
    public BuiltinFunctions.PrintFunction stderr;
    private String currentBuiltinCallSiteKey = null;

    public ProperTeeInterpreter(Map<String, Object> properties, BuiltinFunctions.PrintFunction stdout,
                                 BuiltinFunctions.PrintFunction stderr, int maxIterations, String iterationLimitBehavior) {
        this(properties, stdout, stderr, maxIterations, iterationLimitBehavior, null);
    }

    public ProperTeeInterpreter(Map<String, Object> properties, BuiltinFunctions.PrintFunction stdout,
                                 BuiltinFunctions.PrintFunction stderr, int maxIterations, String iterationLimitBehavior,
                                 BuiltinFunctions builtins) {
        Map<String, Object> incoming = properties != null ? properties : new LinkedHashMap<String, Object>();
        // Reserved `_PROPS`: the full input set exposed as one object, so a script can print,
        // iterate, or pass along ALL inputs at once (PRINT(_PROPS), KEYS(_PROPS),
        // JSON_FORMAT(_PROPS), _PROPS.a ...) while each key also stays directly accessible as a
        // bare variable (a, b, ...). The interpreter keeps its own props view so the caller's map
        // is never mutated, and `_PROPS` holds a (shallow) snapshot that does not contain itself
        // (so JSON_FORMAT(_PROPS) cannot recurse). A caller-supplied `_PROPS` key is left as-is.
        if (incoming.containsKey("_PROPS")) {
            this.properties = incoming;
        } else {
            Map<String, Object> view = new LinkedHashMap<String, Object>(incoming);
            view.put("_PROPS", new LinkedHashMap<String, Object>(incoming));
            this.properties = view;
        }
        this.stdout = stdout;
        this.stderr = stderr;
        this.maxIterations = maxIterations;
        this.iterationLimitBehavior = iterationLimitBehavior;
        this.builtins = builtins != null ? builtins : new BuiltinFunctions(stdout, stderr);
        this.builtins.setInterpreter(this);
    }

    public void setHiddenKeywords(Set<String> keywords) {
        this.hiddenKeywords = keywords != null ? keywords : new HashSet<String>();
    }

    public void setIgnoredFunctions(Set<String> functions) {
        this.ignoredFunctions = functions != null ? functions : new HashSet<String>();
    }

    private void checkKeywordAllowed(String keyword, org.antlr.v4.runtime.ParserRuleContext ctx) {
        if (hiddenKeywords.contains(keyword)) {
            throw createError("'" + keyword + "' is not available in this environment", ctx);
        }
    }

    // --- Helper methods ---

    public String getLocation(org.antlr.v4.runtime.ParserRuleContext ctx) {
        if (ctx != null && ctx.getStart() != null) {
            return "line " + ctx.getStart().getLine() + ":" + ctx.getStart().getCharPositionInLine();
        }
        return "unknown location";
    }

    public ProperTeeError createError(String message, org.antlr.v4.runtime.ParserRuleContext ctx) {
        String location = getLocation(ctx);
        return new ProperTeeError("Runtime Error at " + location + ": " + message);
    }

    public ScopeStack getScopeStack() {
        if (activeThread != null) return activeThread.scopeStack;
        return scopeStack;
    }

    public Map<String, Object> getVariables() {
        if (activeThread != null && activeThread.globalSnapshot != null) {
            return activeThread.globalSnapshot;
        }
        return variables;
    }

    public boolean isInThreadContext() {
        if (activeThread != null) return activeThread.inThreadContext;
        return inThreadContext;
    }

    public boolean isInMonitorContext() {
        if (activeThread != null) return activeThread.inMonitorContext;
        return inMonitorContext;
    }

    public boolean isInMultiContext() {
        if (activeThread != null) return activeThread.inMultiContext;
        return inMultiContext;
    }

    public Map<String, Object> getMultiResultVars() {
        if (activeThread != null) return activeThread.multiResultVars;
        return multiResultVars;
    }

    public String getCurrentBuiltinCallSiteKey() {
        return currentBuiltinCallSiteKey;
    }

    public boolean isInFunctionScope() {
        return !getScopeStack().isEmpty();
    }

    // --- Evaluate an expression node eagerly ---
    public Object eval(org.antlr.v4.runtime.tree.ParseTree ctx) {
        if (ctx == null) return null;
        return ctx.accept(this);
    }

    // --- Create a Stepper for a statement/block ---
    public RootStepper createRootStepper(ProperTeeParser.RootContext ctx) {
        return new RootStepper(this, ctx);
    }

    // --- Statement-list Stepper (inner classes) ---

    /**
     * Process collected results from SPAWN_THREADS command (sent back by scheduler).
     * Builds a result collection map and assigns it to the result variable.
     */
    @SuppressWarnings("unchecked")
    private static void processSpawnResults(ProperTeeInterpreter interp, Object sendValue) {
        if (sendValue == null) return;
        Map<String, Object> payload = (Map<String, Object>) sendValue;
        String resultVarName = (String) payload.get("resultVarName");
        Map<String, Object> collection = (Map<String, Object>) payload.get("collection");

        if (resultVarName == null) return; // fire-and-forget

        ScopeStack ss = interp.getScopeStack();
        Map<String, Object> vars = interp.getVariables();
        if (!ss.isEmpty()) {
            ss.set(resultVarName, collection);
        } else {
            vars.put(resultVarName, collection);
        }
    }

    /**
     * Drives a flat list of statements, yielding a BOUNDARY between each so the scheduler can
     * round-robin at statement granularity. Honors SchedulerCommands (SLEEP/SPAWN_THREADS) and
     * AsyncPendingException returned by {@code eval()} of a statement, and feeds SPAWN_THREADS
     * results back via {@link #setSendValue}.
     *
     * <p>This is the single engine behind the top-level script ({@link RootStepper}), each
     * multi-block worker (function body), and — when driving a {@code child} sub-stepper — the
     * bodies of control-flow constructs (e.g. {@link IfStepper}). Three policies parameterize it:
     * <ul>
     *   <li>{@code localScope} — when non-null, pushed before the first statement and popped on
     *       completion (function/worker bodies). When null, no scope is pushed (top level, and
     *       if/loop bodies which share the enclosing scope).</li>
     *   <li>{@code emptyObjectOnFallthrough} — when a body completes without an explicit
     *       {@code return}: workers/functions yield an empty object ({@code {}}); the top level and
     *       control-flow bodies yield the last statement's value.</li>
     *   <li>{@code catchReturn} — when true, a {@code return} ends this stepper with the returned
     *       value (top level / function / worker). When false (if/loop bodies), {@code return}
     *       propagates up so it unwinds to the enclosing function, matching the eager model.</li>
     * </ul>
     *
     * <p>Cooperative nesting: a statement that is itself a suspendable control-flow construct is run
     * as a {@code child} sub-stepper rather than evaluated eagerly. BOUNDARY/COMMAND results from the
     * child are relayed upward (the child stays active across suspension); Break/Continue/Return
     * unwind through {@code child.step()} as exceptions, exactly as they did through the eager
     * {@code evalBlock} call stack. Statements with no sub-stepper (assignment, plain expression,
     * multi, flow control) keep the original eager {@code eval()} path.
     */
    public static class StatementListStepper implements Stepper {
        private final ProperTeeInterpreter interp;
        private final List<ProperTeeParser.StatementContext> statements;
        private final Map<String, Object> localScope;
        private final boolean emptyObjectOnFallthrough;
        private final boolean catchReturn;
        private int index = 0;
        private Object result = null;
        private boolean hasExplicitReturn = false;
        private boolean done = false;
        private boolean yieldBoundary = false;
        private boolean scopePushed = false;
        private Object sendValue;
        private boolean waitingForSpawn = false;
        private Stepper child; // active control-flow sub-stepper (null when none)

        public StatementListStepper(ProperTeeInterpreter interp,
                                    List<ProperTeeParser.StatementContext> statements,
                                    Map<String, Object> localScope,
                                    boolean emptyObjectOnFallthrough) {
            this(interp, statements, localScope, emptyObjectOnFallthrough, true);
        }

        public StatementListStepper(ProperTeeInterpreter interp,
                                    List<ProperTeeParser.StatementContext> statements,
                                    Map<String, Object> localScope,
                                    boolean emptyObjectOnFallthrough,
                                    boolean catchReturn) {
            this.interp = interp;
            this.statements = statements;
            this.localScope = localScope;
            this.emptyObjectOnFallthrough = emptyObjectOnFallthrough;
            this.catchReturn = catchReturn;
        }

        @Override
        public StepResult step() {
            if (done) return StepResult.done(result);

            if (localScope != null && !scopePushed) {
                interp.getScopeStack().push(localScope);
                scopePushed = true;
            }

            // Resume an active control-flow sub-stepper before anything else.
            if (child != null) {
                return driveChild();
            }

            // Process SPAWN_THREADS results
            if (waitingForSpawn && sendValue != null) {
                processSpawnResults(interp, sendValue);
                sendValue = null;
                waitingForSpawn = false;
                // Continue to next statement
                if (index < statements.size()) {
                    yieldBoundary = true;
                    return StepResult.BOUNDARY;
                } else {
                    return finishFallthrough();
                }
            }

            if (yieldBoundary) {
                yieldBoundary = false;
                return StepResult.BOUNDARY;
            }

            if (index < statements.size()) {
                // Suspendable control-flow constructs run as a child sub-stepper so a nested
                // SLEEP/spawn/async yields cooperatively instead of blocking the scheduler.
                Stepper sub = interp.createStatementStepper(statements.get(index));
                if (sub != null) {
                    child = sub;
                    return driveChild();
                }

                try {
                    result = interp.eval(statements.get(index));
                    if (result instanceof SchedulerCommand) {
                        SchedulerCommand cmd = (SchedulerCommand) result;
                        result = null;
                        index++;
                        if (cmd.getType() == SchedulerCommand.CommandType.SPAWN_THREADS) {
                            waitingForSpawn = true;
                        }
                        return StepResult.command(cmd);
                    }
                    if (interp.activeThread != null) interp.activeThread.asyncResultCache.clear();
                    index++;
                    if (index < statements.size()) {
                        yieldBoundary = true;
                        return StepResult.BOUNDARY;
                    } else {
                        return finishFallthrough();
                    }
                } catch (ReturnException e) {
                    if (!catchReturn) throw e;
                    hasExplicitReturn = true;
                    return finish(e.getValue());
                } catch (AsyncPendingException e) {
                    return StepResult.command(SchedulerCommand.awaitAsync());
                }
            }

            return finishFallthrough();
        }

        /** Advance the active {@code child} sub-stepper and relay/translate its result. */
        private StepResult driveChild() {
            StepResult r;
            try {
                r = child.step();
            } catch (AsyncPendingException e) {
                // The child's current statement is async-pending; keep the child for replay.
                return StepResult.command(SchedulerCommand.awaitAsync());
            } catch (ReturnException e) {
                child = null;
                if (!catchReturn) throw e; // unwind to the enclosing function/worker
                hasExplicitReturn = true;
                return finish(e.getValue());
            }
            // BreakException/ContinueException/ProperTeeError propagate uncaught, unwinding through
            // this step() just as they unwound through the eager evalBlock call stack.

            if (r.isBoundary() || r.isCommand()) {
                return r; // child stays active across the yield/suspension
            }

            // Child completed: adopt its value as this statement's result, then advance.
            result = child.getResult();
            child = null;
            if (interp.activeThread != null) interp.activeThread.asyncResultCache.clear();
            index++;
            if (index < statements.size()) {
                yieldBoundary = true;
                return StepResult.BOUNDARY;
            }
            return finishFallthrough();
        }

        private StepResult finishFallthrough() {
            // No explicit return reached. Top level / control-flow bodies keep the last statement
            // value (result); function/worker bodies yield an empty object.
            return finish(emptyObjectOnFallthrough ? new LinkedHashMap<String, Object>() : result);
        }

        private StepResult finish(Object val) {
            if (scopePushed) {
                interp.getScopeStack().pop();
                scopePushed = false;
            }
            result = val;
            done = true;
            return StepResult.done(result);
        }

        @Override
        public boolean isDone() { return done; }
        @Override
        public Object getResult() { return result; }
        public boolean hasExplicitReturn() { return hasExplicitReturn; }
        @Override
        public void setSendValue(Object value) {
            // Route spawn-result / async-resume payloads to the deepest active sub-stepper, which is
            // the one that actually issued the command.
            if (child != null) {
                child.setSendValue(value);
            } else {
                this.sendValue = value;
            }
        }
    }

    /** Top-level script driver: no local scope, last-statement value on fallthrough. */
    public static class RootStepper extends StatementListStepper {
        public RootStepper(ProperTeeInterpreter interp, ProperTeeParser.RootContext ctx) {
            super(interp, ctx.statement(), null, false, true);
        }
    }

    /**
     * Cooperative {@code if}/{@code else}: evaluates the condition once (eagerly — a suspendable call
     * embedded in the condition expression is the documented eager seam), then drives the chosen
     * branch as a child block stepper so a SLEEP/spawn/async in the branch body yields cooperatively.
     * Mirrors {@link #visitIfStatement}: no new scope, branch value (or empty when no branch taken).
     */
    public static class IfStepper implements Stepper {
        private final ProperTeeInterpreter interp;
        private final ProperTeeParser.IfStatementContext ctx;
        private Stepper body; // chosen branch (null until evaluated, or if no branch taken)
        private boolean evaluated = false;
        private Object result = null;
        private boolean done = false;

        public IfStepper(ProperTeeInterpreter interp, ProperTeeParser.IfStatementContext ctx) {
            this.interp = interp;
            this.ctx = ctx;
        }

        @Override
        public StepResult step() {
            if (done) return StepResult.done(result);

            if (!evaluated) {
                interp.checkKeywordAllowed("if", ctx);
                Object condition;
                try {
                    condition = interp.eval(ctx.condition);
                } catch (AsyncPendingException e) {
                    // Async in the condition: retry on resume (evaluated stays false).
                    return StepResult.command(SchedulerCommand.awaitAsync());
                }
                evaluated = true;
                ProperTeeParser.BlockContext branch = null;
                if (TypeChecker.isTruthy(condition)) {
                    branch = ctx.thenBody;
                } else if (ctx.elseBody != null) {
                    branch = ctx.elseBody;
                }
                if (branch == null) {
                    done = true;
                    return StepResult.done(result); // null, matching visitIfStatement
                }
                // catchReturn=false: a return inside the branch unwinds to the enclosing function.
                body = new StatementListStepper(interp, branch.statement(), null, false, false);
            }

            StepResult r = body.step();
            if (r.isDone()) {
                result = body.getResult();
                done = true;
                return StepResult.done(result);
            }
            return r; // BOUNDARY / COMMAND pass through; body stays active
        }

        @Override
        public boolean isDone() { return done; }
        @Override
        public Object getResult() { return result; }
        @Override
        public void setSendValue(Object value) {
            if (body != null) body.setSendValue(value);
        }
    }

    /**
     * Cooperative loop base. Drives each iteration's body as a child block stepper (so a nested
     * SLEEP/spawn/async yields to the scheduler) and yields a BOUNDARY between iterations for
     * round-robin fairness. Faithfully mirrors the eager loop visitors: keyword-hide check,
     * iteration-limit enforcement (including the "warn" behavior), Break/Continue handling,
     * last-body-value result, and no new scope for the body (it shares the enclosing scope). A
     * {@code return} inside the body unwinds past the loop to the enclosing function (the body
     * stepper uses {@code catchReturn=false}). A suspendable call embedded in the condition/iterable
     * expression is the documented eager seam, but async there is still honored: the iteration source
     * is re-evaluated on resume (awaitAsync retry).
     *
     * <p>Subclasses supply the iteration source via {@link #advance()} (stage the next element,
     * binding nothing) and {@link #bindCurrent()} (bind loop variables). Binding happens after the
     * limit check, exactly as in the eager loops.
     */
    public abstract static class LoopStepper implements Stepper {
        protected final ProperTeeInterpreter interp;
        private final ProperTeeParser.BlockContext block;
        private final boolean isInfinite;
        private int iterations = 0;
        private Object result = null;
        private boolean started = false;
        private boolean done = false;
        private Stepper body;

        protected LoopStepper(ProperTeeInterpreter interp, ProperTeeParser.BlockContext block, boolean isInfinite) {
            this.interp = interp;
            this.block = block;
            this.isInfinite = isInfinite;
        }

        /** Context for keyword-hide checks and iteration-limit errors. */
        protected abstract org.antlr.v4.runtime.ParserRuleContext ctx();
        /** Hint shown in the iteration-limit error (e.g. {@code "loop ... infinite do"}). */
        protected abstract String infiniteHint();
        /** Stage the next element (no binding); return false when the source is exhausted. */
        protected abstract boolean advance();
        /** Bind the staged loop variable(s); called only after the limit check passes. */
        protected abstract void bindCurrent();

        @Override
        public StepResult step() {
            if (done) return StepResult.done(result);
            if (!started) {
                interp.checkKeywordAllowed("loop", ctx());
                started = true;
            }

            if (body == null) {
                boolean hasNext;
                try {
                    hasNext = advance();
                } catch (AsyncPendingException e) {
                    return StepResult.command(SchedulerCommand.awaitAsync());
                }
                if (!hasNext) {
                    done = true;
                    return StepResult.done(result);
                }
                int limit = isInfinite ? Integer.MAX_VALUE : interp.maxIterations;
                if (++iterations > limit) {
                    if ("warn".equals(interp.iterationLimitBehavior)) {
                        interp.stderr.print(new Object[]{"Warning: Loop exceeded maximum iterations (" + limit + "), stopping loop"});
                        done = true;
                        return StepResult.done(result);
                    }
                    throw interp.createError(
                        "Loop exceeded maximum iterations (" + limit + "). Use '" + infiniteHint() + "' if you need unlimited iterations.",
                        ctx());
                }
                bindCurrent();
                body = new StatementListStepper(interp, block.statement(), null, false, false);
            }

            StepResult r;
            try {
                r = body.step();
            } catch (BreakException e) {
                body = null;
                done = true;
                return StepResult.done(result);
            } catch (ContinueException e) {
                body = null;
                return StepResult.BOUNDARY; // between-iteration yield; next step starts the next iteration
            }
            if (r.isBoundary() || r.isCommand()) return r;

            // Body finished normally: keep its value and yield before the next iteration.
            result = body.getResult();
            body = null;
            return StepResult.BOUNDARY;
        }

        @Override
        public boolean isDone() { return done; }
        @Override
        public Object getResult() { return result; }
        @Override
        public void setSendValue(Object value) {
            if (body != null) body.setSendValue(value);
        }
    }

    /** {@code loop expr [infinite] do ... end} — re-evaluates the condition each iteration. */
    public static class ConditionLoopStepper extends LoopStepper {
        private final ProperTeeParser.ConditionLoopContext ctx;

        public ConditionLoopStepper(ProperTeeInterpreter interp, ProperTeeParser.ConditionLoopContext ctx) {
            super(interp, ctx.block(), ctx.K_INFINITE() != null);
            this.ctx = ctx;
        }

        @Override protected org.antlr.v4.runtime.ParserRuleContext ctx() { return ctx; }
        @Override protected String infiniteHint() { return "loop condition infinite do"; }
        @Override protected boolean advance() { return TypeChecker.isTruthy(interp.eval(ctx.expression())); }
        @Override protected void bindCurrent() { /* no loop variable */ }
    }

    /** {@code loop v in iterable [infinite] do ... end} — iterates list values or map values. */
    public static class ValueLoopStepper extends LoopStepper {
        private final ProperTeeParser.ValueLoopContext ctx;
        private final String valueVar;
        private boolean sourceReady = false;
        private List<Object> list;
        private int listIndex = 0;
        private Iterator<Map.Entry<String, Object>> mapIter;
        private Object pendingValue;

        public ValueLoopStepper(ProperTeeInterpreter interp, ProperTeeParser.ValueLoopContext ctx) {
            super(interp, ctx.block(), ctx.K_INFINITE() != null);
            this.ctx = ctx;
            this.valueVar = ctx.value.getText();
        }

        @Override protected org.antlr.v4.runtime.ParserRuleContext ctx() { return ctx; }
        @Override protected String infiniteHint() { return "loop ... infinite do"; }

        @Override
        @SuppressWarnings("unchecked")
        protected boolean advance() {
            if (!sourceReady) {
                Object iterable = interp.eval(ctx.expression());
                if (iterable instanceof List) {
                    list = (List<Object>) iterable;
                } else if (iterable instanceof Map) {
                    mapIter = ((Map<String, Object>) iterable).entrySet().iterator();
                } else {
                    throw new ProperTeeError("Runtime Error: Cannot iterate over non-iterable value");
                }
                sourceReady = true;
            }
            if (list != null) {
                if (listIndex >= list.size()) return false;
                pendingValue = list.get(listIndex);
                listIndex++;
            } else {
                if (!mapIter.hasNext()) return false;
                pendingValue = mapIter.next().getValue();
            }
            return true;
        }

        @Override
        protected void bindCurrent() {
            ScopeStack ss = interp.getScopeStack();
            Object v = TypeChecker.deepCopy(pendingValue);
            if (!ss.isEmpty()) ss.set(valueVar, v); else interp.getVariables().put(valueVar, v);
        }
    }

    /** {@code loop k, v in iterable [infinite] do ... end} — binds 1-based index/key and value. */
    public static class KeyValueLoopStepper extends LoopStepper {
        private final ProperTeeParser.KeyValueLoopContext ctx;
        private final String keyVar;
        private final String valueVar;
        private boolean sourceReady = false;
        private List<Object> list;
        private int listIndex = 0;
        private Iterator<Map.Entry<String, Object>> mapIter;
        private Object pendingKey;
        private Object pendingValue;

        public KeyValueLoopStepper(ProperTeeInterpreter interp, ProperTeeParser.KeyValueLoopContext ctx) {
            super(interp, ctx.block(), ctx.K_INFINITE() != null);
            this.ctx = ctx;
            this.keyVar = ctx.key.getText();
            this.valueVar = ctx.value.getText();
        }

        @Override protected org.antlr.v4.runtime.ParserRuleContext ctx() { return ctx; }
        @Override protected String infiniteHint() { return "loop ... infinite do"; }

        @Override
        @SuppressWarnings("unchecked")
        protected boolean advance() {
            if (!sourceReady) {
                Object iterable = interp.eval(ctx.expression());
                if (iterable instanceof List) {
                    list = (List<Object>) iterable;
                } else if (iterable instanceof Map) {
                    mapIter = ((Map<String, Object>) iterable).entrySet().iterator();
                } else {
                    throw new ProperTeeError("Runtime Error: Cannot iterate over non-iterable value");
                }
                sourceReady = true;
            }
            if (list != null) {
                if (listIndex >= list.size()) return false;
                pendingKey = listIndex + 1; // 1-based index for arrays
                pendingValue = list.get(listIndex);
                listIndex++;
            } else {
                if (!mapIter.hasNext()) return false;
                Map.Entry<String, Object> entry = mapIter.next();
                pendingKey = entry.getKey();
                pendingValue = entry.getValue();
            }
            return true;
        }

        @Override
        protected void bindCurrent() {
            ScopeStack ss = interp.getScopeStack();
            Object v = TypeChecker.deepCopy(pendingValue);
            if (!ss.isEmpty()) {
                ss.set(keyVar, pendingKey);
                ss.set(valueVar, v);
            } else {
                Map<String, Object> vars = interp.getVariables();
                vars.put(keyVar, pendingKey);
                vars.put(valueVar, v);
            }
        }
    }

    /**
     * Dispatch a statement to a suspendable sub-stepper, or return {@code null} to keep the eager
     * {@code eval()} path. Only constructs that can cooperatively suspend in statement position are
     * handled here; everything else stays eager.
     */
    Stepper createStatementStepper(ProperTeeParser.StatementContext stmt) {
        if (stmt instanceof ProperTeeParser.IfStmtContext) {
            return new IfStepper(this, ((ProperTeeParser.IfStmtContext) stmt).ifStatement());
        }
        if (stmt instanceof ProperTeeParser.IterStmtContext) {
            ProperTeeParser.IterationStmtContext loop = ((ProperTeeParser.IterStmtContext) stmt).iterationStmt();
            if (loop instanceof ProperTeeParser.ConditionLoopContext) {
                return new ConditionLoopStepper(this, (ProperTeeParser.ConditionLoopContext) loop);
            }
            if (loop instanceof ProperTeeParser.ValueLoopContext) {
                return new ValueLoopStepper(this, (ProperTeeParser.ValueLoopContext) loop);
            }
            if (loop instanceof ProperTeeParser.KeyValueLoopContext) {
                return new KeyValueLoopStepper(this, (ProperTeeParser.KeyValueLoopContext) loop);
            }
        }
        return null;
    }

    // ============================================================
    // VISITOR METHODS - Expression visitors return values directly
    // Statement visitors also return values (but may be Steppers for scheduling)
    // ============================================================

    // --- Statement dispatch ---

    @Override
    public Object visitAssignStmt(ProperTeeParser.AssignStmtContext ctx) {
        return eval(ctx.assignment());
    }

    @Override
    public Object visitIfStmt(ProperTeeParser.IfStmtContext ctx) {
        return eval(ctx.ifStatement());
    }

    @Override
    public Object visitIterStmt(ProperTeeParser.IterStmtContext ctx) {
        return eval(ctx.iterationStmt());
    }

    @Override
    public Object visitFuncDefStmt(ProperTeeParser.FuncDefStmtContext ctx) {
        return eval(ctx.functionDef());
    }

    @Override
    public Object visitSpawnExecStmt(ProperTeeParser.SpawnExecStmtContext ctx) {
        return eval(ctx.spawnStmt());
    }

    @Override
    public Object visitParallelExecStmt(ProperTeeParser.ParallelExecStmtContext ctx) {
        return eval(ctx.parallelStmt());
    }

    @Override
    public Object visitFlowStmt(ProperTeeParser.FlowStmtContext ctx) {
        return eval(ctx.flowControl());
    }

    @Override
    public Object visitExprStmt(ProperTeeParser.ExprStmtContext ctx) {
        return eval(ctx.expression());
    }

    // --- Assignment ---

    @Override
    public Object visitAssignment(ProperTeeParser.AssignmentContext ctx) {
        if (isInMonitorContext()) {
            throw createError("Cannot assign variables in monitor block (read-only)", ctx);
        }

        ProperTeeParser.LvalueContext lvalueCtx = ctx.lvalue();
        Object value = eval(ctx.expression());
        ScopeStack ss = getScopeStack();
        Map<String, Object> vars = getVariables();

        if (lvalueCtx instanceof ProperTeeParser.GlobalVarLValueContext) {
            String varName = ((ProperTeeParser.GlobalVarLValueContext) lvalueCtx).ID().getText();

            if (isInThreadContext()) {
                throw createError(
                    "Cannot assign to global variable '::" + varName + "' inside multi block. " +
                    "Functions in multi blocks can only read global variables (via ::) and write to local variables.",
                    ctx);
            }

            // Write directly to globals (bypasses local scopes)
            variables.put(varName, TypeChecker.deepCopy(value));
            return value;
        }

        if (lvalueCtx instanceof ProperTeeParser.VarLValueContext) {
            String varName = ((ProperTeeParser.VarLValueContext) lvalueCtx).ID().getText();

            if (isInThreadContext() && ss.isEmpty()) {
                throw createError(
                    "Cannot assign to global variable '" + varName + "' inside multi block. " +
                    "Functions in multi blocks can only read global variables (via ::) and write to local variables.",
                    ctx);
            }

            if (!ss.isEmpty()) {
                ss.set(varName, TypeChecker.deepCopy(value));
            } else {
                vars.put(varName, TypeChecker.deepCopy(value));
            }
            return value;
        }

        if (lvalueCtx instanceof ProperTeeParser.PropLValueContext) {
            ProperTeeParser.PropLValueContext propCtx = (ProperTeeParser.PropLValueContext) lvalueCtx;
            Object targetObj = evaluateLValueForAssignment(propCtx.lvalue());
            Object key = eval(propCtx.access());

            if (targetObj == null || (!(targetObj instanceof Map) && !(targetObj instanceof List))) {
                throw createError("Cannot set property '" + key + "' on non-object", ctx);
            }

            setProperty(targetObj, key, value, ctx);
            return value;
        }

        throw createError("Unknown lvalue type", ctx);
    }

    @SuppressWarnings("unchecked")
    private void setProperty(Object target, Object key, Object value, org.antlr.v4.runtime.ParserRuleContext ctx) {
        if (target instanceof Map) {
            ((Map<String, Object>) target).put(String.valueOf(key), TypeChecker.deepCopy(value));
        } else if (target instanceof List) {
            List<Object> list = (List<Object>) target;
            if (!(key instanceof Number)) {
                throw createError("Array index must be a number, got string. Use arr.1 not arr.\"1\"", ctx);
            }
            int index = ((Number) key).intValue() - 1; // 1-based to 0-based
            if (index < 0 || index >= list.size()) {
                throw createError("Array index out of bounds", ctx);
            }
            list.set(index, TypeChecker.deepCopy(value));
        }
    }

    private Object evaluateLValueForAssignment(ProperTeeParser.LvalueContext lvalueCtx) {
        ScopeStack ss = getScopeStack();
        Map<String, Object> vars = getVariables();

        if (lvalueCtx instanceof ProperTeeParser.GlobalVarLValueContext) {
            String varName = ((ProperTeeParser.GlobalVarLValueContext) lvalueCtx).ID().getText();
            if (vars.containsKey(varName)) return vars.get(varName);
            if (properties.containsKey(varName)) return properties.get(varName);
            throw new ProperTeeError("Runtime Error: Global variable '" + varName + "' is not defined");
        }

        if (lvalueCtx instanceof ProperTeeParser.VarLValueContext) {
            String varName = ((ProperTeeParser.VarLValueContext) lvalueCtx).ID().getText();
            Object val = ss.get(varName);
            if (val != ScopeStack.UNDEFINED) return val;

            // Inside a function: plain variables are local-only
            if (isInFunctionScope()) {
                throw new ProperTeeError("Runtime Error: Variable '" + varName + "' is not defined in local scope. Use ::" + varName + " to access the global variable.");
            }

            if (vars.containsKey(varName)) return vars.get(varName);
            if (properties.containsKey(varName)) return properties.get(varName);
            throw new ProperTeeError("Runtime Error: Variable '" + varName + "' is not defined");
        }

        if (lvalueCtx instanceof ProperTeeParser.PropLValueContext) {
            ProperTeeParser.PropLValueContext propCtx = (ProperTeeParser.PropLValueContext) lvalueCtx;
            Object targetObj = evaluateLValueForAssignment(propCtx.lvalue());
            Object key = eval(propCtx.access());
            if (targetObj == null) throw new ProperTeeError("Runtime Error: Cannot access property '" + key + "' of null");
            return getProperty(targetObj, key, null);
        }

        throw new ProperTeeError("Runtime Error: Unknown lvalue type in assignment");
    }

    // --- If statement ---

    @Override
    public Object visitIfStatement(ProperTeeParser.IfStatementContext ctx) {
        checkKeywordAllowed("if", ctx);
        Object condition = eval(ctx.condition);

        if (TypeChecker.isTruthy(condition)) {
            if (ctx.thenBody != null) {
                return evalBlock(ctx.thenBody);
            }
            return null;
        } else if (ctx.elseBody != null) {
            return evalBlock(ctx.elseBody);
        }
        return null;
    }

    /** Evaluate a block eagerly (no scheduling boundaries) */
    public Object evalBlock(ProperTeeParser.BlockContext ctx) {
        Object result = null;
        for (ProperTeeParser.StatementContext stmt : ctx.statement()) {
            result = eval(stmt);
            // The cooperative SchedulerCommand model only suspends at the top statement-stepper level.
            // Inside eagerly-evaluated blocks (loop / function / if bodies, monitors) a returned SLEEP
            // command would otherwise be silently discarded and the sleep would no-op. Honor it here
            // with a blocking fallback so timing is correct. Trade-off: a nested SLEEP blocks the
            // scheduler thread, so other cooperative threads do not advance during it (single-threaded
            // scripts are unaffected; top-level SLEEP stays fully cooperative). See B-branch for the
            // full cooperative fix.
            if (result instanceof SchedulerCommand) {
                SchedulerCommand cmd = (SchedulerCommand) result;
                if (cmd.getType() == SchedulerCommand.CommandType.SLEEP) {
                    sleepBlocking(cmd.getDuration());
                    result = null;
                }
            }
        }
        return result;
    }

    /** Blocking sleep fallback for SLEEP commands honored on the eager (non-stepper) execution path. */
    private void sleepBlocking(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- Loops ---

    @Override
    public Object visitConditionLoop(ProperTeeParser.ConditionLoopContext ctx) {
        checkKeywordAllowed("loop", ctx);
        Object result = null;
        boolean isInfinite = ctx.K_INFINITE() != null;
        int limit = isInfinite ? Integer.MAX_VALUE : maxIterations;
        int iterations = 0;

        try {
            Object condition = eval(ctx.expression());
            while (TypeChecker.isTruthy(condition)) {
                if (++iterations > limit) {
                    if ("warn".equals(iterationLimitBehavior)) {
                        stderr.print(new Object[]{"Warning: Loop exceeded maximum iterations (" + limit + "), stopping loop"});
                        break;
                    } else {
                        throw createError(
                            "Loop exceeded maximum iterations (" + limit + "). Use 'loop condition infinite do' if you need unlimited iterations.",
                            ctx);
                    }
                }

                try {
                    result = evalBlock(ctx.block());
                } catch (BreakException e) {
                    break;
                } catch (ContinueException e) {
                    // continue
                }

                condition = eval(ctx.expression());
            }
        } catch (BreakException e) {
            // break from outer
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitValueLoop(ProperTeeParser.ValueLoopContext ctx) {
        checkKeywordAllowed("loop", ctx);
        Object iterable = eval(ctx.expression());
        boolean isInfinite = ctx.K_INFINITE() != null;
        int limit = isInfinite ? Integer.MAX_VALUE : maxIterations;
        int iterations = 0;
        Object result = null;
        String valueVar = ctx.value.getText();
        Map<String, Object> vars = getVariables();
        ScopeStack ss = getScopeStack();

        if (iterable instanceof List) {
            List<Object> list = (List<Object>) iterable;
            try {
                for (int i = 0; i < list.size(); i++) {
                    if (++iterations > limit) {
                        if ("warn".equals(iterationLimitBehavior)) {
                            stderr.print(new Object[]{"Warning: Loop exceeded maximum iterations (" + limit + "), stopping loop"});
                            break;
                        } else {
                            throw createError("Loop exceeded maximum iterations (" + limit + "). Use 'loop ... infinite do' if you need unlimited iterations.", ctx);
                        }
                    }

                    if (!ss.isEmpty()) {
                        ss.set(valueVar, TypeChecker.deepCopy(list.get(i)));
                    } else {
                        vars.put(valueVar, TypeChecker.deepCopy(list.get(i)));
                    }

                    try {
                        result = evalBlock(ctx.block());
                    } catch (BreakException e) { break; }
                    catch (ContinueException e) { /* continue */ }
                }
            } catch (BreakException e) { /* break */ }
        } else if (iterable instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) iterable;
            try {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (++iterations > limit) {
                        if ("warn".equals(iterationLimitBehavior)) {
                            stderr.print(new Object[]{"Warning: Loop exceeded maximum iterations (" + limit + "), stopping loop"});
                            break;
                        } else {
                            throw createError("Loop exceeded maximum iterations (" + limit + "). Use 'loop ... infinite do' if you need unlimited iterations.", ctx);
                        }
                    }

                    if (!ss.isEmpty()) {
                        ss.set(valueVar, TypeChecker.deepCopy(entry.getValue()));
                    } else {
                        vars.put(valueVar, TypeChecker.deepCopy(entry.getValue()));
                    }

                    try {
                        result = evalBlock(ctx.block());
                    } catch (BreakException e) { break; }
                    catch (ContinueException e) { /* continue */ }
                }
            } catch (BreakException e) { /* break */ }
        } else {
            throw new ProperTeeError("Runtime Error: Cannot iterate over non-iterable value");
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitKeyValueLoop(ProperTeeParser.KeyValueLoopContext ctx) {
        checkKeywordAllowed("loop", ctx);
        Object iterable = eval(ctx.expression());
        boolean isInfinite = ctx.K_INFINITE() != null;
        int limit = isInfinite ? Integer.MAX_VALUE : maxIterations;
        int iterations = 0;
        Object result = null;
        String keyVar = ctx.key.getText();
        String valueVar = ctx.value.getText();
        Map<String, Object> vars = getVariables();
        ScopeStack ss = getScopeStack();

        if (iterable instanceof List) {
            List<Object> list = (List<Object>) iterable;
            try {
                for (int i = 0; i < list.size(); i++) {
                    if (++iterations > limit) {
                        if ("warn".equals(iterationLimitBehavior)) {
                            stderr.print(new Object[]{"Warning: Loop exceeded maximum iterations (" + limit + "), stopping loop"});
                            break;
                        } else {
                            throw createError("Loop exceeded maximum iterations (" + limit + "). Use 'loop ... infinite do' if you need unlimited iterations.", ctx);
                        }
                    }

                    // 1-based index for arrays
                    Object keyVal = i + 1;
                    if (!ss.isEmpty()) {
                        ss.set(keyVar, keyVal);
                        ss.set(valueVar, TypeChecker.deepCopy(list.get(i)));
                    } else {
                        vars.put(keyVar, keyVal);
                        vars.put(valueVar, TypeChecker.deepCopy(list.get(i)));
                    }

                    try {
                        result = evalBlock(ctx.block());
                    } catch (BreakException e) { break; }
                    catch (ContinueException e) { /* continue */ }
                }
            } catch (BreakException e) { /* break */ }
        } else if (iterable instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) iterable;
            try {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (++iterations > limit) {
                        if ("warn".equals(iterationLimitBehavior)) {
                            stderr.print(new Object[]{"Warning: Loop exceeded maximum iterations (" + limit + "), stopping loop"});
                            break;
                        } else {
                            throw createError("Loop exceeded maximum iterations (" + limit + "). Use 'loop ... infinite do' if you need unlimited iterations.", ctx);
                        }
                    }

                    if (!ss.isEmpty()) {
                        ss.set(keyVar, entry.getKey());
                        ss.set(valueVar, TypeChecker.deepCopy(entry.getValue()));
                    } else {
                        vars.put(keyVar, entry.getKey());
                        vars.put(valueVar, TypeChecker.deepCopy(entry.getValue()));
                    }

                    try {
                        result = evalBlock(ctx.block());
                    } catch (BreakException e) { break; }
                    catch (ContinueException e) { /* continue */ }
                }
            } catch (BreakException e) { /* break */ }
        } else {
            throw new ProperTeeError("Runtime Error: Cannot iterate over non-iterable value");
        }

        return result;
    }

    // --- Flow control ---

    @Override
    public Object visitBreakStmt(ProperTeeParser.BreakStmtContext ctx) {
        throw new BreakException();
    }

    @Override
    public Object visitContinueStmt(ProperTeeParser.ContinueStmtContext ctx) {
        throw new ContinueException();
    }

    @Override
    public Object visitReturnStmt(ProperTeeParser.ReturnStmtContext ctx) {
        Object value = ctx.expression() != null ? eval(ctx.expression()) : new LinkedHashMap<String, Object>();
        throw new ReturnException(value);
    }

    @Override
    public Object visitDebugStmt(ProperTeeParser.DebugStmtContext ctx) {
        checkKeywordAllowed("debug", ctx);
        // No-op in normal execution; playground debug mode handles this in the scheduler
        return new LinkedHashMap<String, Object>();
    }

    // --- Function definition ---

    @Override
    public Object visitFunctionDef(ProperTeeParser.FunctionDefContext ctx) {
        checkKeywordAllowed("function", ctx);
        String funcName = ctx.funcName.getText();
        List<String> params = new ArrayList<String>();
        if (ctx.parameterList() != null) {
            for (org.antlr.v4.runtime.tree.TerminalNode id : ctx.parameterList().ID()) {
                params.add(id.getText());
            }
        }
        userDefinedFunctions.put(funcName, new FunctionDef(funcName, params, ctx.block()));
        return null;
    }

    // --- Expressions ---

    @Override
    public Object visitAtomExpr(ProperTeeParser.AtomExprContext ctx) {
        return eval(ctx.atom());
    }

    @Override
    public Object visitVarReference(ProperTeeParser.VarReferenceContext ctx) {
        String name = ctx.ID().getText();
        ScopeStack ss = getScopeStack();
        Map<String, Object> vars = getVariables();
        Map<String, Object> multiVars = getMultiResultVars();

        if (isInMultiContext() && multiVars.containsKey(name)) {
            throw createError(
                "Cannot use result variable '" + name + "' inside MULTI block. Result variables are only available after 'end'.",
                ctx);
        }

        // 1. Local scopes
        Object val = ss.get(name);
        if (val != ScopeStack.UNDEFINED) return val;

        // 2. Multi result vars
        if (multiVars.containsKey(name)) return multiVars.get(name);

        // Inside a function: plain variables are local-only, no fallthrough to globals
        if (isInFunctionScope()) {
            throw createError(
                "Variable '" + name + "' is not defined in local scope. Use ::" + name + " to access the global variable.",
                ctx);
        }

        // 3. Variables (global or snapshot) — top-level only
        if (vars.containsKey(name)) return vars.get(name);

        // 4. Built-in properties — top-level only
        if (properties.containsKey(name)) return properties.get(name);

        throw createError("Variable '" + name + "' is not defined", ctx);
    }

    @Override
    public Object visitGlobalVarReference(ProperTeeParser.GlobalVarReferenceContext ctx) {
        String name = ctx.ID().getText();
        Map<String, Object> vars = getVariables();

        // Global variables
        if (vars.containsKey(name)) return vars.get(name);

        // Built-in properties
        if (properties.containsKey(name)) return properties.get(name);

        throw createError("Global variable '" + name + "' is not defined", ctx);
    }

    @Override
    public Object visitIntegerAtom(ProperTeeParser.IntegerAtomContext ctx) {
        return Integer.parseInt(ctx.getText());
    }

    @Override
    public Object visitDecimalAtom(ProperTeeParser.DecimalAtomContext ctx) {
        return Double.parseDouble(ctx.getText());
    }

    @Override
    public Object visitStringAtom(ProperTeeParser.StringAtomContext ctx) {
        String str = ctx.getText();
        return processStringEscapes(str.substring(1, str.length() - 1));
    }

    @Override
    public Object visitBooleanAtom(ProperTeeParser.BooleanAtomContext ctx) {
        return "true".equals(ctx.getText());
    }

    @Override
    public Object visitParenAtom(ProperTeeParser.ParenAtomContext ctx) {
        return eval(ctx.expression());
    }

    @Override
    public Object visitObjectAtom(ProperTeeParser.ObjectAtomContext ctx) {
        return eval(ctx.objectLiteral());
    }

    @Override
    public Object visitObjectLiteral(ProperTeeParser.ObjectLiteralContext ctx) {
        Map<String, Object> obj = new LinkedHashMap<String, Object>();
        if (ctx.objectEntry() == null) return obj;

        for (ProperTeeParser.ObjectEntryContext entryCtx : ctx.objectEntry()) {
            String key = resolveObjectKey(entryCtx.objectKey());
            Object value = eval(entryCtx.expression());
            obj.put(key, value);
        }
        return obj;
    }

    private String resolveObjectKey(ProperTeeParser.ObjectKeyContext ctx) {
        if (ctx.STRING() != null) {
            String str = ctx.STRING().getText();
            return processStringEscapes(str.substring(1, str.length() - 1));
        }
        if (ctx.INTEGER() != null) return ctx.INTEGER().getText();
        return null;
    }

    @Override
    public Object visitArrayAtom(ProperTeeParser.ArrayAtomContext ctx) {
        return eval(ctx.arrayLiteral());
    }

    @Override
    public Object visitListArray(ProperTeeParser.ListArrayContext ctx) {
        List<Object> arr = new ArrayList<Object>();
        if (ctx.expression() == null) return arr;

        for (ProperTeeParser.ExpressionContext exprCtx : ctx.expression()) {
            arr.add(eval(exprCtx));
        }
        return arr;
    }

    @Override
    public Object visitRangeArray(ProperTeeParser.RangeArrayContext ctx) {
        Object startVal = eval(ctx.rangeStart);
        Object endVal = eval(ctx.rangeEnd);

        if (!(startVal instanceof Number) || !(endVal instanceof Number)) {
            throw createError("Range bounds must be numbers", ctx);
        }

        Object stepVal = null;
        if (ctx.rangeStep != null) {
            stepVal = eval(ctx.rangeStep);
            if (!(stepVal instanceof Number)) {
                throw createError("Range step must be a number", ctx);
            }
            if (((Number) stepVal).doubleValue() <= 0) {
                throw createError("Range step must be positive", ctx);
            }
        }

        boolean useIntegers = (startVal instanceof Integer) && (endVal instanceof Integer)
                && (stepVal == null || stepVal instanceof Integer);

        List<Object> arr = new ArrayList<Object>();
        if (useIntegers) {
            long start = ((Integer) startVal).longValue();
            long end = ((Integer) endVal).longValue();
            long step = stepVal == null ? 1L : ((Integer) stepVal).longValue();

            if (start > end) {
                step = -step;
            }

            if (step > 0) {
                for (long i = start; i <= end; i += step) arr.add((int) i);
            } else {
                for (long i = start; i >= end; i += step) arr.add((int) i);
            }
        } else {
            BigDecimal start = toBigDecimal((Number) startVal);
            BigDecimal end = toBigDecimal((Number) endVal);
            BigDecimal step = stepVal == null ? BigDecimal.ONE : toBigDecimal((Number) stepVal);

            if (start.compareTo(end) > 0) {
                step = step.negate();
            }

            if (step.signum() > 0) {
                for (BigDecimal value = start; value.compareTo(end) <= 0; value = value.add(step)) {
                    arr.add(boxRangeNumber(value));
                }
            } else {
                for (BigDecimal value = start; value.compareTo(end) >= 0; value = value.add(step)) {
                    arr.add(boxRangeNumber(value));
                }
            }
        }
        return arr;
    }

    private static BigDecimal toBigDecimal(Number value) {
        if (value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        return BigDecimal.valueOf(value.doubleValue());
    }

    private static Object boxRangeNumber(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            try {
                return normalized.intValueExact();
            } catch (ArithmeticException e) {
                return normalized.doubleValue();
            }
        }
        return normalized.doubleValue();
    }

    // --- Member access ---

    @Override
    @SuppressWarnings("unchecked")
    public Object visitMemberAccessExpr(ProperTeeParser.MemberAccessExprContext ctx) {
        Object targetObj = eval(ctx.expression());
        Object key = eval(ctx.access());

        if (targetObj == null) {
            throw createError("Cannot access property '" + key + "' of null", ctx);
        }

        return getProperty(targetObj, key, ctx);
    }

    @SuppressWarnings("unchecked")
    public Object getProperty(Object target, Object key, org.antlr.v4.runtime.ParserRuleContext ctx) {
        if (target instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) target;
            String strKey = String.valueOf(key);
            if (!map.containsKey(strKey)) {
                if (ctx != null) throw createError("Property '" + key + "' does not exist", ctx);
                throw new ProperTeeError("Runtime Error: Property '" + key + "' does not exist");
            }
            return map.get(strKey);
        }
        if (target instanceof List) {
            List<Object> list = (List<Object>) target;
            if (!(key instanceof Number)) {
                String msg = "Array index must be a number, got string. Use arr.1 not arr.\"1\"";
                if (ctx != null) throw createError(msg, ctx);
                throw new ProperTeeError("Runtime Error: " + msg);
            }
            int index = ((Number) key).intValue() - 1; // 1-based to 0-based
            if (index < 0 || index >= list.size()) {
                if (ctx != null) throw createError("Array index out of bounds", ctx);
                throw new ProperTeeError("Runtime Error: Array index out of bounds");
            }
            return list.get(index);
        }
        if (target instanceof String) {
            // String character access
            String s = (String) target;
            if (!(key instanceof Number)) {
                String msg = "String index must be a number, got string. Use str.1 not str.\"1\"";
                if (ctx != null) throw createError(msg, ctx);
                throw new ProperTeeError("Runtime Error: " + msg);
            }
            int index = ((Number) key).intValue() - 1; // 1-based to 0-based
            if (index < 0 || index >= s.length()) {
                if (ctx != null) throw createError("String index out of bounds", ctx);
                throw new ProperTeeError("Runtime Error: String index out of bounds");
            }
            return String.valueOf(s.charAt(index));
        }

        if (ctx != null) throw createError("Cannot access property '" + key + "' on " + TypeChecker.typeOf(target), ctx);
        throw new ProperTeeError("Runtime Error: Cannot access property '" + key + "' on " + TypeChecker.typeOf(target));
    }

    // --- Access visitors ---

    @Override
    public Object visitStaticAccess(ProperTeeParser.StaticAccessContext ctx) {
        return ctx.ID().getText();
    }

    @Override
    public Object visitVarEvalAccess(ProperTeeParser.VarEvalAccessContext ctx) {
        String varName = ctx.ID().getText();

        // $::var — resolve from globals/properties directly (same as ::var)
        if (ctx.GLOBAL_PREFIX() != null) {
            Map<String, Object> vars = getVariables();
            if (vars.containsKey(varName)) return vars.get(varName);
            if (properties.containsKey(varName)) return properties.get(varName);
            return null;
        }

        ScopeStack ss = getScopeStack();
        Map<String, Object> vars = getVariables();

        Object val = ss.get(varName);
        if (val != ScopeStack.UNDEFINED) return val;

        // Inside a function: $key only checks local scope
        if (isInFunctionScope()) return null;

        if (vars.containsKey(varName)) return vars.get(varName);
        if (properties.containsKey(varName)) return properties.get(varName);
        return null;
    }

    @Override
    public Object visitArrayAccess(ProperTeeParser.ArrayAccessContext ctx) {
        return Integer.parseInt(ctx.INTEGER().getText()); // 1-based; getProperty/setProperty convert for arrays
    }

    @Override
    public Object visitStringKeyAccess(ProperTeeParser.StringKeyAccessContext ctx) {
        String str = ctx.STRING().getText();
        return processStringEscapes(str.substring(1, str.length() - 1));
    }

    @Override
    public Object visitEvalAccess(ProperTeeParser.EvalAccessContext ctx) {
        return eval(ctx.expression());
    }

    // --- Operators ---

    @Override
    public Object visitUnaryMinusExpr(ProperTeeParser.UnaryMinusExprContext ctx) {
        Object value = eval(ctx.expression());
        if (!TypeChecker.isNumber(value)) {
            throw createError("Unary minus requires numeric operand. Got -" + TypeChecker.typeOf(value), ctx);
        }
        return TypeChecker.boxNumber(-TypeChecker.toDouble(value));
    }

    @Override
    public Object visitNotExpr(ProperTeeParser.NotExprContext ctx) {
        Object value = eval(ctx.expression());
        if (!TypeChecker.isBoolean(value)) {
            throw createError("Logical NOT requires boolean operand. Got not " + TypeChecker.typeOf(value), ctx);
        }
        return !(Boolean) value;
    }

    @Override
    public Object visitMultiplicativeExpr(ProperTeeParser.MultiplicativeExprContext ctx) {
        Object left = eval(ctx.expression(0));
        Object right = eval(ctx.expression(1));
        String op = ctx.getChild(1).getText();

        if (!TypeChecker.isNumber(left) || !TypeChecker.isNumber(right)) {
            throw createError("Arithmetic operator '" + op + "' requires numeric operands. Got " +
                TypeChecker.typeOf(left) + " " + op + " " + TypeChecker.typeOf(right), ctx);
        }

        double l = TypeChecker.toDouble(left);
        double r = TypeChecker.toDouble(right);

        if ("*".equals(op)) return TypeChecker.boxNumber(l * r);
        if ("/".equals(op) || "%".equals(op)) {
            if (r == 0) throw createError("Division by zero", ctx);
            return "/".equals(op) ? TypeChecker.boxNumber(l / r) : TypeChecker.boxNumber(l % r);
        }
        return null;
    }

    @Override
    public Object visitAdditiveExpr(ProperTeeParser.AdditiveExprContext ctx) {
        Object left = eval(ctx.expression(0));
        Object right = eval(ctx.expression(1));
        String op = ctx.getChild(1).getText();

        if ("+".equals(op)) {
            if (TypeChecker.isNumber(left) && TypeChecker.isNumber(right)) {
                return TypeChecker.boxNumber(TypeChecker.toDouble(left) + TypeChecker.toDouble(right));
            }
            if (TypeChecker.isString(left) || TypeChecker.isString(right)) {
                return TypeChecker.toStringValue(left) + TypeChecker.toStringValue(right);
            }
            throw createError("Addition requires numeric or string operands. Got " +
                TypeChecker.typeOf(left) + " + " + TypeChecker.typeOf(right), ctx);
        }
        if ("-".equals(op)) {
            if (!TypeChecker.isNumber(left) || !TypeChecker.isNumber(right)) {
                throw createError("Subtraction requires numeric operands. Got " +
                    TypeChecker.typeOf(left) + " - " + TypeChecker.typeOf(right), ctx);
            }
            return TypeChecker.boxNumber(TypeChecker.toDouble(left) - TypeChecker.toDouble(right));
        }
        return null;
    }

    @Override
    public Object visitComparisonExpr(ProperTeeParser.ComparisonExprContext ctx) {
        Object left = eval(ctx.expression(0));
        Object right = eval(ctx.expression(1));
        String op = ctx.op.getText();

        if (">".equals(op) || "<".equals(op) || ">=".equals(op) || "<=".equals(op)) {
            if (!TypeChecker.isNumber(left) || !TypeChecker.isNumber(right)) {
                throw createError("Comparison operator '" + op + "' requires numeric operands. Got " +
                    TypeChecker.typeOf(left) + " " + op + " " + TypeChecker.typeOf(right), ctx);
            }
        }

        double l, r;
        switch (op) {
            case ">": return TypeChecker.toDouble(left) > TypeChecker.toDouble(right);
            case "<": return TypeChecker.toDouble(left) < TypeChecker.toDouble(right);
            case ">=": return TypeChecker.toDouble(left) >= TypeChecker.toDouble(right);
            case "<=": return TypeChecker.toDouble(left) <= TypeChecker.toDouble(right);
            case "==": return objectEquals(left, right);
            case "!=": return !objectEquals(left, right);
            default: return false;
        }
    }

    private boolean objectEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            return TypeChecker.toDouble(a) == TypeChecker.toDouble(b);
        }
        return a.equals(b);
    }

    @Override
    public Object visitAndExpr(ProperTeeParser.AndExprContext ctx) {
        Object left = eval(ctx.expression(0));
        Object right = eval(ctx.expression(1));
        if (!TypeChecker.isBoolean(left) || !TypeChecker.isBoolean(right)) {
            throw createError("Logical AND requires boolean operands. Got " +
                TypeChecker.typeOf(left) + " and " + TypeChecker.typeOf(right), ctx);
        }
        return (Boolean) left && (Boolean) right;
    }

    @Override
    public Object visitOrExpr(ProperTeeParser.OrExprContext ctx) {
        Object left = eval(ctx.expression(0));
        Object right = eval(ctx.expression(1));
        if (!TypeChecker.isBoolean(left) || !TypeChecker.isBoolean(right)) {
            throw createError("Logical OR requires boolean operands. Got " +
                TypeChecker.typeOf(left) + " or " + TypeChecker.typeOf(right), ctx);
        }
        return (Boolean) left || (Boolean) right;
    }

    // --- Function call ---

    @Override
    public Object visitFuncAtom(ProperTeeParser.FuncAtomContext ctx) {
        return eval(ctx.functionCall());
    }

    @Override
    public Object visitFunctionCall(ProperTeeParser.FunctionCallContext ctx) {
        String funcName = ctx.funcName.getText();

        // Check function ignore list
        if (ignoredFunctions.contains(funcName)) {
            throw createError("'" + funcName + "' is not available in this environment", ctx);
        }

        // Evaluate arguments
        List<Object> args = new ArrayList<Object>();
        if (ctx.expression() != null) {
            for (ProperTeeParser.ExpressionContext exprCtx : ctx.expression()) {
                args.add(eval(exprCtx));
            }
        }

        // Built-in function
        if (builtins.has(funcName)) {
            String previousCallSiteKey = currentBuiltinCallSiteKey;
            currentBuiltinCallSiteKey = buildBuiltinCallSiteKey(ctx);
            try {
                Object result = builtins.get(funcName).call(args);

                // SLEEP returns a SchedulerCommand - propagate it up
                if (result instanceof SchedulerCommand) {
                    return result;
                }
                return result;
            } finally {
                currentBuiltinCallSiteKey = previousCallSiteKey;
            }
        }

        // User-defined function
        if (userDefinedFunctions.containsKey(funcName)) {
            return callUserFunction(funcName, args, ctx);
        }

        throw createError("Unknown function '" + funcName + "'", ctx);
    }

    private String buildBuiltinCallSiteKey(ProperTeeParser.FunctionCallContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return "unknown";
        }
        return ctx.getStart().getLine() + ":" +
            ctx.getStart().getCharPositionInLine() + ":" +
            ctx.getStart().getStartIndex();
    }

    @SuppressWarnings("unchecked")
    private Object callUserFunction(String funcName, List<Object> args, ProperTeeParser.FunctionCallContext callCtx) {
        FunctionDef funcDef = userDefinedFunctions.get(funcName);
        List<String> params = funcDef.getParams();
        ScopeStack ss = getScopeStack();

        // Argument count validation
        if (args.size() > params.size()) {
            throw createError(
                "Function '" + funcName + "' expects " + params.size() + " argument(s), but " + args.size() + " were provided",
                callCtx);
        }

        // Create local scope
        Map<String, Object> localScope = new LinkedHashMap<String, Object>();
        for (int i = 0; i < params.size(); i++) {
            localScope.put(params.get(i), i < args.size() ? TypeChecker.deepCopy(args.get(i)) : new LinkedHashMap<String, Object>());
        }

        // Push scope
        ss.push(localScope);

        try {
            // Run the body via evalBlock so a SLEEP command in the body is honored (blocking fallback
            // on this eager call path) instead of being silently discarded. ReturnException still
            // propagates out to the catch below.
            evalBlock(funcDef.getBody());

            // No explicit return: result is empty object
            return new LinkedHashMap<String, Object>();
        } catch (ReturnException e) {
            return e.getValue();
        } finally {
            ss.pop();
        }
    }

    // --- SPAWN statements ---

    private String resolveAndValidateDynamicKey(Object keyValue, org.antlr.v4.runtime.ParserRuleContext ctx) {
        String key = TypeChecker.toStringValue(keyValue);
        if (key.isEmpty()) {
            return null; // treat empty as unnamed (auto-keyed)
        }
        // Duplicate key check
        for (SpawnSpec existing : collectedSpawns) {
            if (existing.resultKey != null && existing.resultKey.equals(key)) {
                throw createError("Duplicate result key '" + key + "' in multi block", ctx);
            }
        }
        return key;
    }

    private String processStringEscapes(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    default: sb.append('\\'); sb.append(next); break;
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public Object visitSpawnKeyStmt(ProperTeeParser.SpawnKeyStmtContext ctx) {
        checkKeywordAllowed("thread", ctx);
        if (!inMultiSetup) {
            throw createError("thread can only be used inside multi blocks", ctx);
        }
        ProperTeeParser.FunctionCallContext funcCallCtx = ctx.functionCall();
        String funcName = funcCallCtx.funcName.getText();

        // Resolve key using access rule (same as property access)
        String keyName = null;
        ProperTeeParser.AccessContext accessCtx = ctx.access();
        if (accessCtx != null) {
            if (accessCtx instanceof ProperTeeParser.StaticAccessContext) {
                keyName = ((ProperTeeParser.StaticAccessContext) accessCtx).ID().getText();
            } else if (accessCtx instanceof ProperTeeParser.StringKeyAccessContext) {
                String raw = ((ProperTeeParser.StringKeyAccessContext) accessCtx).STRING().getText();
                String key = processStringEscapes(raw.substring(1, raw.length() - 1));
                keyName = key.isEmpty() ? null : key; // empty string treated as unnamed
            } else if (accessCtx instanceof ProperTeeParser.ArrayAccessContext) {
                keyName = ((ProperTeeParser.ArrayAccessContext) accessCtx).INTEGER().getText();
            } else if (accessCtx instanceof ProperTeeParser.VarEvalAccessContext) {
                ProperTeeParser.VarEvalAccessContext varCtx = (ProperTeeParser.VarEvalAccessContext) accessCtx;
                String varName = varCtx.ID().getText();
                Object keyValue;

                if (varCtx.GLOBAL_PREFIX() != null) {
                    // $::var — resolve from globals/properties directly
                    Map<String, Object> vars = getVariables();
                    if (vars.containsKey(varName)) {
                        keyValue = vars.get(varName);
                    } else if (properties.containsKey(varName)) {
                        keyValue = properties.get(varName);
                    } else {
                        throw createError("Variable '" + varName + "' is not defined", ctx);
                    }
                } else {
                    ScopeStack ss = getScopeStack();
                    Map<String, Object> vars = getVariables();

                    keyValue = ss.get(varName);
                    if (keyValue == ScopeStack.UNDEFINED) {
                        if (isInFunctionScope()) {
                            throw createError(
                                "Variable '" + varName + "' is not defined in local scope. Use ::" + varName + " to access the global variable.",
                                ctx);
                        }
                        if (vars.containsKey(varName)) {
                            keyValue = vars.get(varName);
                        } else if (properties.containsKey(varName)) {
                            keyValue = properties.get(varName);
                        } else {
                            throw createError("Variable '" + varName + "' is not defined", ctx);
                        }
                    }
                }
                keyName = resolveAndValidateDynamicKey(keyValue, ctx);
            } else if (accessCtx instanceof ProperTeeParser.EvalAccessContext) {
                Object keyValue = eval(((ProperTeeParser.EvalAccessContext) accessCtx).expression());
                keyName = resolveAndValidateDynamicKey(keyValue, ctx);
            }
            // Duplicate key check
            for (SpawnSpec existing : collectedSpawns) {
                if (existing.resultKey != null && existing.resultKey.equals(keyName)) {
                    throw createError("Duplicate result key '" + keyName + "' in multi block", ctx);
                }
            }
        }

        // Evaluate arguments now (during setup phase)
        List<Object> args = new ArrayList<Object>();
        if (funcCallCtx.expression() != null) {
            for (ProperTeeParser.ExpressionContext exprCtx : funcCallCtx.expression()) {
                args.add(eval(exprCtx));
            }
        }

        collectedSpawns.add(new SpawnSpec(funcName, args, keyName, funcCallCtx));
        return null;
    }

    // --- Parallel / MULTI ---

    @Override
    @SuppressWarnings("unchecked")
    public Object visitParallelStmt(ProperTeeParser.ParallelStmtContext ctx) {
        checkKeywordAllowed("multi", ctx);
        Map<String, Object> vars = getVariables();

        // Extract result variable name from [resultVar] syntax (nullable)
        String resultVarName = ctx.resultVar != null ? ctx.resultVar.getText() : null;

        // Deep-copy snapshot of globals for thread purity
        Map<String, Object> globalSnapshot = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            globalSnapshot.put(entry.getKey(), TypeChecker.deepCopy(entry.getValue()));
        }

        // Setup phase: execute the block body, collecting SPAWN specs
        // Push a scope so setup variables don't leak (:: required for globals, like functions)
        inMultiSetup = true;
        collectedSpawns = new ArrayList<SpawnSpec>();
        getScopeStack().push(new LinkedHashMap<String, Object>());

        boolean setupSucceeded = false;
        try {
            evalBlock(ctx.block());
            setupSucceeded = true;
        } finally {
            getScopeStack().pop();
            inMultiSetup = false;
            // If setup threw, drop collected spawns so failed multi state
            // doesn't survive in the interpreter (REPL/embedded scenarios).
            if (!setupSucceeded) {
                collectedSpawns = null;
            }
        }

        // If no spawns were collected, assign empty map if result var specified
        if (collectedSpawns.isEmpty()) {
            collectedSpawns = null;
            if (resultVarName != null) {
                ScopeStack ss = getScopeStack();
                if (!ss.isEmpty()) {
                    ss.set(resultVarName, new LinkedHashMap<String, Object>());
                } else {
                    vars.put(resultVarName, new LinkedHashMap<String, Object>());
                }
            }
            return null;
        }

        // Resolve auto-keys and build thread specs in a try/finally to ensure
        // collectedSpawns is dropped even if validation/build throws.
        List<String> resultKeyNames = new ArrayList<String>();
        List<SchedulerCommand.ThreadSpec> specs = new ArrayList<SchedulerCommand.ThreadSpec>();
        try {
            Set<String> allKeys = new LinkedHashSet<String>();
            int autoPos = 1;
            for (int i = 0; i < collectedSpawns.size(); i++) {
                SpawnSpec spawn = collectedSpawns.get(i);
                if (spawn.resultKey != null) {
                    allKeys.add(spawn.resultKey);
                }
            }
            for (int i = 0; i < collectedSpawns.size(); i++) {
                SpawnSpec spawn = collectedSpawns.get(i);
                if (spawn.resultKey == null) {
                    String autoKey = "#" + autoPos;
                    if (allKeys.contains(autoKey)) {
                        throw createError("Auto-generated key '" + autoKey + "' conflicts with an explicit key in multi block", spawn.ctx);
                    }
                    allKeys.add(autoKey);
                    // Update the spawn's resultKey by replacing the entry
                    collectedSpawns.set(i, new SpawnSpec(spawn.funcName, spawn.args, autoKey, spawn.ctx));
                    autoPos++;
                }
            }

            for (int i = 0; i < collectedSpawns.size(); i++) {
                SpawnSpec spawn = collectedSpawns.get(i);
                resultKeyNames.add(spawn.resultKey);

                if (userDefinedFunctions.containsKey(spawn.funcName)) {
                    FunctionDef funcDef = userDefinedFunctions.get(spawn.funcName);
                    List<String> params = funcDef.getParams();

                    // Argument count validation
                    if (spawn.args.size() > params.size()) {
                        throw createError(
                            "Function '" + spawn.funcName + "' expects " + params.size() + " argument(s), but " + spawn.args.size() + " were provided",
                            spawn.ctx);
                    }

                    Map<String, Object> localScope = new LinkedHashMap<String, Object>();
                    for (int j = 0; j < params.size(); j++) {
                        localScope.put(params.get(j), j < spawn.args.size() ? TypeChecker.deepCopy(spawn.args.get(j)) : new LinkedHashMap<String, Object>());
                    }

                    Stepper threadStepper = new StatementListStepper(
                        this, funcDef.getBody().statement(), localScope, true);
                    specs.add(new SchedulerCommand.ThreadSpec(spawn.funcName + "-" + i, threadStepper, localScope));

                } else if (builtins.has(spawn.funcName)) {
                    // Check function ignore list
                    if (ignoredFunctions.contains(spawn.funcName)) {
                        throw createError("'" + spawn.funcName + "' is not available in this environment", spawn.ctx);
                    }
                    // Built-in function: execute immediately and wrap result. If it returns a
                    // SchedulerCommand (e.g. SLEEP), yield that command to the scheduler so the worker
                    // actually sleeps, instead of treating the command object as the thread's result.
                    Object builtinResult = builtins.get(spawn.funcName).call(spawn.args);
                    Stepper builtinStepper;
                    if (builtinResult instanceof SchedulerCommand) {
                        builtinStepper = new CommandThenDoneStepper(
                            (SchedulerCommand) builtinResult, new LinkedHashMap<String, Object>());
                    } else {
                        builtinStepper = new ImmediateStepper(builtinResult);
                    }
                    specs.add(new SchedulerCommand.ThreadSpec("builtin-" + spawn.funcName + "-" + i, builtinStepper, null));
                } else {
                    throw createError("Unknown function '" + spawn.funcName + "'", spawn.ctx);
                }
            }
        } finally {
            collectedSpawns = null;
        }

        // Monitor spec
        SchedulerCommand.MonitorSpec monitorSpec = null;
        if (ctx.monitorClause() != null) {
            ProperTeeParser.MonitorClauseContext mc = ctx.monitorClause();
            int interval = Integer.parseInt(mc.INTEGER().getText());
            monitorSpec = new SchedulerCommand.MonitorSpec(interval, mc.block());
        }

        // Return the SPAWN_THREADS command (the scheduler will handle this)
        return SchedulerCommand.spawnThreads(specs, monitorSpec, globalSnapshot, resultKeyNames, resultVarName);
    }

    // --- LValue visitors (for property access in expressions) ---

    @Override
    public Object visitVarLValue(ProperTeeParser.VarLValueContext ctx) {
        String varName = ctx.ID().getText();
        ScopeStack ss = getScopeStack();
        Map<String, Object> vars = getVariables();

        Object val = ss.get(varName);
        if (val != ScopeStack.UNDEFINED) return val;

        // Inside a function: plain variables are local-only
        if (isInFunctionScope()) {
            throw new ProperTeeError("Runtime Error: Variable '" + varName + "' is not defined in local scope. Use ::" + varName + " to access the global variable.");
        }

        if (vars.containsKey(varName)) return vars.get(varName);
        if (properties.containsKey(varName)) return properties.get(varName);
        throw new ProperTeeError("Runtime Error: Variable '" + varName + "' is not defined");
    }

    @Override
    public Object visitGlobalVarLValue(ProperTeeParser.GlobalVarLValueContext ctx) {
        String varName = ctx.ID().getText();

        // Global variables (always real globals, not snapshot)
        if (variables.containsKey(varName)) return variables.get(varName);

        // Built-in properties
        if (properties.containsKey(varName)) return properties.get(varName);

        throw new ProperTeeError("Runtime Error: Global variable '" + varName + "' is not defined");
    }

    @Override
    public Object visitPropLValue(ProperTeeParser.PropLValueContext ctx) {
        Object targetObj = eval(ctx.lvalue());
        Object key = eval(ctx.access());

        if (targetObj == null) throw new ProperTeeError("Runtime Error: Cannot access property '" + key + "' of null");
        return getProperty(targetObj, key, null);
    }

    // --- Comparison op ---
    @Override
    public Object visitComparisonOp(ProperTeeParser.ComparisonOpContext ctx) {
        return ctx.getText();
    }
}
