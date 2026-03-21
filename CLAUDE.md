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
- **`graph/`** — Opening tree as a graph keyed by `PositionKey`. `OpeningTree` is the in-memory graph storing `MutablePreviousAndNextMoves` per position. `TreeRepository` interface abstracts persistence (`DbTreeRepository` for local DB, `BookTreeRepository` for remote book). `NodeManager` orchestrates tree and repository. `NavigationHistory` handles back/forward navigation. `TrainingSchedule` manages spaced-repetition scheduling; `TrainingEntry` is a lightweight reference to trainable positions. `NodeState` enum represents position state. Mutable/immutable split: `MutablePreviousAndNextMoves` (live) vs `PreviousAndNextMoves` (snapshots for persistence).
- **`interactions/`** — Game interaction controllers. `InteractionsManager` (abstract) handles tile selection, move execution, and promotion flow. Concrete implementations: `LinesExplorer` (free exploration), `SingleMoveTrainer` (spaced-repetition training), `BookExplorer` (exploring and downloading community books).
- **`data/`** — Persistence layer. `DatabaseQueryManager` interface backed by Room (`nonJsMain`) for local storage. Sub-package `book/` contains `Book`, `BookMove`, and `SupabaseBookQueryManager` for community opening books. Sub-package `online/auth/` contains `AuthManager` for Supabase authentication (used by books).
- **`config/`** — App configuration and secrets. `SecretsTemplate.kt` defines secret fields; `Secrets.kt` is generated at build time from `local.properties`.
- **`date/`** — Spaced repetition scheduling (`NextDateCalculator`).

### UI layer (`ui/`)

Compose Multiplatform UI. Components in `ui/components/` (board, explore, training, navigation, popup, settings). Pages in `ui/pages/`. DI via Koin (`Koin.kt`).

## Key Conventions

- **Formatting**: ktfmt with Google style. Pre-commit hook checks formatting on `master`.
- **Testing**: Kotest assertions, no mocking. UI tests extend `TestFromMainActivity`. AAA pattern.
- **DI**: Koin for dependency injection. Modules defined in `Koin.kt`.
- **PR titles**: Must follow Conventional Commits (`feat(module): ...`, `fix: ...`).
- **Secrets**: Add to `SecretsTemplate.kt` with default `NOT_FOUND`, set real value in `local.properties` as `UPPER_SNAKE_CASE`. Generated `Secrets.kt` is gitignored.
- **Kotlin style**: Prefer `val` over `var`, avoid `!!` and `lateinit`, use sealed classes for state, leverage coroutines for async.
- **Visibility**: All fields and methods must be `private` whenever possible. Minimize public API surface.
- **Test-only code**: A method that is only used in tests is a bad pattern. Do not introduce `public`/`internal` methods solely for testing; instead, test through the public API.
- **Formatting**: Always run `./gradlew ktfmtFormat` before compiling, building, or testing.
- **KDoc**: Always add KDoc on all public declarations and every non-trivial `@Composable` function.
