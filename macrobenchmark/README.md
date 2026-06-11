# Macrobenchmark

Automated UI performance measurements for the Android app, built on
[`androidx.benchmark.macro`](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).
The benchmarks drive the real app through UiAutomator and measure a build that behaves like a
release build, so the numbers are representative. Debug builds are roughly 3 to 5 times slower
because of JIT and runtime checks, which is exactly why these benchmarks exist.

## What is measured

| Benchmark                   | Metric                              | What it tells you                                                                                        |
|-----------------------------|-------------------------------------|----------------------------------------------------------------------------------------------------------|
| `ScreenNavigationBenchmark` | `FrameTimingMetric`                 | Frame durations and jank while cycling Explore, Training and Settings through the bottom navigation bar. |
| `BoardCompositionBenchmark` | `TraceSectionMetric("%BoardGrid%")` | Wall time spent composing the chess board when entering the Explore screen.                              |

## How it works

- `:androidApp` has a `benchmark` build type: initialized from `release`, signed with the debug
  key so it installs anywhere, explicitly not debuggable (Macrobenchmark refuses debuggable
  targets), and made profileable from the shell through the manifest overlay in
  `androidApp/src/benchmark/AndroidManifest.xml`.
- Board composition is measured without any marker code in the app. The benchmark build adds
  `androidx.compose.runtime:runtime-tracing` (see `benchmarkImplementation` in
  `androidApp/build.gradle.kts`), and this module sets the instrumentation argument
  `androidx.benchmark.fullTracing.enable=true`. Together they make the Compose runtime emit one
  Perfetto slice per composable, which `TraceSectionMetric` then aggregates. The
  `composeRuntimeTracing` version in `gradle/libs.versions.toml` must track the
  `androidx.compose.runtime` version that the Compose Multiplatform release resolves to,
  otherwise no slices are produced.
- UiAutomator finds the navigation items through the existing `bottom_navigation_bar_item_*`
  Compose test tags, exposed as resource ids by the `testTagsAsResourceId` semantics flag set in
  `MainActivity`.

## Running locally

On a physical device (preferred, the only honest numbers):

```sh
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

On an emulator the library refuses to run unless told otherwise, and results are only useful for
relative comparisons, never absolute values:

```sh
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

Results are printed in the test output and written as JSON under
`macrobenchmark/build/outputs/`, including captured Perfetto traces that can be opened in
[ui.perfetto.dev](https://ui.perfetto.dev).

## Running on the Gradle Managed Device (CI)

A managed virtual device `pixel6Api34` is defined in this module. CI (and anyone without a
device) can run:

```sh
./gradlew :macrobenchmark:pixel6Api34BenchmarkAndroidTest \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

The `benchmark.yml` workflow wires this up on GitHub Actions behind a manual trigger.

## Interpreting results

- Compare numbers from the same device only. Emulator runs depend on host load.
- `frameDurationCpuMs` percentiles (P50/P90/P95/P99) are the headline numbers for navigation.
- For `BoardCompositionBenchmark`, composition tracing itself adds a small overhead to every
  composable, so use its `FrameTimingMetric` values as relative indicators only;
  `ScreenNavigationBenchmark` is the clean frame timing source.
