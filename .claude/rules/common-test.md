---
paths:
  - "**/src/commonTest/**"
---

# commonTest

No commas in backtick test names. Kotlin/Native turns the name into an Objective-C selector and fails `compileTestKotlinIosSimulatorArm64` with "Name contains illegal characters". JVM and wasmJs accept it, so only the iOS CI job breaks.
