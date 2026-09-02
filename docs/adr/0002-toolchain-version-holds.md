# ADR 0002: Hold the toolchain at AGP 9.0.x and Gradle 9.6.x

## Status

Accepted, 2026-09-03. Enforced by `packageRules` in `renovate.json`.

## Context

Four dependency lines are pinned below their latest release. They are
recorded together because three of them are one constraint wearing
different hats, and a fifth PR that "just bumps one" reopens the whole
problem.

**AGP below 9.1.** IntelliJ IDEA only syncs AGP versions its bundled
Android plugin supports. IDEA up to 2026.2 supports the 9.0.x line
only; AGP 9.1 support is tracked upstream as IDEA-390133. Raising AGP
past the cap does not break the Gradle build, it breaks project sync
in the IDE, which is worse: the failure shows up as a broken editor
rather than a red build.

**`org.jetbrains.androidx.lifecycle` below 2.11.0.** Lifecycle 2.11.0
declares AAR metadata forcing AGP 9.1.0 and `compileSdk` 37, so
resolving it fails the build while the AGP cap stands.

**Compose Multiplatform below 1.12.0, and `androidx.compose` with
it.** Compose Multiplatform 1.12.0 resolves to `androidx.compose`
1.12.0, whose AAR metadata carries the same AGP 9.1.0 +
`compileSdk` 37 requirement. `androidx.compose.runtime` and
`androidx.compose.ui` are pinned separately so a transitive bump can't
drag them past the Compose Multiplatform release they must track.

**Gradle wrapper below 9.7.0.** Unrelated to AGP. Gradle 9.7.x
exhausts the daemon heap while storing the configuration cache entry
for the Kotlin/Wasm browser tasks (`KotlinWebpack`, `KotlinJsTest`),
so `:composeApp:wasmJsBrowserDevelopmentRun` and
`:composeApp:wasmJsTest` die with `Java heap space` before any task
runs. The entry is discarded anyway — those tasks are not
configuration-cache compatible — and 9.6.1 stores and discards it in
about 25 seconds.

## Decision

Encode every hold as a renovate `packageRules` entry with an
`allowedVersions` bound and a `description` explaining it, so the
constraint travels with the tool that would otherwise violate it.
Renovate then stops proposing the bad bump instead of proposing it
every Monday and failing CI.

The three AGP-derived holds lift together, and only once IDEA ships
AGP 9.1 sync support. The Gradle wrapper hold lifts independently,
once a Gradle release stops running the daemon out of heap on the
Kotlin/Wasm configuration cache entry — or once those tasks become
configuration-cache compatible.

## Consequences

- The project stays on an IDE-syncable AGP at the cost of trailing the
  Android and Compose release lines. Given that IDEA is the primary
  development environment, editor sync is worth more than a minor
  version.
- Lifting the AGP cap is a four-rule change, not a one-rule change.
  Bumping AGP alone leaves the lifecycle and Compose pins in place and
  silently keeps the app on old Compose.
- The `compileSdk` 37 move is deferred along with the cap, so any API
  that needs it is out of reach for now.
- Renovate PRs for these lines will simply not appear. Absence of an
  update PR is not evidence that the dependency is current — check
  `renovate.json` before concluding the project is up to date.

## Links

- IDEA-390133: AGP 9.1 sync support in IntelliJ IDEA
- PR #240: pin `androidx-lifecycle` below 2.11.0 to keep AGP on 9.0.x
- Commit 62ca73c: hold Compose below 1.12.0, Gradle wrapper below 9.7.0
