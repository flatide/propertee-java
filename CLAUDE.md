# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is (v0.4.0)

ProperTee Java is a Java implementation of the [ProperTee](https://github.com/flatide/ProperTee) language. It uses ANTLR4 for parsing and a **Stepper interface pattern for cooperative multithreading** (replacing the JavaScript generator-based approach from [propertee-js](https://github.com/flatide/propertee-js)). Every statement visitor produces a Stepper object; a central scheduler round-robins between threads at statement boundaries.

## Project Structure

Multi-module Gradle project with three subprojects:

| Module | Contents | Depends On |
|---|---|---|
| `propertee-core` | Language runtime: interpreter, scheduler, stepper, builtins, TaskRunner, platform provider, ANTLR grammar | — |
| `propertee-cli` | CLI runner (`Main.java`) and interactive REPL (`Repl.java`) | `propertee-core` |

Source layout: `propertee-{module}/src/main/java/com/propertee/{package}/`. Grammar at `propertee-core/grammar/ProperTee.g4`. Tests at `propertee-core/src/test/`.

## Build Commands

```bash
# Full build (generate parser + compile + test)
./gradlew clean build

# Individual steps:
./gradlew generateGrammarSource   # regenerate parser/lexer/visitor from grammar
./gradlew classes                 # compile all Java sources
./gradlew test                    # run JUnit tests (ScriptTest + TaskEngineTest)

# CLI fat JARs:
./gradlew jar7                    # Java 7 fat JAR → build/libs/propertee-java-java7.jar
./gradlew jar8                    # Java 8 fat JAR → build/libs/propertee-java-java8.jar
./gradlew jarAll                  # Both JARs

# Run a script via Gradle:
./gradlew :propertee-cli:run --args="sample/01_hello.pt"
```

**After any grammar change** (`propertee-core/grammar/ProperTee.g4`), run `./gradlew generateGrammarSource` to regenerate parser files.

**Build requirements:** JDK 8+ for building (JDK 8 required to produce Java 7 bytecode via `-source 1.7 -target 1.7`). Gradle (wrapper included). Target runtime is **Java 7 (1.7)**.

## CLI Runner

`Main.java` runs `.pt` scripts from the command line. Falls back to an interactive REPL when no file is given.

```bash
java -jar build/libs/propertee-java-java8.jar script.pt                    # run a script
java -jar build/libs/propertee-java-java8.jar -p '{"width":100}' script.pt # with built-in properties
java -jar build/libs/propertee-java-java8.jar -f props.json script.pt      # properties from file
java -jar build/libs/propertee-java-java8.jar --max-iterations 5000 script.pt
java -jar build/libs/propertee-java-java8.jar                              # interactive REPL
```

REPL commands: `.vars` (show variables), `.exit` (quit). Multi-line blocks are auto-detected via `do`/`if` vs `end` depth.

## Testing

```bash
# Run all tests via JUnit (integrated with build)
./gradlew test

# Run a single script test by name (parameterized test filter)
./gradlew :propertee-core:test --tests "com.propertee.tests.ScriptTest.testScript[09_functions]"

# Run only TaskEngine tests
./gradlew :propertee-core:test --tests "com.propertee.tests.TaskEngineTest"

# Run all tests via shell script (compares JAR output against .expected files)
./test_all.sh
```

**Script tests:** 85 test pairs in `propertee-core/src/test/resources/tests/` (numbered 01-85, test 31 skipped). Each `NN_name.pt` file has a matching `.expected` file. Notable special cases: test 34 requires `-p` properties; test 41 uses `registerExternal`; test 71 uses `registerExternalAsync`; test 72 uses `SHELL()`; tests 73-74 test keyword/function ignore; tests 75-77 test range edge cases; tests 78-80 test `START_TASK`/`WAIT_TASK`/`CANCEL_TASK`; tests 81-85 test new builtins (string matching, map extensions, type/env, JSON, file I/O). Test 83 and 85 require DefaultPlatformProvider injection.

**Adding a new test:** Create `NN_name.pt` and `NN_name.expected` in `propertee-core/src/test/resources/tests/`, then add the test name string to the `testNames` array in `ScriptTest.java`. The test list is hardcoded — tests won't be discovered automatically.

**TaskRunner tests:** `TaskEngineTest.java` tests DefaultTaskRunner process lifecycle and kill/cancel behavior. These spawn real shell processes. Multi-instance and archival tests have been moved to TeeBox's ManagedTaskEngineTest.

**Sample scripts:** `sample/01_hello.pt` through `sample/16_comments.pt` cover all language features.

## Architecture

### Execution Flow

```
Script text → ProperTeeLexer → ProperTeeParser → Parse Tree
                                                      ↓
                                          ProperTeeInterpreter.visit*(tree)
                                                      ↓
                                              Stepper objects (state machines)
                                                      ↓
                                              Scheduler.run(mainStepper)
                                                      ↓
                                         Round-robin stepper.step() loop
```

### Stepper Pattern (replaces JS generators)

The `Stepper` interface replaces JavaScript's `function*`/`yield`/`yield*`:

```java
interface Stepper {
    StepResult step();           // advance one step
    boolean isDone();            // completed?
    Object getResult();          // final value
    void setSendValue(Object v); // scheduler sends data back (MULTI results)
}
```

- **Statement visitors** return multi-step Steppers (`BlockStepper`, `RootStepper`, `FunctionCallStepper`, etc.) that yield `StepResult.BOUNDARY` between statements.
- **Expression visitors** evaluate eagerly via `eval()` — expressions are atomic, never yielding mid-evaluation.
- `StepResult.boundary()` = statement boundary (thread stays READY)
- `StepResult.command(cmd)` = scheduler command (SLEEP, SPAWN_THREADS, AWAIT_ASYNC)
- `StepResult.done(value)` = stepper completed with result

### Package Structure

**Core packages** (in `propertee-core`):

| Package | Role |
|---|---|
| `com.propertee.interpreter` | Core interpreter (`ProperTeeInterpreter.java` ~1800 lines), built-in functions, scope management, function definitions |
| `com.propertee.stepper` | Stepper interface, StepResult, SchedulerCommand — the cooperative multithreading API |
| `com.propertee.scheduler` | Round-robin scheduler, ThreadContext, ThreadState — manages thread lifecycle |
| `com.propertee.runtime` | Type checking, error types (ProperTeeError, BreakException, ContinueException, ReturnException), Result pattern |
| `com.propertee.task` | TaskRunner interface, DefaultTaskRunner (lightweight in-memory), deprecated TaskEngine. Task/TaskInfo/TaskObservation models, TaskStatus enum |
| `com.propertee.platform` | PlatformProvider interface for host-gated OS access (file I/O, ENV). DefaultPlatformProvider (unrestricted), UnsupportedPlatformProvider (rejects all) |
| `com.propertee.parser` | ANTLR4-generated code (do not edit — regenerated from `propertee-core/grammar/ProperTee.g4`) |

**Application packages:**

| Package | Module | Role |
|---|---|---|
| `com.propertee.cli` | `propertee-cli` | CLI entry point (`Main.java`) and interactive REPL (`Repl.java`) |

### Key Files

| File | Role |
|---|---|
| `propertee-core/grammar/ProperTee.g4` | ANTLR4 grammar — defines all syntax. Semicolons are whitespace (part of WS rule). `thread` keyword for spawning in multi blocks. `multi resultVar do ... end` syntax with optional result collection. Thread spawn keys reuse the `access` rule (same as property access): `thread key:`, `thread "key":`, `thread 42:`, `thread $var:`, `thread $::var:`, `thread $(expr):`, `thread :` (unnamed). `arrayLiteral` has two alternatives: `RangeArray` (`[start..end]` or `[start..end, step]`) and `ListArray` (`[1, 2, 3]`). Object keys must be quoted strings or integers — bare identifiers are not allowed (`{"name": "Alice"}`, not `{name: "Alice"}`). |
| `ProperTeeInterpreter.java` | Main visitor. All `visit*` methods plus inner Stepper classes (RootStepper, BlockStepper, FunctionCallStepper, ThreadGeneratorStepper). `visitSpawnKeyStmt` resolves key from `access` context (StaticAccess, StringKeyAccess, ArrayAccess, VarEvalAccess, EvalAccess). `visitParallelStmt` resolves auto-keys (`#1`, `#2`) for unnamed threads and detects collisions with explicit keys before passing to scheduler. `eval()` for expressions, `createStepper()` for statements. Integer keys on objects become string keys in `getProperty()`. `resolveAndValidateDynamicKey()` auto-coerces dynamic keys to string via `TO_STRING()` (empty treated as unnamed, no duplicates). |
| `BuiltinFunctions.java` | 64 built-in functions (PRINT, SUM, MAX, MIN, LEN, PUSH, SPLIT, JOIN, HAS_KEY, KEYS, SORT, SORT_DESC, SORT_BY, SORT_BY_DESC, REVERSE, RANDOM, MILTIME, DATE, TIME, SHELL, SHELL_CTX, START_TASK, TASK_STATUS, TASK_RESULT, WAIT_TASK, CANCEL_TASK, etc.). Internal registration uses private `registerResult()`/`registerResultAsync()`; host injection uses public `registerExternal()`/`registerExternalAsync()`. LEN supports strings, arrays, and objects. `registerExternal()` for sync I/O, `registerExternalAsync()` for async I/O (blocking calls on thread pool). `SHELL` and task functions use `TaskRunner` interface (was `TaskEngine`, now lightweight). `SHELL_CTX(cwd[, env])` creates a context config (sync via `registerExternal`). `SHELL(cmd)` or `SHELL(ctx, cmd)` executes shell commands (async via `registerExternalAsync`). `SHELL` auto-unwraps Result from `SHELL_CTX` — pass the Result directly, not `.value`. `PrintFunction` interface takes `Object[]` args, not `String` |
| `PlatformProvider.java` | Interface for host-gated OS capabilities (ENV, file I/O). `DefaultPlatformProvider` provides unrestricted access; `UnsupportedPlatformProvider` rejects all calls. Hosts implement this to apply path restrictions, read-only policies, etc. |
| `Scheduler.java` | Round-robin scheduler. Manages thread state, SLEEP timers, MULTI block spawning. Pre-builds result collection with `Result.running()` at spawn time (all keys pre-resolved by interpreter, including auto-keys `"#1"`, `"#2"` for unnamed threads), updates entries in-place as threads complete, injects result collection into monitor scope for live status reads |
| `ThreadContext.java` | Per-thread state: scope stack, global snapshot, sleep tracking, parent/child relationships, `resultCollection` (live map updated in-place by scheduler) |
| `TypeChecker.java` | Runtime type checks, number formatting, value formatting |
| `ScopeStack.java` | Scope chain with UNDEFINED sentinel |

### Multi Block Purity Model

**Setup phase scope:** The multi block body (setup phase) runs in an isolated scope — a scope is pushed before setup and popped after. Variables created during setup don't leak into the surrounding scope. The `::` prefix is required to access globals, same as inside functions. `$::var` syntax accesses globals directly in dynamic keys (equivalent to `$(::var)`).

**Spawned thread purity:** Functions spawned inside multi blocks are pure with respect to global state:
- **Can read** globals via `::` (reads from a snapshot taken at `multi` block entry)
- **Cannot write** globals — `::x = value` is a runtime error (enforced via `inThreadContext` flag set by Scheduler)
- **Can call** any function (user-defined or built-in)
- **Can create** and modify local variables freely (plain `x` without `::`)
- **Return results** via `thread key: func()` syntax as Result objects: `{status: "done", ok: true, value: <result>}` on success, `{status: "error", ok: false, value: "<error>"}` on error. Results are pre-built with `{status: "running", ok: false, value: {}}` at spawn time and updated in-place as threads complete. The monitor clause can read `resultVar.key.status` during execution. The collection is assigned to `resultVar` after all threads finish.
- No locks, no shared mutable state

### Scope Resolution

**At top level:** global variables → built-in properties.

**Inside functions and multi setup (plain `x`):** local scopes (top of stack first) → multi result vars → error with hint to use `::x`.

**Inside functions and multi setup (`::x`):** global variables/snapshot → built-in properties.

The `::` prefix (`GLOBAL_PREFIX` token) bypasses local scopes and accesses globals directly. At top level (outside functions and multi setup), `x` and `::x` are equivalent. The `activeThread` field on the interpreter routes scope access through the thread's local state when set by the scheduler.

### Scheduler (`Scheduler.java`)

The scheduler drives all execution — even single-threaded scripts run through it. Key mechanics:

**Instance state**: `threads` (LinkedHashMap<Integer, ThreadContext>), `monitors` (ArrayList<MonitorState>), `nextThreadId` (counter), `currentThreadId` (for round-robin).

**MonitorState** (inner class): `interval` (ms), `blockCtx`, `lastRun` (timestamp), `parentThreadId`, `childIds`.

**Main loop** (`run()`):
1. Creates thread 0 (main) with the root stepper
2. Loop: `wakeThreads()` → `pollAsyncFutures()` → `runMonitors()` → `selectNextThread()` → `step()` → `processStepResult()`
3. When no READY threads exist: sleep-polls (capped at 50ms) if SLEEPING threads remain, busy-waits (1ms) if WAITING or BLOCKED threads remain, otherwise exits
4. Thread 0 errors propagate as exceptions; child thread errors go to stderr as `[THREAD ERROR]`

**Thread selection** (`selectNextThread()`): Round-robin by sorted thread ID, starting after `currentThreadId`. Only picks READY threads.

**Step result handling** (`processStepResult()`):
- `BOUNDARY` → mark thread READY (yield point for scheduling)
- `COMMAND(SLEEP)` → mark thread SLEEPING with wake time
- `COMMAND(SPAWN_THREADS)` → call `handleSpawnThreads()` (creates child threads, parent goes WAITING)
- `COMMAND(AWAIT_ASYNC)` → mark thread BLOCKED (waiting for async I/O)
- `DONE` → mark COMPLETED, notify parent via `notifyChildCompleted()`

**Async polling** (`pollAsyncFutures()`): Each iteration checks BLOCKED threads. If `Future.isDone()`, caches result in `asyncResultCache` and marks READY. If timeout exceeded (`asyncTimeoutMs > 0` and elapsed > timeout), cancels future, caches `Result.error("timeout")`, and marks READY.

**Thread spawning** (`handleSpawnThreads()`):
1. Creates child ThreadContexts from specs, each with `inThreadContext = true`
2. Sets up monitor if present (stores interval, block ctx, child IDs in MonitorState, `lastRun` initialized to current time)
3. Marks parent WAITING with child ID set
4. Pre-builds `resultCollection` on parent with `Result.running()` entries (all keys pre-resolved by interpreter, including auto-keys `"#1"`, `"#2"` for unnamed threads)

**Child completion** (`notifyChildCompleted()`):
1. Updates parent's `resultCollection` in-place — `Result.ok()` or `Result.error()`
2. Removes child from parent's `waitingForChildren` set
3. When all children done: runs final monitor tick, removes monitor, sends `{resultVarName, collection}` payload to parent via `collectedResults`
4. Parent wakes to READY; scheduler sends payload via `stepper.setSendValue()`

**Monitor execution** (`runMonitors()` + `executeMonitorSync()`): Each scheduler iteration checks all monitors — fires when `(now - lastRun >= interval)`, updates `lastRun`. `executeMonitorSync()` creates a temporary ThreadContext (id -1, name "monitor") with `inMonitorContext = true`. Copies global snapshot and injects the live `resultCollection` under `resultCollectionVarName`. Runs the monitor block synchronously via `visitor.evalBlock()`. Monitor errors go to stderr as `[MONITOR ERROR]`, not thrown. `runFinalMonitor()` runs one last tick when all children complete, then removes the monitor.

**Interpreter integration**: `visitor.activeThread` is set to the current thread before each step. The interpreter's `getScopeStack()`, `getVariables()`, `isInFunctionScope()` etc. all check `activeThread` to route scope access through the thread's local state.

### ThreadContext (`ThreadContext.java`)

Per-thread state container. Constructor takes `(int id, String name, Stepper stepper, Map<String, Object> globalSnapshot)`. All fields are public (scheduler accesses them directly).

| Field | Type | Default | Purpose |
|---|---|---|---|
| `id` | `int` | (ctor) | Unique thread ID (0 = main, -1 = monitor) |
| `name` | `String` | (ctor) | Debug name (e.g. `"worker-0"`, `"main"`, `"monitor"`) |
| `stepper` | `Stepper` | (ctor) | The stepper driving this thread's execution |
| `state` | `ThreadState` | `READY` | `READY → RUNNING → READY → ... → COMPLETED/ERROR` |
| `scopeStack` | `ScopeStack` | `new ScopeStack()` | Thread-private local variable scopes |
| `globalSnapshot` | `Map<String, Object>` | (ctor) | Read-only globals for thread purity (main thread uses live `variables`) |
| `sleepUntil` | `Long` | `null` | Absolute wake time (ms) when SLEEPING |
| `inThreadContext` | `boolean` | `false` | True for child threads — blocks `::x = val` writes |
| `inMonitorContext` | `boolean` | `false` | True for monitor execution — blocks all assignments |
| `inMultiContext` | `boolean` | `false` | True during multi setup — blocks result var access |
| `multiResultVars` | `Map<String, Object>` | `{}` | Result variables from completed multi blocks (accessible in later code) |
| `result` | `Object` | `null` | Final return value when COMPLETED |
| `error` | `Throwable` | `null` | Exception when ERROR |
| `parentId` | `Integer` | `null` | Parent thread ID (null for main) |
| `waitingForChildren` | `Set<Integer>` | `null` | Child IDs still running (null when not WAITING) |
| `resultCollection` | `Map<String, Object>` | `null` | Live result map updated in-place as children complete |
| `childIds` | `List<Integer>` | `null` | Ordered child thread IDs for this multi block |
| `resultKeyNames` | `List<String>` | `null` | Parallel list of key names (all pre-resolved by interpreter, including auto-keys `"#N"`) |
| `resultCollectionVarName` | `String` | `null` | The `resultVar` name from `multi resultVar do` |
| `collectedResults` | `Object` | `null` | Payload sent to parent stepper via `setSendValue()` when all children done |
| `resultKeyName` | `String` | `null` | This child's key in parent's collection |
| `localScope` | `Map<String, Object>` | `null` | Function parameters for spawned thread |
| `asyncResultCache` | `Map<String, Object>` | `{}` | Cached results from completed async operations |
| `asyncFuture` | `Future<Object>` | `null` | The pending Future for current async operation |
| `asyncCacheKey` | `String` | `null` | Cache key for current pending async operation |
| `asyncTimeoutMs` | `long` | `0` | Timeout for current async operation (0 = no timeout) |
| `asyncSubmitTime` | `long` | `0` | Timestamp when async operation was submitted |

Methods: `markRunning()`, `markReady()`, `markSleeping(long)`, `markBlocked()`, `markWaiting(List<Integer>)`, `markCompleted(Object)`, `markError(Throwable)`, `shouldWake(long)`, `childCompleted(int)`, `clearAsyncState()`.

**ThreadState transitions:**
```
READY → RUNNING → READY           (normal step: boundary)
READY → RUNNING → SLEEPING        (SLEEP command)
READY → RUNNING → WAITING         (SPAWN_THREADS command, waiting for children)
READY → RUNNING → BLOCKED         (AWAIT_ASYNC command, waiting for async I/O)
READY → RUNNING → COMPLETED       (stepper done)
READY → RUNNING → ERROR           (exception thrown)
WAITING → READY                    (all children completed)
SLEEPING → READY                   (sleep timer expired)
BLOCKED → READY                    (async future completed or timed out)
```

### SchedulerCommand & StepResult

**StepResult** — returned by `Stepper.step()`:
- `StepResult.BOUNDARY` — statement boundary, thread yields for round-robin
- `StepResult.done(value)` — stepper completed with final value
- `StepResult.command(cmd)` — scheduler command requiring action

**SchedulerCommand** — three types:
- `SchedulerCommand.sleep(durationMs)` — pause current thread
- `SchedulerCommand.spawnThreads(specs, monitorSpec, globalSnapshot, resultKeyNames, resultVarName)` — spawn child threads for multi block
- `SchedulerCommand.awaitAsync()` — block thread until async external function completes

**SchedulerCommand.ThreadSpec** — per-thread spawn data: `{name, stepper, localScope}`

**SchedulerCommand.MonitorSpec** — monitor config: `{interval, blockCtx}`

### Flow Control

`BreakException`, `ContinueException`, `ReturnException`, and `AsyncPendingException` propagate through stepper chains. Steppers catch these where appropriate: loops catch break/continue, function call steppers catch return, statement-level steppers (RootStepper, BlockStepper, FunctionCallStepper, ThreadGeneratorStepper) catch `AsyncPendingException` and return `AWAIT_ASYNC` command for retry.

## TaskRunner (`com.propertee.task`)

Manages detached shell processes for SHELL()/START_TASK() builtins.

| Class | Role |
|---|---|
| `TaskRunner` | Interface: execute, getTask, waitForCompletion, killTask, observe, stdout/stderr/output, exitCode, statusMap, shutdown. Defines the eval/runtime contract for process execution. |
| `DefaultTaskRunner` | Lightweight implementation: in-memory task tracking (ConcurrentHashMap), no disk persistence, no indexing, no archival. Used by CLI and as the default for embedders. |
| `TaskEngine` | **Deprecated.** Original implementation with full persistence/indexing/archival. Now implements TaskRunner for backward compat. Will be removed in next major version. |
| `TaskStatus` | Enum: STARTING, RUNNING, COMPLETED, FAILED, KILLED, DETACHED, LOST. `@SerializedName` for lowercase JSON compat. `isTransient()` for states that need recheck. |
| `Task` | Model: command, pid, pgid, status, exitCode, timeoutMs, hostInstanceId, archived flag, stdoutTail/stderrTail. |
| `TaskInfo` | DTO with computed fields: elapsedMs, timeoutExceeded, healthHints. |
| `TaskObservation` | Snapshot from `observe()`: alive, elapsed, output timestamps, health hints. |

**Note:** Process management (persistence, multi-instance ownership, archival, restart recovery) has been moved to the separate [TeeBox](https://github.com/flatide/TeeBox) repository as `ManagedTaskEngine`. Core only provides lightweight process execution.

### Structured Results Contract

Scripts can return results via two mechanisms:
1. **Explicit return** — `return expr` in script → `hasExplicitReturn=true`, `resultData=returnValue`
2. **`result` variable fallback** — if no return, checks `variables.get("result")` → `hasExplicitReturn=false`, `resultData=resultVar`
3. **Neither** — `resultData=null`

`RootStepper` (public inner class of `ProperTeeInterpreter`) tracks `hasExplicitReturn` via `ReturnException` catch.

## Language Quick Reference

```
// Variables
x = 10

// Global access inside functions (:: prefix)
function readX() do return ::x end
function setX(v) do ::x = v end

// Functions
function add(a, b) do return a + b end

// Any function can run in multi blocks via thread keyword
function worker(name) do
    PRINT(name + " working")
    return 42
end

// Parallel execution — results collected into result object
// Each entry is {status: "done"/"error"/"running", ok: true/false, value: ...}
multi result do
    thread a: worker("A")
    thread b: worker("B")
monitor 100
    PRINT(result.a.status)   // "running" or "done" — monitor reads live status
end
PRINT(result.a.value)   // named access
LEN(result)             // 2

// Dynamic thread keys — $var, $::var, and $(expr) syntax
names = ["alpha", "beta"]
multi result do
    loop name in ::names do
        thread $name: worker(name)             // key from variable
    end
    thread $("gamma"): worker("C")             // key from expression
end

// Conditional/dynamic spawning in multi blocks
// Setup runs in isolated scope — :: required for globals
multi result do
    if ::needsA == true then
        thread rA: workerA()
    end
    i = 1
    loop i <= 3 infinite do
        thread : workerB(i)
        i = i + 1
    end
end

// Range arrays
nums = [1..5]              // [1, 2, 3, 4, 5]
odds = [1..10, 2]          // [1, 3, 5, 7, 9]
down = [5..1]              // [5, 4, 3, 2, 1] (auto step -1)

// Loops
loop condition infinite do ... end
loop item in collection do ... end
loop key, val in collection do ... end
loop x in [1..10] do ... end  // range in loop

// Access patterns: obj.prop, arr.1, obj."key", obj.$var, obj.$::var, obj.$(expr)

// Shell commands
result = SHELL("echo hello")             // one-off
ctx = SHELL_CTX("/data", {"ENV": "prod"})
result = SHELL(ctx, "./build.sh")        // with context (auto-unwraps Result)

// Task engine — detached external processes
taskId = START_TASK("long-job.sh")       // returns task ID string
status = TASK_STATUS(taskId)             // observation map
result = WAIT_TASK(taskId, 5000)         // wait with timeout
result = TASK_RESULT(taskId)             // get completed result
CANCEL_TASK(taskId)                      // kill task and descendants
```

## External Functions & Result Pattern

Host applications can register external built-in functions that return result objects instead of throwing errors:

```java
// Java host registers an external function:
interpreter.builtins.registerExternal("GET_BALANCE", new BuiltinFunction() {
    public Object call(List<Object> args) {
        String user = (String) args.get(0);
        if (userExists(user)) return Result.ok(getBalance(user));
        return Result.error("account not found");
    }
});
```

```
// ProperTee script checks the result:
res = GET_BALANCE("alice")
if res.ok == true then
    PRINT("Balance:", res.value)
else
    PRINT("Error:", res.value)
end
```

- `Result.running()` → `{status: "running", ok: false, value: {}}`
- `Result.ok(value)` → `{status: "done", ok: true, value: ...}`
- `Result.error(message)` → `{status: "error", ok: false, value: "..."}`
- `registerExternal()` wraps the function in try-catch — thrown exceptions automatically become `{status: "error", ok: false, value: "error message"}`
- Core builtins (PRINT, SUM, LEN, etc.) return values directly and are not wrapped

### Async External Functions

`registerExternalAsync(name, func)` and `registerExternalAsync(name, func, timeoutMs)` register functions that execute on a background thread pool. Used for blocking I/O (DB queries, HTTP calls) so other ProperTee threads aren't frozen.

**Mechanism — statement re-execution with cached results:**
1. Async wrapper submits function to `ExecutorService` thread pool, stores `Future` on `ThreadContext`
2. Throws `AsyncPendingException` to unwind expression evaluation stack
3. Stepper catches it, does **not** advance statement index, returns `StepResult.command(awaitAsync())`
4. Scheduler marks thread `BLOCKED` (new `ThreadState`), polls `Future.isDone()` each iteration
5. When future completes, caches result on ThreadContext under `asyncResultCache`, marks thread `READY`
6. Stepper re-executes the **same statement** — async wrapper finds cached result, returns it immediately
7. Statement completes normally (assignment happens, etc.)
8. After successful statement execution, `asyncResultCache` is cleared

**Key files:**
- `AsyncPendingException.java` — control flow exception (not a real error)
- `BuiltinFunctions.java` — `registerExternalAsync()`, executor management, cache key logic (`funcName + "|" + formatValue(args)`)
- `ThreadContext.java` — `asyncResultCache`, `asyncFuture`, `asyncCacheKey`, `asyncTimeoutMs`, `asyncSubmitTime`, `markBlocked()`, `clearAsyncState()`
- `ThreadState.java` — `BLOCKED` state (between `WAITING` and `COMPLETED`)
- `SchedulerCommand.java` — `AWAIT_ASYNC` command type + `awaitAsync()` factory
- `Scheduler.java` — `pollAsyncFutures()` method, `AWAIT_ASYNC` case in `processStepResult`, BLOCKED check in no-READY-threads block
- All four stepper classes in `ProperTeeInterpreter.java` — catch `AsyncPendingException`, clear cache on success

**Limitations:** Side-effect replay on statement retry; sequential multi-async (not parallel); not allowed in monitors.

### Keyword Hide & Function Ignore Lists

Host applications can restrict language features via `setHiddenKeywords()` and `setIgnoredFunctions()`:

```java
// Hide language keywords (if, loop, function, multi, thread, debug)
Set<String> hidden = new HashSet<String>();
hidden.add("multi");
hidden.add("loop");
visitor.setHiddenKeywords(hidden);

// Ignore built-in/external functions
Set<String> ignored = new HashSet<String>();
ignored.add("SHELL");
visitor.setIgnoredFunctions(ignored);
```

- Hidden keywords produce `'keyword' is not available in this environment` runtime error when the corresponding visitor is entered
- Ignored functions produce `'FUNC' is not available in this environment` runtime error at call site (both in normal calls and multi block spawn dispatch)
- Checked keywords: `if`, `loop` (all 3 loop forms), `function` (definition only, not calling), `multi`, `thread`, `debug`

## Conventions

- **No null** — the language has no null keyword. Functions without `return` or with bare `return` produce `{}` (empty object). Missing function arguments default to `{}`.
- **String escapes** — `\"`, `\\`, `\n`, `\t`, `\r` are processed in all string contexts (literals, object keys, property access). Unrecognized escapes like `\d` are preserved as-is.
- **Java 7 target compatibility** — no lambdas, no streams, no Java 8 APIs. Use anonymous inner classes throughout. Build currently set to `-source 1.8 -target 1.8` for JDK 9+ compatibility; switch to `VERSION_1_7` when building with JDK 8.
- **Collections** — use `LinkedHashMap<String, Object>` for objects (preserves insertion order), `ArrayList<Object>` for lists. The `Object` type represents all values at runtime.
- `SLEEP()` returns a `SchedulerCommand` — the stepper yields it to the scheduler
- 1-based indexing for array/string access (`.1` is the first element). `visitArrayAccess` returns the 1-based integer; consumers (`getProperty`, `setProperty`) convert to 0-based for arrays and strings. For objects, the integer becomes the string key directly (`obj.1` reads/writes key `"1"`)
- Strict type checking: `and`/`or` require booleans, arithmetic requires numbers. Exception: `+` with at least one string coerces the other operand via `TO_STRING()` (concatenation)
- Numbers: `Integer` for whole numbers, `Double` for decimals. Format helper strips `.0`
- Division always produces `Double`
- Semicolons are optional statement separators (treated as whitespace by the lexer)
- **Syntax highlighting** — ProperTee repo has Vim (`editors/vim/syntax/propertee.vim`) and VS Code (`editors/vscode/syntaxes/propertee.tmLanguage.json`) syntax files. The playground (`propertee-js/docs/index.html`) has its own regex-based syntax highlighting via `highlightSyntax()` — update the `builtins` and `keywords` strings there when adding new built-in functions or keywords. Update all three locations when adding keywords or built-in functions.
- **DefaultTaskRunner** — lightweight in-memory task tracking, no disk persistence. The deprecated `TaskEngine` retains index-based persistence for backward compat.

## CI / Releases

GitHub Actions workflow (`.github/workflows/release-artifacts.yml`) publishes build artifacts on tag push (e.g., `git tag v0.4.0 && git push origin v0.4.0`). Published assets: `propertee-java-java7.jar`, `propertee-java-java8.jar`.

## Dependencies

- ANTLR 4.9.3 (parser generation and runtime — last version supporting Java 7 runtime)
- Gson 2.8.9 (JSON parsing for `-p`/`-f` properties — last version supporting Java 7)
- JUnit 4.13.2 (testing)
