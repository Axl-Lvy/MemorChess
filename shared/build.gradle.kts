@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.kotlinX.serialization.plugin)
  alias(libs.plugins.ktfmt)
}

// Code formatting configuration
ktfmt { googleStyle() }

android {
  namespace = "proj.memorchess.axl.shared"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  jvmToolchain(17)

  // Android target for mobile app
  androidTarget {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
  }

  // JVM target for server
  jvm()

  // iOS targets for iOS app
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  // WebAssembly target for web app
  wasmJs { browser() }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.datetime)
      implementation(libs.ktor.client.resource)
      implementation(libs.ktor.server.resource)
    }

    commonTest.dependencies { implementation(libs.kotlin.test) }
  }
}
