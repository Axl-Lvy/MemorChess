import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.kotlinxBenchmark)
  alias(libs.plugins.kotlinAllopen)
  alias(libs.plugins.ktfmt)
}

// JMH requires benchmark state classes to be open; the Kotlin allopen plugin lifts the
// restriction for every class annotated with JMH's @State.
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
      // Small but stable run: enough iterations for JMH to settle on the hot path while
      // keeping the whole suite well under ten minutes.
      warmups = 3
      iterations = 5
      iterationTime = 500
      iterationTimeUnit = "ms"
      // JSON keeps the results machine readable so CI can merge them with the
      // macrobenchmark output into a single artifact.
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

// Benchmarks are not application code: keep them out of the Sonar analysis. Empty values
// also stop the root configuration, which lists composeApp source sets that do not exist
// here, from being inherited.
extensions.configure<org.sonarqube.gradle.SonarExtension> {
  properties {
    property("sonar.sources", "")
    property("sonar.tests", "")
  }
}

dependencies {
  implementation(projects.composeApp)
  implementation(libs.kotlinx.benchmark.runtime)
  // TreeStore's mutation API is suspending; benchmarks bridge it with runBlocking.
  implementation(libs.kotlinx.coroutines.core)
}
