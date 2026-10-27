import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.kotlinX.serialization.plugin)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.kover)
  application
  `java-test-fixtures`
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

application { mainClass.set("proj.memorchess.axl.server.MainKt") }

dependencies {
  implementation(projects.shared)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.krontab)
  implementation(libs.postgresql)
  implementation(libs.hikari)
  implementation(libs.slf4j.api)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.server.rate.limit)
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.server.auth.jwt)
  implementation(libs.ktor.serialization.kotlinx.json)
  // Not transitive from :shared, and SYNC_JSON exposes a Json in this module's signatures.
  implementation(libs.kotlinx.serialization.json)
  runtimeOnly(libs.slf4j.simple)

  // java-test-fixtures exposes only main's api dependencies to the fixtures, and everything above
  // is implementation, so the fixtures declare what they compile against themselves.
  testFixturesImplementation(projects.shared)
  testFixturesImplementation(libs.kotlinx.coroutines.core)
  testFixturesImplementation(libs.hikari)
  testFixturesImplementation(libs.testcontainers.postgresql)
  testFixturesImplementation(libs.ktor.server.core)
  testFixturesImplementation(libs.ktor.server.netty)
  // Brings com.auth0 java-jwt and jwks-rsa, which TestJwks signs and publishes keys with.
  testFixturesImplementation(libs.ktor.server.auth.jwt)

  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.ktor.server.test.host)
  // The test HTTP client needs its own ContentNegotiation; the server one is not on its classpath.
  testImplementation(libs.ktor.client.content.negotiation)
  // Without an slf4j provider, the reason a Testcontainers container fails to start is swallowed.
  testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
  // Testcontainers' docker-java negotiates Docker API 1.32, below Docker 29's minimum of 1.40.
  systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.40")
}

// buildSha stays local to this block: a script-level val referenced by filesMatching captures the
// whole script object, which the configuration cache cannot serialize.
tasks.processResources {
  val buildSha = providers.gradleProperty("buildSha").orElse("dev")
  inputs.property("buildSha", buildSha)
  filesMatching("build-info.properties") { expand("buildSha" to buildSha.get()) }
}

ktfmt { googleStyle() }

// Match the engine composeApp and shared use, or Kover refuses to merge the reports. Fixtures are
// test code, but Kover only excludes the compilation named test by default.
kover {
  useJacoco("0.8.14")
  currentProject { sources { excludedSourceSets.add("testFixtures") } }
}
