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

application {
  // The Docker image in the deployment step invokes this through installDist's start script.
  mainClass.set("proj.memorchess.axl.server.MainKt")
}

dependencies {
  implementation(projects.shared)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.krontab)
  implementation(libs.postgresql)
  implementation(libs.hikari)
  implementation(libs.awssdk.s3)
  implementation(libs.slf4j.api)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.server.rate.limit)
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.server.auth.jwt)
  implementation(libs.ktor.serialization.kotlinx.json)
  // :shared declares kotlinx.serialization as implementation, so it is not on this module's
  // compile classpath transitively, and SYNC_JSON is a Json in this module's signatures.
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
  // The test HTTP client needs its own ContentNegotiation to decode responses; the server side
  // declaration above does not put it on the client's classpath.
  testImplementation(libs.ktor.client.content.negotiation)
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

// Bakes the build sha into build-info.properties, read by BuildInfo at runtime and served by
// GET /v1/version. The Docker image build passes -PbuildSha=<short sha>. Anywhere else (local
// builds, tests) it falls back to "dev". buildSha is a local val inside the task's own
// configuration block, not a script-level property, because a filesMatching action referencing a
// script-level val captures the whole script object, which the configuration cache refuses to
// serialize.
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
