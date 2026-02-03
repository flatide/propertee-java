# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

ProperTee Java is a Java port of the ProperTee Concurrent DSL interpreter. It uses ANTLR4 for parsing and a **Stepper interface pattern for cooperative multithreading** (replacing the JavaScript generator-based approach from the original `propertee-concurrent` project). Every statement visitor produces a Stepper object; a central scheduler round-robins between threads at statement boundaries.

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

**After any grammar change** (`src/main/antlr/com/propertee/parser/ProperTee.g4`), run `./gradlew generateGrammarSource` to regenerate parser files.

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

# Run all tests via shell script (compares JAR output against .expected files)
./test_all.sh
```

There are 40 test pairs in `src/test/resources/tests/`. Each `NN_name.pt` file has a matching `.expected` file. Test 34 (`builtin_properties`) requires properties passed via `-p`.

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

### Key Files

| File | Role |
|---|---|
| `ProperTee.g4` | ANTLR4 grammar — defines all syntax |
| `ProperTeeInterpreter.java` | Main visitor (~1500 lines). All `visit*` methods plus inner Stepper classes (RootStepper, BlockStepper, FunctionCallStepper, ThreadGeneratorStepper, ParallelStepper) |
| `BuiltinFunctions.java` | 23 built-in functions (PRINT, SUM, MAX, MIN, LEN, PUSH, SPLIT, JOIN, etc.) |
| `Scheduler.java` | Round-robin scheduler. Manages thread state, SLEEP timers, MULTI block spawning, monitor ticking |
| `ThreadContext.java` | Per-thread state: scope stack, global snapshot, sleep tracking, parent/child relationships |
| `ThreadState.java` | Enum: READY, RUNNING, SLEEPING, WAITING, COMPLETED, ERROR |
| `Stepper.java` | Core interface |
| `StepResult.java` | Step result types: BOUNDARY, COMMAND, DONE |
| `SchedulerCommand.java` | Commands from interpreter to scheduler: SLEEP, SPAWN_THREADS |
| `TypeChecker.java` | Runtime type checks, number formatting, value formatting |
| `ScopeStack.java` | Scope chain with UNDEFINED sentinel |
| `Main.java` | CLI entry point with arg parsing |
| `Repl.java` | Interactive REPL with multi-line block detection |

### Thread Purity Model

Thread functions are pure with respect to global state:
- **Can read** globals via a snapshot taken at MULTI block entry
- **Cannot write** globals (enforced at runtime)
- **Can only call** other thread functions or built-in functions
- **Return results** via `->` syntax in MULTI blocks; results assigned only after ALL threads complete

### Scope Resolution

Variable lookup order: local scopes (top of stack first) → multi result vars → global variables/snapshot → built-in properties. The `activeThread` field on the interpreter routes scope access through the thread's local state when set by the scheduler.

### Flow Control

`BreakException`, `ContinueException`, and `ReturnException` propagate through stepper chains. Steppers catch these where appropriate (loops catch break/continue, function call steppers catch return).

## Language Quick Reference

```
// Variables
x = 10

// Functions
function add(a, b) do return a + b end

// Thread functions (callable from multi blocks)
thread worker(name) do
    PRINT(name + " working")
    return 42
end

// Parallel execution
multi
    worker("A") -> resultA
    worker("B") -> resultB
monitor 100
    PRINT("[tick]")
end

// Loops
loop condition infinite do ... end
loop item in collection do ... end
loop key, val in collection do ... end

// Access patterns: obj.prop, arr.1, obj."key", obj.$var, obj.$(expr)
```

## Conventions

- Java 7 target compatibility (no lambdas, no streams, no Java 8 APIs — anonymous inner classes throughout). Build currently set to `-source 1.8 -target 1.8` for JDK 9+ compatibility; switch to `VERSION_1_7` when building with JDK 8.
- `SLEEP()` returns a `SchedulerCommand` — the stepper yields it to the scheduler
- 1-based indexing for array access (`.1` is the first element)
- Strict type checking: no coercion, `and`/`or` require booleans, arithmetic requires numbers
- Numbers: `Integer` for whole numbers, `Double` for decimals. Format helper strips `.0`
- Division always produces `Double`

## Dependencies

- ANTLR 4.9.3 (parser generation and runtime — last version supporting Java 7 runtime)
- Gson 2.8.9 (JSON parsing for `-p`/`-f` properties — last version supporting Java 7)
- JUnit 4.13.2 (testing)
