import io.github.tabilzad.ktor.model.SecurityScheme

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ktor)
  alias(libs.plugins.kotlinX.serialization.plugin)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.inspektor)
}

application { mainClass = "io.ktor.server.netty.EngineMain" }

// Code formatting configuration
ktfmt { googleStyle() }

dependencies {
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.auth.jwt)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.exposed.core)
  implementation(libs.exposed.jdbc)
  implementation(libs.postgresql)
  implementation(libs.ktor.server.netty)
  implementation(libs.logback.classic)
  implementation(libs.ktor.server.config.yaml)
  implementation(libs.ktor.client.apache)
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.kotlin.test.junit)
  implementation(libs.ktor.server.swagger)
  implementation(libs.ktor.server.openapi)
}

swagger {
  documentation {
    info {
      title = "MemorChess Server API"
      description = "API documentation for the MemorChess server."
      version = "0.0.1"
      contact {
        name = "Axl-Lvy"
        email = "axllvydev@gmail.com"
        url = "https://axl-lvy.fr"
      }
    }

    security {
      schemes {
        "basicAuth" to SecurityScheme(type = "http", scheme = "basic")
        "formAuth" to SecurityScheme(type = "apiKey", `in` = "cookie", name = "SESSION")
      }
      scopes {
        or { +"basicAuth" }
        or { +"formAuth" }
      }
    }
  }

  pluginOptions { format = "yaml" }
}
