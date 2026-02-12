# ProperTee for Java v0.3.0

A Java implementation of the [ProperTee](https://github.com/flatide/ProperTee) language using ANTLR4 for parsing and a **Stepper interface pattern for cooperative multithreading** with round-robin scheduling.

For language specification, syntax reference, and built-in functions, see the [ProperTee Language Home](https://github.com/flatide/ProperTee). For detailed runtime semantics (types, scoping, operators, threading), see [LANGUAGE.md](LANGUAGE.md). See the [Changelog](LANGUAGE.md#changelog) for what's new.

**Runtime compatibility:** Java 7+ (no lambdas, no streams, no Java 8 APIs)

## Quick Start

```bash
# Build
./gradlew clean build

# Run a script
java -jar build/libs/propertee-java-java8.jar script.pt

# With built-in properties
java -jar build/libs/propertee-java-java8.jar -p '{"width":100}' script.pt

# Interactive REPL
java -jar build/libs/propertee-java-java8.jar
```

## Building

```bash
./gradlew jar7      # Java 7 fat JAR -> build/libs/propertee-java-java7.jar
./gradlew jar8      # Java 8 fat JAR -> build/libs/propertee-java-java8.jar
./gradlew jarAll    # Both JARs
./gradlew test      # Run JUnit tests
./test_all.sh all   # Run integration tests against both JARs
```

## How the Stepper Pattern Works

The `Stepper` interface replaces JavaScript's `function*`/`yield` pattern:

- **Statement visitors** return multi-step Steppers that yield `StepResult.BOUNDARY` between statements — the scheduler can switch threads here
- **Expression visitors** evaluate eagerly via `eval()` — expressions are atomic
- Threads communicate with the scheduler via `StepResult.command()` (`SLEEP`, `SPAWN_THREADS`)

Thread functions are **pure** with respect to global state:
- Can read globals via a snapshot taken at `multi` block entry
- Cannot write globals (enforced at runtime)
- Return results via `thread key: func()` syntax; results assigned only after all threads complete
- No locks, no shared mutable state

## External Functions & Result Pattern

Host applications can register external functions that return result objects:

```java
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

## Integrating with Legacy Java Systems

ProperTee Java is designed for embedding into existing Java applications, including legacy systems running Java 7. The interpreter has zero dependency on Java 8+ APIs, making it safe to deploy on older JVMs, application servers (Tomcat 7, JBoss EAP 6, WebSphere 8.x), and embedded environments.

### Dependencies

All dependencies target Java 7 compatibility:

| Library | Version | Purpose |
|---|---|---|
| ANTLR 4.9.3 | runtime + generated parser | Last version supporting Java 7 |
| Gson 2.8.9 | JSON property loading | Last version supporting Java 7 |

### Choosing the Right JAR

| Target Environment | JAR | Bytecode |
|---|---|---|
| Java 7 (JBoss EAP 6, Tomcat 7, WebSphere 8.x, Android < 7) | `propertee-java-java7.jar` | Major version 51 |
| Java 8+ (Tomcat 8+, WildFly, Spring Boot, modern stacks) | `propertee-java-java8.jar` | Major version 52 |

Both JARs are fat JARs containing all dependencies. Drop one into your `lib/` or `WEB-INF/lib/` directory.

### Programmatic Embedding

The interpreter can be invoked directly from Java code in four steps:

```java
import com.propertee.cli.Main;
import com.propertee.interpreter.ProperTeeInterpreter;
import com.propertee.interpreter.BuiltinFunctions;
import com.propertee.scheduler.Scheduler;
import com.propertee.stepper.Stepper;
import org.antlr.v4.runtime.*;

import java.util.*;

// 1. Parse the script
List<String> errors = new ArrayList<String>();
ProperTeeParser.RootContext tree = Main.parseScript(scriptText, errors);
if (tree == null) {
    for (String err : errors) {
        System.err.println(err);
    }
    return;
}

// 2. Create the interpreter
Map<String, Object> properties = new LinkedHashMap<String, Object>();
properties.put("appName", "MyLegacyApp");
properties.put("version", 3);

// Custom I/O — redirect PRINT output to your logging framework
BuiltinFunctions.PrintFunction stdout = new BuiltinFunctions.PrintFunction() {
    public void print(String message) {
        logger.info("[ProperTee] " + message);
    }
};
BuiltinFunctions.PrintFunction stderr = new BuiltinFunctions.PrintFunction() {
    public void print(String message) {
        logger.error("[ProperTee] " + message);
    }
};

ProperTeeInterpreter interpreter = new ProperTeeInterpreter(
    properties,   // read-only built-in properties accessible in scripts
    stdout,       // PRINT output handler
    stderr,       // error output handler
    1000,         // max loop iterations (safety limit)
    "error"       // "error" to throw on limit, "warn" to log and continue
);

// 3. Create a scheduler and run
Scheduler scheduler = new Scheduler(interpreter);
Stepper mainStepper = interpreter.createRootStepper(tree);
Object result = scheduler.run(mainStepper);

// 4. Read variables set by the script
Map<String, Object> vars = interpreter.variables;
```

### Integration Patterns

#### Servlet / JSP Environment

Run scripts in response to HTTP requests, passing request parameters as properties:

```java
// In a servlet
Map<String, Object> props = new LinkedHashMap<String, Object>();
props.put("method", request.getMethod());
props.put("path", request.getRequestURI());
props.put("userId", session.getAttribute("userId"));

// Capture output
final StringBuilder output = new StringBuilder();
BuiltinFunctions.PrintFunction capture = new BuiltinFunctions.PrintFunction() {
    public void print(String message) {
        output.append(message).append("\n");
    }
};

ProperTeeInterpreter interp = new ProperTeeInterpreter(props, capture, capture, 1000, "error");
Scheduler scheduler = new Scheduler(interp);
Stepper stepper = interp.createRootStepper(tree);
scheduler.run(stepper);

response.getWriter().write(output.toString());
```

#### Batch Processing / Scheduled Jobs

Use scripts as configurable business rules that can be updated without redeployment:

```java
// Load script from database or filesystem
String script = loadScriptFromDB(ruleId);
List<String> errors = new ArrayList<String>();
ProperTeeParser.RootContext tree = Main.parseScript(script, errors);

// Pass batch context as properties
Map<String, Object> props = new LinkedHashMap<String, Object>();
props.put("batchId", batchId);
props.put("recordCount", records.size());

ProperTeeInterpreter interp = new ProperTeeInterpreter(props, stdout, stderr, 5000, "error");
Scheduler scheduler = new Scheduler(interp);
scheduler.run(interp.createRootStepper(tree));

// Read the script's decision
Object action = interp.variables.get("action");
```

#### Pre-parsing for Performance

Parse scripts once and re-execute with different properties:

```java
// At startup — parse and cache
ProperTeeParser.RootContext cachedTree = Main.parseScript(scriptText, errors);

// Per request — create fresh interpreter, reuse parse tree
ProperTeeInterpreter interp = new ProperTeeInterpreter(requestProps, stdout, stderr, 1000, "error");
Scheduler scheduler = new Scheduler(interp);
scheduler.run(interp.createRootStepper(cachedTree));
```

### Safety and Limits

- **Loop limit:** The `maxIterations` parameter prevents runaway loops. Set it based on your use case (default 1000).
- **No file/network access:** Scripts have no I/O capabilities beyond `PRINT` and `SLEEP`. All data flows through properties and variables.
- **Thread purity:** Thread functions cannot mutate global state. They read a snapshot taken at `multi` block entry, preventing race conditions.
- **No reflection or class loading:** Scripts cannot access Java internals.

### Accessing Script Results

After execution, read values from the interpreter:

```java
// Script variables
Map<String, Object> vars = interpreter.variables;
String status = (String) vars.get("status");
List<Object> results = (List<Object>) vars.get("results");

// Type checking
TypeChecker.typeOf(value);    // "number", "string", "boolean", "list", "object"
TypeChecker.isNumber(value);  // true for Integer or Double
TypeChecker.formatValue(value); // human-readable string
```

### Data Types

| ProperTee Type | Java Type |
|---|---|
| number (integer) | `Integer` |
| number (decimal) | `Double` |
| string | `String` |
| boolean | `Boolean` |
| empty object `{}` | `LinkedHashMap` (empty) |
| list | `ArrayList<Object>` |
| object | `LinkedHashMap<String, Object>` |

Properties passed into the interpreter follow the same mapping. Use Gson-compatible types when constructing property maps.

## Testing

```bash
./gradlew test           # JUnit tests (69 test cases)
./test_all.sh            # Integration tests against Java 8 JAR
./test_all.sh java7      # Integration tests against Java 7 JAR
./test_all.sh all        # Integration tests against both JARs
```

## License

This project is licensed under the [BSD 3-Clause License](LICENSE).
