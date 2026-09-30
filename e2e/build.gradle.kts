import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.ktfmt)
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
  testImplementation(projects.composeApp)
  testImplementation(testFixtures(projects.server))
  testImplementation(projects.shared)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.ktor.client.core)
  testImplementation(libs.ktor.client.cio)
  testImplementation(libs.ktor.client.content.negotiation)
  testImplementation(libs.ktor.serialization.kotlinx.json)
  // Testcontainers logs its Docker discovery through slf4j. Without a provider the reason a
  // container fails to start is swallowed.
  testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
  // Same Docker API floor as :server's tests, for the same reason: see server/build.gradle.kts.
  systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.40")
}

ktfmt { googleStyle() }
