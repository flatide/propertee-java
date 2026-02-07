package com.propertee.interpreter;

import com.propertee.parser.ProperTeeBaseVisitor;
import com.propertee.parser.ProperTeeParser;
import com.propertee.runtime.*;
import com.propertee.stepper.*;
import com.propertee.scheduler.ThreadContext;

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
        public final String resultVarName; // null for fire-and-forget
        public final org.antlr.v4.runtime.ParserRuleContext ctx;

        public SpawnSpec(String funcName, List<Object> args, String resultVarName,
                         org.antlr.v4.runtime.ParserRuleContext ctx) {
            this.funcName = funcName;
            this.args = args;
            this.resultVarName = resultVarName;
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

    // I/O
    public BuiltinFunctions.PrintFunction stdout;
    public BuiltinFunctions.PrintFunction stderr;

    public ProperTeeInterpreter(Map<String, Object> properties, BuiltinFunctions.PrintFunction stdout,
                                 BuiltinFunctions.PrintFunction stderr, int maxIterations, String iterationLimitBehavior) {
        this.properties = properties != null ? properties : new LinkedHashMap<String, Object>();
        this.stdout = stdout;
        this.stderr = stderr;
        this.maxIterations = maxIterations;
        this.iterationLimitBehavior = iterationLimitBehavior;
        this.builtins = new BuiltinFunctions(stdout, stderr);
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

    public boolean isInFunctionScope() {
        return !getScopeStack().isEmpty();
    }

    // --- Evaluate an expression node eagerly ---
    public Object eval(org.antlr.v4.runtime.tree.ParseTree ctx) {
        if (ctx == null) return null;
        return ctx.accept(this);
    }

    // --- Create a Stepper for a statement/block ---
    public Stepper createRootStepper(ProperTeeParser.RootContext ctx) {
        return new RootStepper(this, ctx);
    }

    public Stepper createBlockStepper(ProperTeeParser.BlockContext ctx) {
        return new BlockStepper(this, ctx);
    }

    // --- Root and Block Steppers (inner classes) ---

    /**
     * Process collected results from SPAWN_THREADS command (sent back by scheduler).
     * Assigns thread results to the appropriate variables.
     */
    @SuppressWarnings("unchecked")
    private static void processSpawnResults(ProperTeeInterpreter interp, Object sendValue) {
        if (sendValue == null) return;
        List<Map<String, Object>> collectedResults = (List<Map<String, Object>>) sendValue;
        ScopeStack ss = interp.getScopeStack();
        Map<String, Object> vars = interp.getVariables();

        for (Map<String, Object> entry : collectedResults) {
            String varName = (String) entry.get("varName");
            if (varName != null) {
                Object finalValue = entry.get("result"); // Already {ok, value} Result from Scheduler
                if (!ss.isEmpty()) {
                    ss.set(varName, finalValue);
                } else {
                    vars.put(varName, finalValue);
                }
            }
        }
    }

    /** Root stepper: runs all statements with boundaries, catches ReturnException */
    public static class RootStepper implements Stepper {
        private final ProperTeeInterpreter interp;
        private final List<ProperTeeParser.StatementContext> statements;
        private int index = 0;
        private Object result = null;
        private boolean done = false;
        private boolean yieldBoundary = false;
        private Object sendValue;
        private boolean waitingForSpawn = false;

        public RootStepper(ProperTeeInterpreter interp, ProperTeeParser.RootContext ctx) {
            this.interp = interp;
            this.statements = ctx.statement();
        }

        @Override
        public StepResult step() {
            if (done) return StepResult.done(result);

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
                    done = true;
                    result = null;
                    return StepResult.done(result);
                }
            }

            if (yieldBoundary) {
                yieldBoundary = false;
                return StepResult.BOUNDARY;
            }

            if (index < statements.size()) {
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
                    index++;
                    if (index < statements.size()) {
                        yieldBoundary = true;
                        return StepResult.BOUNDARY;
                    } else {
                        done = true;
                        return StepResult.done(result);
                    }
                } catch (ReturnException e) {
                    result = e.getValue();
                    done = true;
                    return StepResult.done(result);
                }
            }

            done = true;
            return StepResult.done(result);
        }

        @Override
        public boolean isDone() { return done; }
        @Override
        public Object getResult() { return result; }
        @Override
        public void setSendValue(Object value) { this.sendValue = value; }
    }

    /** Block stepper: runs all statements with boundaries */
    public static class BlockStepper implements Stepper {
        private final ProperTeeInterpreter interp;
        private final List<ProperTeeParser.StatementContext> statements;
        private int index = 0;
        private Object result = null;
        private boolean done = false;
        private boolean yieldBoundary = false;
        private Object sendValue;
        private boolean waitingForSpawn = false;

        public BlockStepper(ProperTeeInterpreter interp, ProperTeeParser.BlockContext ctx) {
            this.interp = interp;
            this.statements = ctx.statement();
        }

        @Override
        public StepResult step() {
            if (done) return StepResult.done(result);

            if (waitingForSpawn && sendValue != null) {
                processSpawnResults(interp, sendValue);
                sendValue = null;
                waitingForSpawn = false;
                if (index < statements.size()) {
                    yieldBoundary = true;
                    return StepResult.BOUNDARY;
                } else {
                    done = true;
                    result = null;
                    return StepResult.done(result);
                }
            }

            if (yieldBoundary) {
                yieldBoundary = false;
                return StepResult.BOUNDARY;
            }

            if (index < statements.size()) {
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
                index++;
                if (index < statements.size()) {
                    yieldBoundary = true;
                    return StepResult.BOUNDARY;
                } else {
                    done = true;
                    return StepResult.done(result);
                }
            }

            done = true;
            return StepResult.done(result);
        }

        @Override
        public boolean isDone() { return done; }
        @Override
        public Object getResult() { return result; }
        @Override
        public void setSendValue(Object value) { this.sendValue = value; }
    }

    /** FunctionCall stepper: pushes scope, runs body with boundaries, pops scope */
    public static class FunctionCallStepper implements Stepper {
        private final ProperTeeInterpreter interp;
        private final FunctionDef funcDef;
        private final Map<String, Object> localScope;
        private final List<ProperTeeParser.StatementContext> statements;
        private int index = 0;
        private Object result = null;
        private boolean done = false;
        private boolean yieldBoundary = false;
        private boolean scopePushed = false;
        private Object sendValue;
        private boolean waitingForSpawn = false;

        public FunctionCallStepper(ProperTeeInterpreter interp, FunctionDef funcDef,
                                    Map<String, Object> localScope) {
            this.interp = interp;
            this.funcDef = funcDef;
            this.localScope = localScope;
            this.statements = funcDef.getBody().statement();
        }

        @Override
        public StepResult step() {
            if (done) return StepResult.done(result);

            if (!scopePushed) {
                interp.getScopeStack().push(localScope);
                scopePushed = true;
            }

            if (waitingForSpawn && sendValue != null) {
                processSpawnResults(interp, sendValue);
                sendValue = null;
                waitingForSpawn = false;
                if (index < statements.size()) {
                    yieldBoundary = true;
                    return StepResult.BOUNDARY;
                } else {
                    return finish(new LinkedHashMap<String, Object>());
                }
            }

            if (yieldBoundary) {
                yieldBoundary = false;
                return StepResult.BOUNDARY;
            }

            if (index < statements.size()) {
                try {
                    Object evalResult = interp.eval(statements.get(index));
                    if (evalResult instanceof SchedulerCommand) {
                        SchedulerCommand cmd = (SchedulerCommand) evalResult;
                        result = null;
                        index++;
                        if (cmd.getType() == SchedulerCommand.CommandType.SPAWN_THREADS) {
                            waitingForSpawn = true;
                        }
                        return StepResult.command(cmd);
                    }
                    index++;
                    if (index < statements.size()) {
                        yieldBoundary = true;
                        return StepResult.BOUNDARY;
                    } else {
                        return finish(new LinkedHashMap<String, Object>());
                    }
                } catch (ReturnException e) {
                    return finish(e.getValue());
                }
            }

            return finish(new LinkedHashMap<String, Object>());
        }

        private StepResult finish(Object val) {
            interp.getScopeStack().pop();
            result = val;
            done = true;
            return StepResult.done(result);
        }

        @Override
        public boolean isDone() { return done; }
        @Override
        public Object getResult() { return result; }
        @Override
        public void setSendValue(Object value) { this.sendValue = value; }
    }

    /** Thread generator stepper: used for MULTI block child threads */
    public static class ThreadGeneratorStepper implements Stepper {
        private final ProperTeeInterpreter interp;
        private final FunctionDef funcDef;
        private final Map<String, Object> localScope;
        private final List<ProperTeeParser.StatementContext> statements;
        private int index = 0;
        private Object result = null;
        private boolean done = false;
        private boolean yieldBoundary = false;
        private boolean scopePushed = false;
        private Object sendValue;
        private boolean waitingForSpawn = false;

        public ThreadGeneratorStepper(ProperTeeInterpreter interp, FunctionDef funcDef,
                                       Map<String, Object> localScope) {
            this.interp = interp;
            this.funcDef = funcDef;
            this.localScope = localScope;
            this.statements = funcDef.getBody().statement();
        }

        @Override
        public StepResult step() {
            if (done) return StepResult.done(result);

            if (!scopePushed) {
                interp.getScopeStack().push(localScope);
                scopePushed = true;
            }

            if (waitingForSpawn && sendValue != null) {
                processSpawnResults(interp, sendValue);
                sendValue = null;
                waitingForSpawn = false;
                if (index < statements.size()) {
                    yieldBoundary = true;
                    return StepResult.BOUNDARY;
                } else {
                    return finish(new LinkedHashMap<String, Object>());
                }
            }

            if (yieldBoundary) {
                yieldBoundary = false;
                return StepResult.BOUNDARY;
            }

            if (index < statements.size()) {
                try {
                    Object evalResult = interp.eval(statements.get(index));
                    if (evalResult instanceof SchedulerCommand) {
                        SchedulerCommand cmd = (SchedulerCommand) evalResult;
                        result = null;
                        index++;
                        if (cmd.getType() == SchedulerCommand.CommandType.SPAWN_THREADS) {
                            waitingForSpawn = true;
                        }
                        return StepResult.command(cmd);
                    }
                    index++;
                    if (index < statements.size()) {
                        yieldBoundary = true;
                        return StepResult.BOUNDARY;
                    } else {
                        return finish(new LinkedHashMap<String, Object>());
                    }
                } catch (ReturnException e) {
                    return finish(e.getValue());
                }
            }

            return finish(new LinkedHashMap<String, Object>());
        }

        private StepResult finish(Object val) {
            interp.getScopeStack().pop();
            result = val;
            done = true;
            return StepResult.done(result);
        }

        @Override
        public boolean isDone() { return done; }
        @Override
        public Object getResult() { return result; }
        @Override
        public void setSendValue(Object value) { this.sendValue = value; }
    }

    /** Parallel stepper: for MULTI blocks, yields SPAWN_THREADS command then waits for results */
    public static class ParallelStepper implements Stepper {
        private final ProperTeeInterpreter interp;
        private final SchedulerCommand command;
        private boolean done = false;
        private boolean commandSent = false;
        private Object result = null;
        private Object sendValue = null;

        public ParallelStepper(ProperTeeInterpreter interp, SchedulerCommand command) {
            this.interp = interp;
            this.command = command;
        }

        @Override
        public StepResult step() {
            if (done) return StepResult.done(result);

            if (!commandSent) {
                commandSent = true;
                return StepResult.command(command);
            }

            // After resume with sendValue (collected results from scheduler)
            if (sendValue != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> collectedResults = (List<Map<String, Object>>) sendValue;
                ScopeStack ss = interp.getScopeStack();
                Map<String, Object> vars = interp.getVariables();

                for (Map<String, Object> entry : collectedResults) {
                    String varName = (String) entry.get("varName");
                    if (varName != null) {
                        Object finalValue = entry.get("result"); // Already {ok, value} Result from Scheduler
                        if (!ss.isEmpty()) {
                            ss.set(varName, finalValue);
                        } else {
                            vars.put(varName, finalValue);
                        }
                    }
                }
                sendValue = null;
            }

            done = true;
            result = null;
            return StepResult.done(result);
        }

        @Override
        public boolean isDone() { return done; }
        @Override
        public Object getResult() { return result; }
        @Override
        public void setSendValue(Object value) { this.sendValue = value; }
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
            variables.put(varName, value);
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
                ss.set(varName, value);
            } else {
                vars.put(varName, value);
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
            ((Map<String, Object>) target).put(String.valueOf(key), value);
        } else if (target instanceof List) {
            List<Object> list = (List<Object>) target;
            int index = ((Number) key).intValue();
            if (index < 0 || index >= list.size()) {
                throw createError("Array index out of bounds", ctx);
            }
            list.set(index, value);
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
        }
        return result;
    }

    // --- Loops ---

    @Override
    public Object visitConditionLoop(ProperTeeParser.ConditionLoopContext ctx) {
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
                        ss.set(valueVar, list.get(i));
                    } else {
                        vars.put(valueVar, list.get(i));
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
                        ss.set(valueVar, entry.getValue());
                    } else {
                        vars.put(valueVar, entry.getValue());
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
                        ss.set(valueVar, list.get(i));
                    } else {
                        vars.put(keyVar, keyVal);
                        vars.put(valueVar, list.get(i));
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
                        ss.set(valueVar, entry.getValue());
                    } else {
                        vars.put(keyVar, entry.getKey());
                        vars.put(valueVar, entry.getValue());
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

    // --- Function definition ---

    @Override
    public Object visitFunctionDef(ProperTeeParser.FunctionDefContext ctx) {
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
        return str.substring(1, str.length() - 1);
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
        if (ctx.ID() != null) return ctx.ID().getText();
        if (ctx.STRING() != null) {
            String str = ctx.STRING().getText();
            return str.substring(1, str.length() - 1);
        }
        if (ctx.INTEGER() != null) return ctx.INTEGER().getText();
        return null;
    }

    @Override
    public Object visitArrayAtom(ProperTeeParser.ArrayAtomContext ctx) {
        return eval(ctx.arrayLiteral());
    }

    @Override
    public Object visitArrayLiteral(ProperTeeParser.ArrayLiteralContext ctx) {
        List<Object> arr = new ArrayList<Object>();
        if (ctx.expression() == null) return arr;

        for (ProperTeeParser.ExpressionContext exprCtx : ctx.expression()) {
            arr.add(eval(exprCtx));
        }
        return arr;
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
            int index = ((Number) key).intValue();
            if (index < 0 || index >= list.size()) {
                if (ctx != null) throw createError("Array index out of bounds", ctx);
                throw new ProperTeeError("Runtime Error: Array index out of bounds");
            }
            return list.get(index);
        }
        if (target instanceof String) {
            // String character access
            String s = (String) target;
            int index = ((Number) key).intValue();
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
        int oneBased = Integer.parseInt(ctx.INTEGER().getText());
        return oneBased - 1; // Convert to 0-based
    }

    @Override
    public Object visitStringKeyAccess(ProperTeeParser.StringKeyAccessContext ctx) {
        String str = ctx.STRING().getText();
        return str.substring(1, str.length() - 1);
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
            if (TypeChecker.isString(left) && TypeChecker.isString(right)) {
                return (String) left + (String) right;
            }
            throw createError("Addition requires both operands to be numbers or both to be strings. Got " +
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

        // Evaluate arguments
        List<Object> args = new ArrayList<Object>();
        if (ctx.expression() != null) {
            for (ProperTeeParser.ExpressionContext exprCtx : ctx.expression()) {
                args.add(eval(exprCtx));
            }
        }

        // Built-in function
        if (builtins.has(funcName)) {
            Object result = builtins.get(funcName).call(args);

            // SLEEP returns a SchedulerCommand - propagate it up
            if (result instanceof SchedulerCommand) {
                return result;
            }
            return result;
        }

        // User-defined function
        if (userDefinedFunctions.containsKey(funcName)) {
            return callUserFunction(funcName, args, ctx);
        }

        throw createError("Unknown function '" + funcName + "'", ctx);
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
            localScope.put(params.get(i), i < args.size() ? args.get(i) : new LinkedHashMap<String, Object>());
        }

        // Push scope
        ss.push(localScope);

        try {
            for (ProperTeeParser.StatementContext stmt : funcDef.getBody().statement()) {
                eval(stmt);
            }

            // No explicit return: result is empty object
            return new LinkedHashMap<String, Object>();
        } catch (ReturnException e) {
            return e.getValue();
        } finally {
            ss.pop();
        }
    }

    // --- SPAWN statements ---

    @Override
    public Object visitSpawnAssignStmt(ProperTeeParser.SpawnAssignStmtContext ctx) {
        if (!inMultiSetup) {
            throw createError("thread can only be used inside multi blocks", ctx);
        }
        ProperTeeParser.FunctionCallContext funcCallCtx = ctx.functionCall();
        String funcName = funcCallCtx.funcName.getText();
        String varName = ctx.ID().getText();

        // Evaluate arguments now (during setup phase)
        List<Object> args = new ArrayList<Object>();
        if (funcCallCtx.expression() != null) {
            for (ProperTeeParser.ExpressionContext exprCtx : funcCallCtx.expression()) {
                args.add(eval(exprCtx));
            }
        }

        collectedSpawns.add(new SpawnSpec(funcName, args, varName, funcCallCtx));
        return null;
    }

    @Override
    public Object visitSpawnCallStmt(ProperTeeParser.SpawnCallStmtContext ctx) {
        if (!inMultiSetup) {
            throw createError("thread can only be used inside multi blocks", ctx);
        }
        ProperTeeParser.FunctionCallContext funcCallCtx = ctx.functionCall();
        String funcName = funcCallCtx.funcName.getText();

        // Evaluate arguments now (during setup phase)
        List<Object> args = new ArrayList<Object>();
        if (funcCallCtx.expression() != null) {
            for (ProperTeeParser.ExpressionContext exprCtx : funcCallCtx.expression()) {
                args.add(eval(exprCtx));
            }
        }

        collectedSpawns.add(new SpawnSpec(funcName, args, null, funcCallCtx));
        return null;
    }

    // --- Parallel / MULTI ---

    @Override
    @SuppressWarnings("unchecked")
    public Object visitParallelStmt(ProperTeeParser.ParallelStmtContext ctx) {
        Map<String, Object> vars = getVariables();

        // Snapshot globals for threads
        Map<String, Object> globalSnapshot = new LinkedHashMap<String, Object>(vars);

        // Setup phase: execute the block body, collecting SPAWN specs
        inMultiSetup = true;
        collectedSpawns = new ArrayList<SpawnSpec>();

        try {
            evalBlock(ctx.block());
        } finally {
            inMultiSetup = false;
        }

        // If no spawns were collected, just return (setup-only multi block)
        if (collectedSpawns.isEmpty()) {
            collectedSpawns = null;
            return null;
        }

        // Build thread specs from collected spawns
        List<String> resultVarNames = new ArrayList<String>();
        List<SchedulerCommand.ThreadSpec> specs = new ArrayList<SchedulerCommand.ThreadSpec>();

        for (int i = 0; i < collectedSpawns.size(); i++) {
            SpawnSpec spawn = collectedSpawns.get(i);
            resultVarNames.add(spawn.resultVarName);

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
                    localScope.put(params.get(j), j < spawn.args.size() ? spawn.args.get(j) : new LinkedHashMap<String, Object>());
                }

                Stepper threadStepper = new ThreadGeneratorStepper(this, funcDef, localScope);
                specs.add(new SchedulerCommand.ThreadSpec(spawn.funcName + "-" + i, threadStepper, localScope));

            } else if (builtins.has(spawn.funcName)) {
                // Built-in function: execute immediately and wrap result
                Object builtinResult = builtins.get(spawn.funcName).call(spawn.args);
                Stepper immediateStepper = new ImmediateStepper(builtinResult);
                specs.add(new SchedulerCommand.ThreadSpec("builtin-" + spawn.funcName + "-" + i, immediateStepper, null));
            } else {
                throw createError("Unknown function '" + spawn.funcName + "'", spawn.ctx);
            }
        }

        collectedSpawns = null;

        // Monitor spec
        SchedulerCommand.MonitorSpec monitorSpec = null;
        if (ctx.monitorClause() != null) {
            ProperTeeParser.MonitorClauseContext mc = ctx.monitorClause();
            int interval = Integer.parseInt(mc.INTEGER().getText());
            monitorSpec = new SchedulerCommand.MonitorSpec(interval, mc.block());
        }

        // Return the SPAWN_THREADS command (the scheduler will handle this)
        return SchedulerCommand.spawnThreads(specs, monitorSpec, globalSnapshot, resultVarNames);
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
