# Repository Guidelines

## Project Structure & Module Organization
- Core Java sources live in `propertee-core/src/main/java/com/flatide/propertee/...` (packages split by concern: `core`, `interpreter`, `platform`, `runtime`, `scheduler`, `stepper`, `task`) and the CLI in `propertee-cli/src/main/java/com/flatide/propertee/cli`. All packages live under `com.flatide.propertee` since 1.7.0.
- Grammar source is `grammar/ProperTee.g4`; generated ANTLR files are written to `build/generated-src/antlr/main` during build (package `com.flatide.propertee.parser`).
- Unit/integration harness code is in `propertee-core/src/test/java/com/flatide/propertee/tests`.
- Script-based regression fixtures are in `propertee-core/src/test/resources/tests` as `.tee` + matching `.expected` file pairs.
- Supporting docs and runnable examples: `README.md`, `LANGUAGE.md`, `GUIDE.md`, `sample/`, and `demo/`.

## Build, Test, and Development Commands
- `./gradlew clean build`: full compile, ANTLR generation, and tests.
- `./gradlew test`: run JUnit 4 tests only.
- `./gradlew jar8` / `./gradlew jar7`: build fat JARs for Java 8 or Java 7 compatibility.
- `./gradlew jarAll dist`: build all JAR variants and copy to `dist/`.
- `./gradlew run --args='sample/01_hello.tee'`: run CLI via Gradle.
- `./test_all.sh all`: run fixture-based script regression tests against both Java 7/8 JARs.

## Coding Style & Naming Conventions
- Use 4-space indentation and keep brace style consistent with existing Java files.
- Preserve Java 7 compatibility in runtime code: avoid lambdas, streams, and Java 8+ APIs.
- Class names use `PascalCase`; methods/fields use `camelCase`; constants use `UPPER_SNAKE_CASE`.
- Keep package boundaries meaningful (e.g., scheduler logic stays in `scheduler`/`stepper`).

## Testing Guidelines
- Framework: JUnit 4 (`junit:junit:4.13.2`).
- Add or update fixture tests using paired files: `NN_name.tee` and `NN_name.expected`.
- Keep deterministic output; fixture comparisons are exact string matches in `test_all.sh`.
- Run `./gradlew test` plus `./test_all.sh java8` before opening a PR.

## Commit & Pull Request Guidelines
- Follow the observed commit style: short imperative subject lines (examples: `Add async external functions...`, `build dist`).
- Keep commits focused; separate parser/grammar changes from runtime behavior changes when possible.
- PRs should include: purpose, key design decisions, affected commands/tests, and sample output for behavior changes.
- Link related issues and mention any compatibility impact (Java 7/8, CLI flags, test fixtures).
