plugins {
  alias(libs.plugins.ktorPlugin)
  alias(libs.plugins.kotlinX.serialization.plugin)
  alias(libs.plugins.ktfmt)
  kotlin("jvm")
}

application { mainClass.set("proj.memorchess.server.ApplicationKt") }

dependencies {
  implementation(projects.shared)

  // Ktor server
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.resources)
  implementation(libs.ktor.serialization.kotlinx.json)

  // Database
  implementation(libs.exposed.core)
  implementation(libs.exposed.dao)
  implementation(libs.exposed.jdbc)
  implementation(libs.exposed.kotlin.datetime)
  implementation(libs.kotlinx.datetime)
  implementation(libs.hikaricp)
  implementation(libs.flyway.core)
  implementation(libs.flyway.database.postgresql)
  implementation(libs.postgresql)

  // Logging
  implementation(libs.logback.classic)

  // Testing
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.testcontainers)
  testImplementation(libs.testcontainers.postgresql)
}

ktfmt { googleStyle() }
