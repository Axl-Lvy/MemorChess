# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project Overview

MemorChess (Anki Chess) is a Kotlin Multiplatform app for memorizing chess openings using spaced repetition. Targets Android, iOS, JVM desktop, and WebAssembly (wasmJs).

## Build & Test Commands

```sh
./gradlew build                                                              # build
./gradlew :composeApp:jvmRun                                                 # run desktop
./gradlew jvmTest                                                            # JVM/desktop tests
./gradlew jvmTest --tests "proj.memorchess.axl.core.engine.graph.TestCache"  # single test class
./gradlew :androidApp:connectedCheck                                        # Android instrumented tests
./gradlew :server:test                                                      # server tests (needs Docker)
./gradlew ktfmtCheck                                                        # check formatting
./gradlew ktfmtFormat                                                       # auto-format (ktfmt, Google style)
```

Android/JVM/iOS macro- and micro-benchmarks live in `:macrobenchmark` and `:microbenchmark`; see their READMEs before running them.

## Architecture

Six Gradle modules:

- **`shared`** — pure Kotlin Multiplatform code with no Compose and no Room, shared with `:server`: chess engine core, `PositionKey`, PGN parsing, sync wire types. `composeApp` depends on it with `api`, so its types stay visible to `androidApp` transitively.
- **`composeApp`** — the KMP library holding all app code (`core/` logic, `ui/` Compose UI). Its Android target uses the `com.android.kotlin.multiplatform.library` plugin (`kotlin.androidLibrary {}` DSL, no `android {}` block).
- **`server`** — JVM-only Ktor server (sync, JWKS-backed auth, REST routes). Configured entirely from the environment (`ServerConfig`); no vendor named in code.
- **`androidApp`** — thin Android shell (`MainActivity`, manifest, instrumented tests). Has a `benchmark` build type for `:macrobenchmark`.

Layer maps load automatically when you work in those directories:

- `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/CLAUDE.md`
- `composeApp/src/commonMain/kotlin/proj/memorchess/axl/ui/CLAUDE.md`

The toolchain is held below AGP 9.1 and the Gradle wrapper below 9.7.0. Read `docs/adr/0002-toolchain-version-holds.md` before touching either pin.

## Key Conventions

- **Formatting**: run `./gradlew ktfmtFormat` before building or testing; a pre-commit hook checks it on `master`.
- **Testing**: Kotest assertions, no mocking, AAA pattern.
- **Edge cases**: arithmetic/formatting on external data must be tested at `0`, the lowest non-zero value, both sides of every boundary, and a large value. A new state/branch/sealed subclass needs a propagation test through every consumer in the same PR.
- **Database migrations**: the app isn't in production — change Room/IndexedDB schemas freely, no migrations needed. Never re-enable Room schema export.
- **Adding `wasmJs` to a new module**: the first build fails on `:kotlinWasmStoreYarnLock` ("Lock file was changed"). Run `./gradlew kotlinWasmUpgradeYarnLock` once, then rebuild.
- **Testcontainers**: needs `systemProperty("api.version", "1.40")` on the test task (already set in `server/build.gradle.kts`), or every container test fails with a useless "no valid Docker environment" message. `DOCKER_API_VERSION` does not work here.
- **DI**: Koin; modules defined in `Koin.kt`.
- **Visibility**: work down the ladder (`private` → `internal` → `public`). `internal` does not cross Gradle module boundaries, so anything `shared` exposes to `composeApp` must be `public` there. A data class with a restricted constructor needs `@ConsistentCopyVisibility`.
- **Test-only code**: never add `public`/`internal` members solely for testing; test through the public API.
- **KDoc**: on all public declarations and every non-trivial `@Composable`.
- **Secrets**: no generation flow. Runtime credentials come from `System.getenv`; CI injects them from GitHub Actions secrets.
- **Server env vars**: `SYNC_DB_URL`, `SYNC_DB_USER`, `SYNC_DB_PASSWORD`, `SYNC_JWT_ISSUER`, `SYNC_JWT_AUDIENCE`, `SYNC_JWKS_URL`, optional `SYNC_PORT`. Validated at boot, so a missing one fails the boot rather than the first request.
- **PR titles**: Conventional Commits (`feat(module): ...`).
- **PR descriptions**: base every PR body on `@.github/pull_request_template.md`.
- **No force pushes**: never force-push or rewrite pushed history (`rebase`, `commit --amend`, `reset --hard`). Add commits on top; ask first if a force push seems genuinely necessary.
- **Worktrees**: always develop in a dedicated git worktree, never the main checkout — see the `using-git-worktrees` skill.
