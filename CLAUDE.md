# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

ProperTee Java is a Java implementation of the [ProperTee](https://github.com/flatide/ProperTee) language. It uses ANTLR4 for parsing and a **Stepper interface pattern for cooperative multithreading** (replacing the JavaScript generator-based approach from [propertee-js](https://github.com/flatide/propertee-js)). Every statement visitor produces a Stepper object; a central scheduler round-robins between threads at statement boundaries.

## Build Commands

```bash
# Full build (generate parser + compile + test + JAR)
./gradlew clean build

# Individual steps:
./gradlew generateGrammarSource   # regenerate parser/lexer/visitor from grammar
./gradlew classes                 # compile Java sources
./gradlew jar                     # create fat JAR (build/libs/propertee-java.jar)
./gradlew test                    # run JUnit tests

# Dual-target fat JARs:
./gradlew jar7                    # Java 7 fat JAR → build/libs/propertee-java-java7.jar
./gradlew jar8                    # Java 8 fat JAR → build/libs/propertee-java-java8.jar
./gradlew jarAll                  # Both JARs

# Run a script via Gradle
./gradlew run --args="sample/01_hello.pt"
```

**After any grammar change** (`grammar/ProperTee.g4`), run `./gradlew generateGrammarSource` to regenerate parser files.

**Build requirements:** JDK 8+ for building (JDK 8 required to produce Java 7 bytecode via `-source 1.7 -target 1.7`). Gradle (wrapper included). Target runtime is **Java 7 (1.7)**.

## CLI Runner

`Main.java` runs `.pt` scripts from the command line. Falls back to an interactive REPL when no file is given.

```bash
java -jar build/libs/propertee-java.jar script.pt                          # run a script
java -jar build/libs/propertee-java.jar -p '{"width":100}' script.pt       # with built-in properties
java -jar build/libs/propertee-java.jar -f props.json script.pt            # properties from file
java -jar build/libs/propertee-java.jar --max-iterations 5000 script.pt    # custom loop limit
java -jar build/libs/propertee-java.jar                                    # interactive REPL
```

REPL commands: `.vars` (show variables), `.exit` (quit). Multi-line blocks are auto-detected via `do`/`if`/`multi` vs `end` depth.

## Testing

```bash
# Run all tests via JUnit (integrated with build)
./gradlew test

# Run a single test by name (parameterized test filter)
./gradlew test --tests "com.propertee.tests.ScriptTest.testScript[09_functions]"

# Run all tests via shell script (compares JAR output against .expected files)
./test_all.sh
```

There are 55 test pairs in `src/test/resources/tests/`. Each `NN_name.pt` file has a matching `.expected` file. Test 34 (`builtin_properties`) requires properties passed via `-p`. Test 41 (`result_pattern`) registers external functions via `registerExternal`. Test 46 (`thread_error_result`) verifies that thread errors are captured as `{ok: false, value: "..."}` Result objects. Test 47 (`spawn_outside_multi`) verifies `thread` outside multi block is a runtime error. Test 48 (`has_key`) verifies `HAS_KEY()` built-in function. Tests 49-54 cover multi result collection, dynamic spawn, auto keys, duplicate key error, LEN on maps, and map positional access. Test 55 (`thread_status_field`) verifies the `status` field on thread results. Test 56 (`monitor_reads_result`) verifies that monitor clauses can read thread result status during execution.

**Adding a new test:** Create `NN_name.pt` and `NN_name.expected` in `src/test/resources/tests/`, then add the test name string to the `testNames` array in `ScriptTest.java`. The test list is hardcoded — tests won't be discovered automatically.

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
- `StepResult.command(cmd)` = scheduler command (SLEEP, SPAWN_THREADS)
- `StepResult.done(value)` = stepper completed with result

### Package Structure

| Package | Role |
|---|---|
| `com.propertee.cli` | CLI entry point (`Main.java`) and interactive REPL (`Repl.java`) |
| `com.propertee.interpreter` | Core interpreter (`ProperTeeInterpreter.java` ~1540 lines), built-in functions, scope management, function definitions |
| `com.propertee.stepper` | Stepper interface, StepResult, SchedulerCommand — the cooperative multithreading API |
| `com.propertee.scheduler` | Round-robin scheduler, ThreadContext, ThreadState — manages thread lifecycle |
| `com.propertee.runtime` | Type checking, error types (ProperTeeError, BreakException, ContinueException, ReturnException), Result pattern |
| `com.propertee.parser` | ANTLR4-generated code (do not edit — regenerated from `grammar/ProperTee.g4`) |

### Key Files

| File | Role |
|---|---|
| `grammar/ProperTee.g4` | ANTLR4 grammar — defines all syntax. Semicolons are whitespace (part of WS rule). `thread` keyword for spawning in multi blocks. `multi resultVar do ... end` syntax with optional result collection. |
| `ProperTeeInterpreter.java` | Main visitor. All `visit*` methods plus inner Stepper classes (RootStepper, BlockStepper, FunctionCallStepper, ThreadGeneratorStepper). `thread` spawn visitors collect specs during multi setup. `eval()` for expressions, `createStepper()` for statements. Positional map access in `getProperty()`. |
| `BuiltinFunctions.java` | 24 built-in functions (PRINT, SUM, MAX, MIN, LEN, PUSH, SPLIT, JOIN, HAS_KEY, etc.). LEN supports strings, arrays, and objects. `registerExternal()` for I/O functions with result pattern. `PrintFunction` interface takes `Object[]` args, not `String` |
| `Scheduler.java` | Round-robin scheduler. Manages thread state, SLEEP timers, MULTI block spawning, monitor ticking |
| `ThreadContext.java` | Per-thread state: scope stack, global snapshot, sleep tracking, parent/child relationships |
| `TypeChecker.java` | Runtime type checks, number formatting, value formatting |
| `ScopeStack.java` | Scope chain with UNDEFINED sentinel |

### Multi Block Purity Model

Functions spawned inside multi blocks are pure with respect to global state:
- **Can read** globals via `::` (reads from a snapshot taken at `multi` block entry)
- **Cannot write** globals — `::x = value` is a runtime error (enforced via `inThreadContext` flag set by Scheduler)
- **Can call** any function (user-defined or built-in)
- **Can create** and modify local variables freely (plain `x` without `::`)
- **Return results** via `thread func() -> key` syntax as Result objects: `{status: "done", ok: true, value: <result>}` on success, `{status: "error", ok: false, value: "<error>"}` on error. Results are pre-built with `{status: "running", ok: false, value: {}}` at spawn time and updated in-place as threads complete. The monitor clause can read `resultVar.key.status` during execution. The collection is assigned to `resultVar` after all threads finish.
- No locks, no shared mutable state

### Scope Resolution

**At top level:** global variables → built-in properties.

**Inside functions (plain `x`):** local scopes (top of stack first) → multi result vars → error with hint to use `::x`.

**Inside functions (`::x`):** global variables/snapshot → built-in properties.

The `::` prefix (`GLOBAL_PREFIX` token) bypasses local scopes and accesses globals directly. At top level, `x` and `::x` are equivalent. The `activeThread` field on the interpreter routes scope access through the thread's local state when set by the scheduler.

### Flow Control

`BreakException`, `ContinueException`, and `ReturnException` propagate through stepper chains. Steppers catch these where appropriate (loops catch break/continue, function call steppers catch return).

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
multi result do
    thread worker("A") -> a
    thread worker("B") -> b
monitor 100
    PRINT("[tick]")
end
PRINT(result.a.value)   // named access
PRINT(result.1.value)   // positional access
LEN(result)             // 2

// Conditional/dynamic spawning in multi blocks
multi result do
    if needsA == true then
        thread workerA() -> rA
    end
    i = 1
    loop i <= 3 infinite do
        thread workerB(i)
        i = i + 1
    end
end

// Loops
loop condition infinite do ... end
loop item in collection do ... end
loop key, val in collection do ... end

// Access patterns: obj.prop, arr.1, obj."key", obj.$var, obj.$(expr)
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

## Conventions

- **No null** — the language has no null keyword. Functions without `return` or with bare `return` produce `{}` (empty object). Missing function arguments default to `{}`.
- **Java 7 target compatibility** — no lambdas, no streams, no Java 8 APIs. Use anonymous inner classes throughout. Build currently set to `-source 1.8 -target 1.8` for JDK 9+ compatibility; switch to `VERSION_1_7` when building with JDK 8.
- **Collections** — use `LinkedHashMap<String, Object>` for objects (preserves insertion order), `ArrayList<Object>` for lists. The `Object` type represents all values at runtime.
- `SLEEP()` returns a `SchedulerCommand` — the stepper yields it to the scheduler
- 1-based indexing for array access (`.1` is the first element)
- Strict type checking: no coercion, `and`/`or` require booleans, arithmetic requires numbers
- Numbers: `Integer` for whole numbers, `Double` for decimals. Format helper strips `.0`
- Division always produces `Double`
- Semicolons are optional statement separators (treated as whitespace by the lexer)

## Dependencies

- ANTLR 4.9.3 (parser generation and runtime — last version supporting Java 7 runtime)
- Gson 2.8.9 (JSON parsing for `-p`/`-f` properties — last version supporting Java 7)
- JUnit 4.13.2 (testing)
