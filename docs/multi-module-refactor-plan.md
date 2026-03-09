# Multi-Module Refactor Plan

## Goal

Split the repository into three Gradle modules without changing runtime behavior:

1. `propertee-core`
2. `propertee-cli`
3. `propertee-mockserver`

The immediate objective is to remove dependencies from `mockserver` to `cli` so the later physical split is mechanical rather than architectural.

## Target Module Boundaries

### `propertee-core`

- `grammar/`
- `com.propertee.parser`
- `com.propertee.core`
- `com.propertee.interpreter`
- `com.propertee.runtime`
- `com.propertee.scheduler`
- `com.propertee.stepper`
- `com.propertee.task`

### `propertee-cli`

- `com.propertee.cli`

### `propertee-mockserver`

- `com.propertee.mockserver`
- `deploy/mockserver`
- `demo/mockserver`

## Phase 0: Decouple Before Moving Files

1. Introduce a parser facade in core:
   - `com.propertee.core.ScriptParser`
2. Replace `Main.parseScript()` usage in:
   - `mockserver`
   - tests
   - REPL
3. Keep `Main.parseScript()` as a temporary compatibility wrapper.

This phase removes the current `mockserver -> cli` dependency and makes `cli` optional.

## Phase 1: Gradle Module Split

1. Update `settings.gradle`:
   - include `propertee-core`, `propertee-cli`, `propertee-mockserver`
2. Move source trees without renaming packages.
3. Keep shared dependency versions and Java 7/8 jar logic centralized at the root.

Dependency graph:

- `propertee-cli -> propertee-core`
- `propertee-mockserver -> propertee-core`

## Phase 2: Test Split

Move tests to the owning module:

- core:
  - script fixtures
  - async demo
  - task engine tests
- mockserver:
  - mock server API/config tests

## Phase 3: Packaging Split

- `propertee-cli`: CLI fat jars
- `propertee-mockserver`: mock server fat jar + deployment zip
- `propertee-core`: library jar

## Notes

- `TaskEngine` stays in core.
- `Main.parseScript()` should be removed only after all internal call sites use `ScriptParser`.
- Physical repo split, if needed later, should happen only after this module boundary is stable.
