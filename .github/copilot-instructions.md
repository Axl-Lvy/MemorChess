# GitHub Copilot Instructions

These instructions define how GitHub Copilot should assist with this project. The goal is to ensure consistent,
high-quality code generation aligned with our conventions, stack, and best practices.

## 🧠 Context

- **Project Type**: Multiplatform Application (Android, IOS, Wasm, JVM)
- **Language**: Kotlin
- **Framework / Libraries**: Kotlin Multiplatform / Compose / Koin
- **Architecture**: Clean Architecture / Modular

## 🔧 General Guidelines

- Use Kotlin-idiomatic syntax and features (e.g., data classes, extension functions).
- Prefer immutable data (`val`, `List`) over mutable (`var`, `MutableList`).
- Use null safety, smart casting, and Elvis operators effectively.
- Favor expression-style syntax and scoped functions (`let`, `apply`, `run`, `with`).
- Keep files and functions concise and focused.

## 📁 File Structure

Use this structure as a guide when creating or updating files:

```text
src/
  main/
    kotlin/
      proj/
        memorchess/
          axl/
            core/
              data/
              engine/
            ui/
              components/
              layouts/
  test/
    kotlin/
      proj/
        memorchess/
          axl/
            database/
            training/
```

## 🧶 Patterns

### ✅ Patterns to Follow

- Use data classes for DTOs and model representations.
- Use sealed classes and `when` expressions for state/result handling.
- Leverage Coroutines for async and non-blocking operations.
- Use dependency injection via Koin.
- Handle errors using `Result`, `sealed class`, or exception mapping.
- Prefer composition to inheritance.
- Document public classes and functions with KDoc.
- Document Compose UI components with KDoc.

### 🚫 Patterns to Avoid

- Don’t ignore nullability warnings—handle them explicitly.
- Avoid use of `!!` (force unwrap).
- Don’t expose mutable internal state—prefer immutable interfaces.
- Avoid using `lateinit` unless absolutely necessary.
- Don’t overuse global objects or singletons without lifecycle management.
- Don't abuse of comments; write self-documenting code instead.

## 🧪 Testing Guidelines

- Use `Kotest` for unit and integration tests.
- Never mock objects.
- Use Coroutines test utilities for suspending functions.
- Structure tests by feature and follow the AAA (Arrange-Act-Assert) pattern. No need to specify comments with
  `// Arrange`, `// Act`, `// Assert` as the structure should be clear.
- Test state flows and edge/error conditions.
- Extend `TestFromMainActivity` for UI tests.
