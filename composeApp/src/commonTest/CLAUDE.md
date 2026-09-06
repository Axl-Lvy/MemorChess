# commonTest

Shared test source set, compiled for every target including iOS.

- **No commas in backtick test names.** Kotlin/Native derives the iOS test's Objective-C selector
  from the Kotlin function name, and a comma there fails `compileTestKotlinIosSimulatorArm64` with
  "Name contains illegal characters". JVM and wasmJs compile the same name fine, so the break only
  shows up in the iOS CI job.
