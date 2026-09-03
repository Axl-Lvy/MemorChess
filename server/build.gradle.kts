import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.kover)
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
    // The sync row types expose kotlin.time.Instant in their public API.
    optIn.add("kotlin.time.ExperimentalTime")
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
  implementation(projects.shared)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.postgresql)
  implementation(libs.hikari)
  implementation(libs.slf4j.api)

  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.kotlinx.coroutines.test)
  // Testcontainers logs its Docker discovery through slf4j; without a provider the reason a
  // container fails to start is swallowed.
  testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
  // Testcontainers 1.21.3 bundles a docker-java that negotiates Docker API 1.32, which Docker 29
  // refuses outright: its minimum is 1.40. Without this every container test dies with
  // "Could not find a valid Docker environment", whose real cause is only visible once an slf4j
  // provider is on the test classpath. 1.40 has been supported since Docker 19.03, so it is a
  // safe floor. Left overridable for anyone who needs a different one.
  systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.40")
}

ktfmt { googleStyle() }

// Match the engine composeApp and shared use, or Kover refuses to merge the reports.
kover { useJacoco("0.8.14") }
