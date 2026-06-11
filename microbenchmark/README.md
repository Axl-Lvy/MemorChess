# Microbenchmark

JVM microbenchmarks for the pure Kotlin core of `:composeApp`, built on
[kotlinx-benchmark](https://github.com/Kotlin/kotlinx-benchmark) (JMH under the hood). They
complement `:macrobenchmark`: that module measures the rendered Android UI, this one measures the
algorithms underneath it, with no device or emulator required.

## What is measured

| Benchmark                  | What it guards                                                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------|
| `GameEngineBenchmark`      | SAN move validation and FEN production while replaying opening lines, the per move hot path of the app. |
| `OpeningTreeBenchmark`     | Building the opening graph through `TreeStore` and the lookup path used on every navigation step.       |
| `Fsrs6SchedulingBenchmark` | A full FSRS 6 scheduling pass over a card batch covering every `ReviewGrade` and `CardPhase`.            |
| `FenBenchmark`             | Parsing cropped FENs into engines and cropping positions back into `PositionKey`s.                       |

All inputs are deterministic fixtures (`OpeningLines`); the FSRS fuzz source is a constant. Runs
are therefore comparable across machines, up to hardware differences.

## Running locally

```sh
./gradlew :microbenchmark:benchmark
```

The full suite takes a few minutes. For a quick check that every benchmark still executes (numbers
are meaningless at these settings):

```sh
./gradlew :microbenchmark:mainSmokeBenchmark
```

## Reading the output

JMH reports throughput in `ops/s`: one op is one whole benchmark method invocation, for example
replaying all sixteen fixture lines, not a single move. Higher is better. The console prints a
summary table; machine readable JSON lands under
`microbenchmark/build/reports/benchmarks/<configuration>/<timestamp>/main.json` with `score`,
`scoreError` and percentile fields per benchmark. Compare the `score` of the same benchmark across
runs and treat differences within `scoreError` as noise.

## Caveat: JVM numbers are not Android numbers

These benchmarks run on a desktop JVM (HotSpot with C2). The app ships on ART, a different runtime
with a different compiler, allocator and GC. Absolute values do not transfer. What does transfer is
algorithmic complexity: a change that makes graph construction quadratic or doubles the work per
SAN move shows up here regardless of runtime. Use this module to catch algorithmic regressions in
core logic; use `:macrobenchmark` for realistic on device UI performance.

## CI

The manually triggered `benchmark.yml` workflow runs this suite in a `microbenchmark` job, in
parallel with the `macrobenchmark` job, then merges both JSON result sets into a single
`benchmark-results` artifact with one subdirectory per module.
