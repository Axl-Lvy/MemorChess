rootProject.name = "MemorChess"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val localProperties = java.util.Properties().apply {
  val file = file("local.properties")
  if (file.exists()) file.inputStream().use { load(it) }
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenLocal()
    mavenCentral()
    maven {
      url = uri("https://maven.pkg.github.com/Axl-Lvy/Stockfish-Multiplatform")
      credentials {
        username = System.getenv("GITHUB_ACTOR")
          ?: localProperties.getProperty(".GITHUB_ACTOR", "")
        password = System.getenv("GITHUB_TOKEN")
          ?: localProperties.getProperty(".GITHUB_TOKEN", "")
      }
    }
  }
}

include(":composeApp")
