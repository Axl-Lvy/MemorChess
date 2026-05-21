# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MemorChess (Anki Chess) is a Kotlin Multiplatform app for memorizing chess openings using spaced repetition. Targets Android, iOS, JVM desktop, and WebAssembly (wasmJs).

## Build & Test Commands

```sh
# Build
./gradlew build

# Run desktop (JVM)
./gradlew :composeApp:run

# Run tests
./gradlew desktopTest                          # JVM/desktop tests
./gradlew connectedAndroidTest                 # Android instrumented tests

# Run a single test class (desktop)
./gradlew desktopTest --tests "proj.memorchess.axl.core.engine.graph.TestCache"

# Code formatting (ktfmt, Google style)
./gradlew ktfmtCheck                           # Check formatting
./gradlew ktfmtFormat                          # Auto-format
```

## Architecture

Single Gradle module (`composeApp`) with Kotlin Multiplatform source sets: `commonMain`, `androidMain`, `jvmMain`, `iosMain`, `wasmJsMain`, `nonJsMain` (Room DB shared by Android/JVM/iOS), `debugMain` (hot-reload previews).

### Core layer (`core/`)

- **`engine/`** — Chess engine wrapper around the `chess-core` library (`io.github.alluhemanth:chess-core`). `GameEngine` is the central class: manages board state, validates moves (SAN and coordinate-based), handles promotions, and produces position FENs. Value classes: `Fen` (full 6-part FEN string) and `PositionKey` (cropped FEN without move counters, used as graph key). Helper types: `Player`, `ChessPiece`, `PieceKind`, `BoardLocation`, `TileColor`.
- **`graph/`** — Opening tree as a graph keyed by `PositionKey`. `OpeningTree` is a pure in memory graph of immutable `Node` and `Edge` values. `TreeStore` is the single mutation chokepoint: it wraps `DatabaseQueryManager`, owns the cached `OpeningTree`, and is what UI, interactions, and scheduling code talk to. `TrainingScheduler` drives spaced repetition through `TreeStore` and `SchedulingAlgorithm`. `NavigationHistory` handles back/forward navigation over `PositionKey` and `Edge` values. `TrainingEntry` is a lightweight reference to a trainable position. `NodeState` enum represents position state. `DeleteMode` (`HARD`/`SOFT`) selects deletion semantics; the app defaults to `HARD` because it is local only. `PreviousAndNextMoves` lives at the persistence seam only as a DTO for `DatabaseQueryManager`.
- **`interactions/`** — Game interaction controllers. `InteractionsManager` (abstract) handles tile selection, move execution, and promotion flow. Concrete implementations: `LinesExplorer` (free exploration, talks to `TreeStore`), `SingleMoveTrainer` (spaced-repetition training, talks to `TrainingScheduler`).
- **`data/`** — Low level persistence seam. `DatabaseQueryManager` is implemented by a Room backed store in `nonJsMain` and an IndexedDB backed one in `wasmJsMain`; only `TreeStore` and the platform impls touch this interface. No remote backend and no synchronization layer.
- **`config/`** — App configuration and secrets. `SecretsTemplate.kt` defines secret fields; `Secrets.kt` is generated at build time from `local.properties`.
- **`scheduling/`** — Spaced repetition scheduling. `SchedulingAlgorithm` is the algorithm interface; `Fsrs6SchedulingAlgorithm` is the active FSRS 6 implementation. `CardState` is the per card state (`dueDate`, `lastReview`, `stability`, `difficulty`, `reps`, `lapses`) and `ReviewGrade` is the cross algorithm rating enum (`AGAIN`, `HARD`, `GOOD`, `EASY`).
- **`date/`** — Date utilities (`DateUtil`) shared across the codebase.

### UI layer (`ui/`)

Compose Multiplatform UI. Components in `ui/components/` (board, explore, training, navigation, popup, settings). Pages in `ui/pages/`. DI via Koin (`Koin.kt`).

**`ui/**` is excluded from Sonar coverage** because `@Composable` functions emit synthetic branches that JaCoCo can't filter, which made the uncovered-condition counts meaningless. That means there is **no automated safety net for UI code** — coverage gating, new-code coverage thresholds, and uncovered-line reports all skip this folder. When writing or modifying anything under `ui/`, deliberately think through every branch: empty/loading/error states, zero and boundary values (per the numeric-edge-cases rule), every `when` arm, every nullable, every conditional `Modifier`, every state transition. The cost of a regression here is the same as anywhere else; the difference is that nothing will catch it for you.

## Key Conventions

- **Formatting**: ktfmt with Google style. Pre-commit hook checks formatting on `master`.
- **Testing**: Kotest assertions, no mocking. UI tests extend `TestFromMainActivity`. AAA pattern.
- **Numeric edge cases**: Any code that does arithmetic, division, weighting, or formatting on numbers that come from external data (API counts, ratings, percentages, durations, file sizes) must have a test that includes `0`, the lowest non zero value, the value just below and just above each formatting or branching boundary, and a representative large value. Adding a new state, branch, or sealed subclass to a state machine requires a propagation test through every consumer in the same PR. The rule exists because picking a single happy path sample (`white=10, draws=5, black=3`) hides crashes that only fire on edge data like `Modifier.weight(0f)`.
- **DI**: Koin for dependency injection. Modules defined in `Koin.kt`.
- **PR titles**: Must follow Conventional Commits (`feat(module): ...`, `fix: ...`).
- **Secrets**: Add to `SecretsTemplate.kt` with default `NOT_FOUND`, set real value in `local.properties` as `UPPER_SNAKE_CASE`. Generated `Secrets.kt` is gitignored.
- **Kotlin style**: Prefer `val` over `var`, avoid `!!` and `lateinit`, use sealed classes for state, leverage coroutines for async.
- **Visibility**: All fields and methods must be `private` whenever possible. Minimize public API surface.
- **Test-only code**: A method that is only used in tests is a bad pattern. Do not introduce `public`/`internal` methods solely for testing; instead, test through the public API.
- **Formatting**: Always run `./gradlew ktfmtFormat` before compiling, building, or testing.
- **KDoc**: Always add KDoc on all public declarations and every non-trivial `@Composable` function.
