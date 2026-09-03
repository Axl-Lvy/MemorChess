# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MemorChess (Anki Chess) is a Kotlin Multiplatform app for memorizing chess openings using spaced repetition. Targets Android, iOS, JVM desktop, and WebAssembly (wasmJs).

## Build & Test Commands

```sh
# Build
./gradlew build

# Run desktop (JVM)
./gradlew :composeApp:jvmRun

# Run tests
./gradlew jvmTest                              # JVM/desktop tests
./gradlew :androidApp:connectedCheck           # Android instrumented tests

# Run a single test class (desktop)
./gradlew jvmTest --tests "proj.memorchess.axl.core.engine.graph.TestCache"

# Code formatting (ktfmt, Google style)
./gradlew ktfmtCheck                           # Check formatting
./gradlew ktfmtFormat                          # Auto-format

# UI performance benchmarks (Android device/emulator only, see macrobenchmark/README.md)
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest

# Core logic microbenchmarks (plain JVM, see microbenchmark/README.md)
./gradlew :microbenchmark:benchmark
```

## Architecture

Five Gradle modules:

- **`shared`** — pure Kotlin Multiplatform code with no Compose and no Room, shared with the future `:server` module: the chess engine core (`core/engine/`, minus `evaluation/`), `PositionKey`, and PGN parsing (`PgnParser`, `PgnGame`, `PgnParseException`). Packages match their original `composeApp` locations, so a package split across the two modules is normal here and intended. `composeApp` depends on it with `api`, so the moved types stay visible to `androidApp` and `microbenchmark` transitively.
- **`composeApp`** — the Kotlin Multiplatform library holding all shared code (`core/` logic, `ui/` Compose UI). Source sets: `commonMain`, `androidMain` (platform actuals, OAuth redirect activity, `AndroidContextProvider`), `jvmMain`, `iosMain`, `wasmJsMain`, `nonJsMain` (Room DB shared by Android/JVM/iOS), `debugMain` (hot-reload previews). Its Android target uses the `com.android.kotlin.multiplatform.library` plugin (`kotlin.androidLibrary {}` DSL, no `android {}` block).
- **`androidApp`** — the thin Android application shell: `MainActivity`, launcher manifest and resources, and the instrumented tests (`src/androidTest`). Adds a `benchmark` build type (release performance, debug signing, profileable) measured by `:macrobenchmark`.
- **`macrobenchmark`** — UI performance benchmarks run against `androidApp`'s `benchmark` build on a device or emulator. See `macrobenchmark/README.md`.
- **`microbenchmark`** — JVM only JMH benchmarks over `composeApp`'s pure Kotlin core. Catches algorithmic regressions only; JVM numbers are not ART numbers. See `microbenchmark/README.md`.

Layer maps live next to the code they describe and load automatically when you work in those directories:

- `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/CLAUDE.md` — the core layer, package by package.
- `composeApp/src/commonMain/kotlin/proj/memorchess/axl/ui/CLAUDE.md` — the UI layer, and why it has no coverage safety net.

The toolchain is deliberately held below AGP 9.1, and the Gradle wrapper below 9.7.0. Read `docs/adr/0002-toolchain-version-holds.md` before raising any of those pins.

## Key Conventions

- **Formatting**: ktfmt with Google style. Always run `./gradlew ktfmtFormat` before compiling, building, or testing. A pre-commit hook checks formatting on `master`.
- **Testing**: Kotest assertions, no mocking, AAA pattern. Android UI tests live in `androidApp/src/androidTest` and use `createAndroidComposeRule<MainActivity>()`.
- **Edge cases**: arithmetic, division, weighting, or formatting on numbers from external data must be tested at `0`, the lowest non zero value, either side of every formatting or branching boundary, and a representative large value. Adding a new state, branch, or sealed subclass to a state machine requires a propagation test through every consumer in the same PR.
- **Database migrations**: the app is not in production, so change the Room (`nonJsMain`) and IndexedDB (`wasmJsMain`) schemas freely **without writing migrations** — recreating the local database is acceptable. Room runs with `exportSchema = false` and `fallbackToDestructiveMigration(dropAllTables = true)`; never re-enable schema export (no `schemas/*.json` should ever be generated or committed). For IndexedDB (`IndexedDbInstance`), bump `DB_VERSION` and keep the single destructive `recreate` upgrade — never add per-version branches.
- **DI**: Koin for dependency injection. Modules defined in `Koin.kt`.
- **Visibility**: all fields and methods must be `private` whenever possible. Minimize public API surface.
- **Test-only code**: never introduce `public`/`internal` members solely for testing; test through the public API.
- **Kotlin style**: prefer `val` over `var`, avoid `!!` and `lateinit`, use sealed classes for state, leverage coroutines for async.
- **KDoc**: always add KDoc on all public declarations and every non-trivial `@Composable` function.
- **Secrets**: there is no secrets-generation flow. Runtime credentials (e.g. `LICHESS_API_TOKEN`) are read from the process environment via `System.getenv`; CI injects them from GitHub Actions secrets.
- **PR titles**: must follow Conventional Commits (`feat(module): ...`, `fix: ...`).
- **PR descriptions**: You MUST always use `@.github/pull_request_template.md` as the base for every PR body. Start the body with the template content (the `## Options` checkboxes, unchecked by default) and add the description below it.
- **No force pushes**: never force push (`git push --force` / `--force-with-lease`) to any branch, and never run history-rewriting commands (`git rebase`, `git commit --amend`, `git reset --hard` on pushed commits) that would require a force push to upload. Add commits on top instead. If a force push seems genuinely necessary, stop and ask the user first.
