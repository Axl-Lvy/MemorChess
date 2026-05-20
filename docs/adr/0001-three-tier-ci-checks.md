# ADR 0001: Three tier CI checks (noop / cheap / full)

## Status

Accepted, 2026-05-20. Implemented in PR #137.

## Context

The `Check code` workflow originally ran the same set of jobs for every
pull request: format check, JVM tests with Kover coverage, Android
instrumented tests sharded four ways, wasm tests, Sonar analysis, and
test result publishing. The wall time was around 8 minutes on a green
PR and around 10 minutes on a master push.

That cost was paid even when the PR touched no code (README typo,
LICENSE update, CI tweak) or when it touched only common or JVM code
that none of the platform specific jobs could possibly regress. The
`is-check-necessary` gate that existed in the old workflow could only
skip every test job or run every test job, with a `[skip-tests]` title
override.

Real timings from runs `26167170479` and `26164384787` showed the
critical path was always
`android-tests (longest shard) -> sonar-analysis`, with `wasm-tests`
close behind. The other parallel jobs (`jvm-tests`, `format-check`)
finished long before, but the runner had no way to know that the
expensive jobs were unnecessary for the change being reviewed.

## Decision

Classify every push into one of three tiers via a `detect` job that
runs `dorny/paths-filter@v3` against the diff:

- **noop**: no code or build file touched. Only `detect` and
  `check-status` run. README, LICENSE, images, issue templates.
- **cheap**: code in `commonMain` or `jvmMain` (or their tests) touched
  but no platform specific source set, manifest, gradle script, or
  version catalog. Runs `format-check`, `jvm-tests` (with Kover and
  Sonar inline), `compile-multiplat` (a debug Android and wasm compile
  to catch `expect`/`actual` regressions), and `publish-test-results`.
- **full**: anything in `androidMain`, `iosMain`, `wasmJsMain`,
  `nonJsMain`, `androidInstrumentedTest`, `debugMain`, an Android
  manifest, any `*.gradle.kts`, `gradle/libs.versions.toml`, or
  `.github/workflows/check.yml` touched. Runs the cheap-tier set plus
  `android-tests` (4-shard matrix), `wasm-tests`, and a standalone
  `sonar-analysis` job that aggregates JVM Kover with the per shard
  Android jacoco reports. Scheduled cron runs and the
  `[x] Force running tests` PR body checkbox also pin to full.

The `[skip-tests]` PR title override (forces noop) was kept.

Branch protection still uses a single required check named
`All checks passed`, which is a final aggregator job with one bash
script that checks the tier appropriate set of upstream jobs.

Additional changes bundled into the same PR because they only made
sense once the tier topology was in place:

- Sonar runs inline inside `jvm-tests` on the cheap tier so it does
  not pay for a separate runner allocation, JDK setup, Gradle init,
  Kotlin recompile, and coverage artifact download. Kover writes the
  XML to the same Gradle build directory, so no artifact handoff is
  needed in process. Standalone `sonar-analysis` is now full tier only.
- `jvm-tests` checkout uses `fetch-depth: 0` so the inline Sonar step
  can compute git blame for new code coverage.
- `jvm-tests` is the designated Gradle home cache writer
  (`cache-read-only: false`); the parallel jobs use
  `cache-read-only: true` to remove cache save contention.
- The master push trigger was removed from the workflow. Direct pushes
  to master are not part of the workflow (PRs only), and the daily cron
  takes over baseline drift detection.
- `update_properties.sh` got the executable bit in the git index so
  the repeated `chmod +x` step was removed from every job.

Three things that were tried and reverted:

- Collapsing the 4-shard Android matrix to a single emulator. The
  codebase has 132 instrumented tests; the single emulator hangs at
  test 31 (related to `TestStockfishEvaluator`, tracked in
  issue #138). The matrix stays at 4.
- Using `-no-snapshot-save` to actually load the cached AVD snapshot.
  The snapshot saved by the create AVD step is incompatible with the
  test run step ("Failed to load snapshot 'default_boot'"), so the
  emulator cold boots either way. Kept `-no-snapshot`.
- Removing the workflow level `GITHUB_ACTOR` / `GITHUB_TOKEN` env. It
  is consumed by `settings.gradle.kts` to authenticate against the
  Stockfish-Multiplatform GitHub Packages repository.

## Consequences

Verified wall times on this PR:

| Tier  | Wall  | Triggered by                                |
|-------|-------|---------------------------------------------|
| noop  | 23s   | README only, doc only PRs                   |
| cheap | 2m39s | `commonMain` and `jvmMain` and test changes |
| full  | ~9m   | platform code or build config changes       |

The most frequent PRs (refactor in `commonMain`, JVM bug fix, business
logic change in `commonTest`) drop from 8 minutes to under 3 minutes.
Doc only PRs drop to under 30 seconds. Platform changes pay the same
cost as before, which is correct.

Trade offs:

- The detect logic adds about 10 seconds of overhead to every run, and
  a wrong filter rule risks classifying a change as cheap when it is
  actually platform sensitive. The `compile-multiplat` job on the
  cheap tier is the safety net for `expect`/`actual` regressions, but
  it does not run UI or instrumented tests. A behavioural Android only
  regression in `commonMain` code would not be caught until master.
- Self editing the workflow forces full tier because
  `.github/workflows/check.yml` is in the `platform` filter. Safe
  default.
- The cheap tier no longer reuses the standalone `sonar-analysis` job,
  so two Sonar call sites have to stay aligned (one inside
  `jvm-tests`, one in `sonar-analysis`). Both pass the same
  `--no-configuration-cache --no-parallel --no-daemon` flags.
- Removing the master push trigger means a doc only commit straight to
  master will not refresh the Sonar baseline. The daily cron covers
  that within 24 hours.

## Links

- PR #137: implementation
- Issue #138: flaky `TestStockfishEvaluator.newEvaluationResetsPreviousResults`
- Reference timings: runs 26174693739 (noop), 26175554210 (cheap),
  26174080397 (full)
