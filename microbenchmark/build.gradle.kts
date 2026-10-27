import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.kotlinxBenchmark)
  alias(libs.plugins.kotlinAllopen)
  alias(libs.plugins.ktfmt)
}

// JMH requires @State classes to be open.
allOpen { annotation("org.openjdk.jmh.annotations.State") }

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
    // Edge and CardState expose kotlin.time.Instant in their public API.
    optIn.add("kotlin.time.ExperimentalTime")
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

benchmark {
  targets { register("main") }

  configurations {
    named("main") {
      warmups = 3
      iterations = 5
      iterationTime = 500
      iterationTimeUnit = "ms"
      // CI merges the JSON with the macrobenchmark output.
      reportFormat = "json"
      advanced("jvmForks", "1")
    }

    // Minimal run proving every benchmark still executes; numbers are meaningless.
    register("smoke") {
      warmups = 1
      iterations = 1
      iterationTime = 100
      iterationTimeUnit = "ms"
      reportFormat = "json"
      advanced("jvmForks", "1")
    }
  }
}

ktfmt { googleStyle() }

dependencies {
  implementation(projects.composeApp)
  implementation(libs.kotlinx.benchmark.runtime)
  // TreeStore's mutation API is suspending; benchmarks bridge it with runBlocking.
  implementation(libs.kotlinx.coroutines.core)
}
