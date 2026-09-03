import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.kover)
}

kotlin {
  androidLibrary {
    namespace = "proj.memorchess.axl.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
  }

  jvm()

  iosArm64()
  iosSimulatorArm64()

  @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }

  sourceSets {
    commonMain.dependencies {
      // api, not implementation: GameEngine exposes chess-core types in its signatures.
      api(libs.chess.core)
      implementation(libs.kermit.logging)
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotest.assertions)
    }

    sourceSets.all { languageSettings.optIn("kotlin.time.ExperimentalTime") }
  }
}

ktfmt { googleStyle() }
