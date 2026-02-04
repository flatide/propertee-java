# ProperTee Language Guide

A beginner-friendly introduction to the ProperTee concurrent DSL.

---

## Getting Started

Run a script:

```bash
java -jar propertee-java.jar script.pt
```

Or start the interactive REPL:

```bash
java -jar propertee-java.jar
```

---

## Variables

Variables are dynamically typed. Assign with `=`:

```
name = "Alice"
age = 30
pi = 3.14
active = true
```

No declaration keyword needed — just assign.

---

## Data Types

| Type | Examples |
|---|---|
| Number (integer) | `10`, `-3`, `0` |
| Number (decimal) | `3.14`, `-0.5` |
| String | `"hello"`, `"it's \"quoted\""` |
| Boolean | `true`, `false` |
| Array | `[1, 2, 3]`, `["a", "b"]` |
| Object | `{name: "Alice", age: 30}` |
| Empty object | `{}` |

Strings support `\"` and `\\` escape sequences.

Arrays use **1-based indexing** — the first element is at position 1.

---

## Operators

### Arithmetic

```
x = 10 + 3    // 13
x = 10 - 3    // 7
x = 10 * 3    // 30
x = 10 / 3    // 3.3333...
x = 10 % 3    // 1
x = -5        // negation
```

Division always produces a decimal number.

### String Concatenation

```
greeting = "Hello, " + "World!"
```

Both sides of `+` must be the same type — use `TO_STRING()` to convert numbers:

```
msg = "You are " + TO_STRING(age) + " years old"
```

### Comparison

```
==   !=   >   <   >=   <=
```

### Logical

```
true and false   // false
true or false    // true
not true         // false
```

Both operands of `and`/`or` must be booleans. No truthy/falsy coercion.

---

## Comments

```
// Single-line comment

/* Multi-line
   comment */
```

---

## If / Else

```
if score >= 90 then
    PRINT("Excellent!")
else
    PRINT("Keep trying")
end
```

The `else` clause is optional. Conditions must be boolean expressions.

---

## Loops

### Condition Loop

Repeats while a condition is true:

```
i = 1
loop i <= 5 do
    PRINT(i)
    i = i + 1
end
```

By default, loops are limited to 1000 iterations for safety. Add `infinite` to remove the limit:

```
loop condition infinite do
    // unlimited iterations
end
```

### Value Loop

Iterate over elements of an array or object:

```
colors = ["red", "green", "blue"]
loop color in colors do
    PRINT(color)
end
```

### Key-Value Loop

Get both key and value:

```
person = {name: "Alice", age: 30}
loop key, val in person do
    PRINT(key + ": " + TO_STRING(val))
end
```

For arrays, the key is the 1-based index.

### Break and Continue

```
loop i <= 10 do
    if i == 5 then break end
    if i % 2 == 0 then continue end
    PRINT(i)
    i = i + 1
end
```

---

## Functions

```
function greet(name) do
    return "Hello, " + name + "!"
end

PRINT(greet("World"))
```

Functions without a `return` statement return an empty object `{}`.

A bare `return` (no expression) also returns `{}`:

```
function doWork() do
    PRINT("working...")
    return
end
```

Functions support recursion:

```
function factorial(n) do
    if n <= 1 then return 1 end
    return n * factorial(n - 1)
end
```

---

## Arrays

```
fruits = ["apple", "banana", "cherry"]

// Access (1-based)
first = fruits.1           // "apple"

// Modify
fruits.1 = "avocado"

// Built-in functions
fruits = PUSH(fruits, "date")     // append item
fruits = POP(fruits)              // remove last
length = LEN(fruits)              // count
part = SLICE(fruits, 1, 3)        // sub-array
merged = CONCAT([1, 2], [3, 4])  // [1, 2, 3, 4]
```

---

## Objects

```
person = {name: "Alice", age: 30, city: "NYC"}

// Access
PRINT(person.name)
PRINT(person."city")

// Modify
person.age = 31
person."email" = "alice@example.com"

// Dynamic access
field = "name"
PRINT(person.$field)

// Computed key access
fields = ["name", "age"]
PRINT(person.$(fields.1))

// Computed keys in object literals
key = "color"
obj = {$key: "blue"}    // same as {color: "blue"}
```

---

## Access Patterns

ProperTee supports several ways to access properties and array elements:

| Pattern | Description | Example |
|---|---|---|
| `.name` | Static property | `obj.name` |
| `.1` | Array index (1-based) | `arr.1` |
| `."key"` | String key | `obj."special-key"` |
| `.$var` | Dynamic variable | `obj.$field` |
| `.$(expr)` | Dynamic expression | `obj.$(getKey())` |

All patterns work for both reading and writing. They can be chained: `team.members.1.name`.

---

## Strings

```
s = "Hello, World!"

// Built-in string functions
LEN(s)              // 13
UPPERCASE(s)        // "HELLO, WORLD!"
LOWERCASE(s)        // "hello, world!"
TRIM("  hi  ")      // "hi"
SUBSTRING(s, 1, 5)  // "Hello"  (1-based, length 5)
SPLIT("a,b,c", ",") // ["a", "b", "c"]
JOIN(["a","b"], "-") // "a-b"
CHARS("abc")         // ["a", "b", "c"]
```

`SUBSTRING` uses 1-based positions. The second argument is the length to extract.

---

## Type Conversion

```
n = TO_NUMBER("42")      // 42
s = TO_STRING(3.14)      // "3.14"
s = TO_STRING(true)      // "true"
s = TO_STRING({a: 1})    // "{a: 1}"
```

---

## Built-in Functions Reference

### Output
| Function | Description |
|---|---|
| `PRINT(...)` | Print values separated by spaces |

### Math
| Function | Description |
|---|---|
| `SUM(...)` | Sum of all arguments |
| `MAX(...)` | Maximum value |
| `MIN(...)` | Minimum value |
| `ABS(n)` | Absolute value |
| `FLOOR(n)` | Round down |
| `CEIL(n)` | Round up |
| `ROUND(n)` | Round to nearest integer |

### Strings
| Function | Description |
|---|---|
| `LEN(s)` | Length of string or array |
| `TRIM(s)` | Remove whitespace |
| `UPPERCASE(s)` | Convert to upper case |
| `LOWERCASE(s)` | Convert to lower case |
| `SUBSTRING(s, start)` | Substring from position (1-based) |
| `SUBSTRING(s, start, len)` | Substring of given length |
| `SPLIT(s, delim)` | Split into array |
| `JOIN(arr, sep)` | Join array to string |
| `CHARS(s)` | String to character array |

### Arrays
| Function | Description |
|---|---|
| `LEN(arr)` | Number of elements |
| `PUSH(arr, ...)` | New array with items appended |
| `POP(arr)` | New array without last item |
| `SLICE(arr, start)` | Sub-array from position |
| `SLICE(arr, start, end)` | Sub-array (end exclusive) |
| `CONCAT(...)` | Merge arrays |

### Conversion
| Function | Description |
|---|---|
| `TO_NUMBER(s)` | String to number |
| `TO_STRING(v)` | Any value to string |

### Threading
| Function | Description |
|---|---|
| `SLEEP(ms)` | Pause current thread |

---

## Threads and Parallel Execution

ProperTee supports cooperative multithreading through `thread` functions and `multi` blocks.

### Defining Thread Functions

```
thread worker(name) do
    PRINT(name + " started")
    SLEEP(100)
    PRINT(name + " done")
    return name + " result"
end
```

Thread functions are like regular functions but can only be called inside `multi` blocks.

### Running Threads

```
multi
    worker("A") -> resultA
    worker("B") -> resultB
end

PRINT(resultA)   // "A result"
PRINT(resultB)   // "B result"
```

All threads in a `multi` block run concurrently. The `->` operator captures each thread's return value. Results are available after the `multi` block ends.

### Monitor

A `monitor` clause runs code periodically while threads execute:

```
multi
    longTask() -> result
monitor 500
    PRINT("still running...")
end
```

The number after `monitor` is the interval in milliseconds.

### Thread Rules

- Threads **can read** global variables (via a snapshot taken when the `multi` block starts)
- Threads **cannot write** to global variables
- Threads can call other thread functions and built-in functions
- Threads **cannot** call regular functions
- `SLEEP()` pauses only the calling thread — other threads keep running

---

## Result Pattern (External Functions)

External functions registered from Java can return result objects for error handling:

```
res = GET_BALANCE("alice")

if res.ok == true then
    PRINT("Balance: " + TO_STRING(res.value))
end

res2 = GET_BALANCE("unknown")

if res2.ok == false then
    PRINT("Error: " + res2.value)
end
```

Result objects have two fields:
- `ok` — `true` for success, `false` for error
- `value` — the result value on success, or an error message on failure

---

## Running Scripts from the Command Line

```bash
# Run a script
java -jar propertee-java.jar script.pt

# Pass built-in properties
java -jar propertee-java.jar -p '{"width":100}' script.pt

# Properties from a JSON file
java -jar propertee-java.jar -f props.json script.pt

# Set custom loop iteration limit
java -jar propertee-java.jar --max-iterations 5000 script.pt
```

Built-in properties are accessible as global variables in the script.

---

## Quick Reference

```
// Variables
x = 42
name = "ProperTee"

// If/else
if x > 0 then PRINT("positive") else PRINT("non-positive") end

// Condition loop
loop x > 0 do x = x - 1 end

// Value loop
loop item in [1, 2, 3] do PRINT(item) end

// Key-value loop
loop k, v in {a: 1} do PRINT(k, v) end

// Function
function double(n) do return n * 2 end

// Thread + multi
thread work(id) do return id end
multi
    work(1) -> r1
    work(2) -> r2
end

// Access patterns
obj.name    obj.1    obj."key"    obj.$var    obj.$(expr)
```

---

## Reserved Keywords

`if` `then` `else` `end` `loop` `in` `do` `break` `continue`
`function` `thread` `return` `not` `and` `or` `true` `false`
`infinite` `multi` `monitor`
