# CLAUDE.md

MemorChess is a Kotlin Multiplatform app for memorizing chess openings with spaced repetition. Targets Android, iOS, JVM desktop, and wasmJs.

## Commands

```sh
./gradlew ktfmtFormat                                                                   # format (ktfmt, Google style); a pre-commit hook checks it on master
./gradlew jvmTest                                                                       # composeApp + shared tests
./gradlew :composeApp:jvmTest --tests "proj.memorchess.axl.core.engine.graph.TestCache" # one class
./gradlew :composeApp:wasmJsTest --no-configuration-cache                                # wasm UI tests
./gradlew :server:test                                                                  # needs Docker
./gradlew :e2e:test                                                                     # client against a real server, needs Docker
./gradlew :androidApp:connectedCheck                                                    # Android instrumented tests
./gradlew :composeApp:jvmRun                                                            # run desktop
```

Read the `:macrobenchmark` and `:microbenchmark` READMEs before running benchmarks.

## Modules

- `shared` is pure KMP (no Compose, no Room) and shared with `:server`. `internal` does not cross module boundaries, so anything `composeApp` uses from it must be `public`.
- `composeApp`'s Android target uses `com.android.kotlin.multiplatform.library`: configure it in `kotlin.androidLibrary {}`, there is no `android {}` block.
- `server` is configured only from the environment, validated at boot: `SYNC_DB_URL`, `SYNC_DB_USER`, `SYNC_DB_PASSWORD`, `SYNC_JWT_ISSUER`, `SYNC_JWT_AUDIENCE`, `SYNC_JWKS_URL`, optional `SYNC_PORT`. No vendor is named in code.
- The Gradle wrapper is held below 9.7.0. Read `docs/adr/0002-toolchain-version-holds.md` before touching that pin.

## Code

- **Comments**: prefer names and small functions that make a comment unnecessary. Every public declaration gets a one or two line KDoc saying what it does, not how; `@param`/`@return` only when they add something the name does not. Inline comments only for a non-obvious why (an invariant, a workaround). No history, issue, PR, or mockup references, no section dividers, no list of callers.
- **Types**: a domain concept gets its own type (`@JvmInline value class`, enum, sealed type) rather than a raw `String`/`Int`/`Long`/`Double`/`Boolean`, including in wire DTOs and server routes. Ids, positions, moves, tokens, revisions, and durations (`kotlin.time.Duration`, `Instant`) are never bare primitives; a tri-state is an enum, not a `Boolean?`, and the side to move is a `Player`, not a Boolean.
  - Validate once, at the boundary: a type whose values are constrained (e.g. a canonical UUID) has a `parse` factory, and every route or decoder goes through it.
  - `@Serializable` value classes encode as their bare value, so they are safe on the wire. Enums on the wire carry `@SerialName` values matching the existing strings; decoding a value that may grow (e.g. `CardPhase`) falls back to a default instead of throwing.
  - Room entities, Exposed columns, and IndexedDB `external interface : JsAny` fields stay primitive; convert in the mappers, as `MoveEntity` does for `PositionKey`. SQL never hard-codes an enum's name as a string literal.
  - Secrets (OAuth access and refresh tokens) get their own type with a redacted `toString()`, so a data class holding one cannot log it.
  - Not worth a type: values used inside one algorithm (FSRS `stability`/`difficulty`), UI ratios, plain counts and depths.
- **Visibility**: work down the ladder (`private` → `internal` → `public`). A data class with a restricted constructor needs `@ConsistentCopyVisibility`.
- **Database**: the app is not in production. Change Room/IndexedDB schemas freely without migrations. Never re-enable Room schema export.
- **Secrets**: read from `System.getenv`; CI injects them from GitHub Actions secrets. There is no generation flow.

## Tests

- Kotest assertions, no mocking. Arrange/act/assert sections are separated by blank lines, without `// Arrange` markers.
- Never add `public`/`internal` members only for tests; test through the public API.
- Arithmetic/formatting on external data is tested at `0`, the lowest non-zero value, both sides of every boundary, and a large value. A new state, branch, or sealed subclass needs a propagation test through every consumer in the same PR.
- Testcontainers needs `systemProperty("api.version", "1.40")` on the test task (set in `server/build.gradle.kts`), otherwise every container test fails with "no valid Docker environment". `DOCKER_API_VERSION` does not work.
- Adding `wasmJs` to a module fails the first build on `:kotlinWasmStoreYarnLock`. Run `./gradlew kotlinWasmUpgradeYarnLock` once, then rebuild.

## Git

- Develop in a dedicated worktree, never the main checkout (`using-git-worktrees` skill).
- PR titles follow Conventional Commits (`feat(module): ...`); PR bodies use `.github/pull_request_template.md`.
- Never force-push or rewrite pushed history. Add commits on top, and ask first if a force push seems necessary.
