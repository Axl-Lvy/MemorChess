import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinX.serialization.plugin)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.room)
  alias(libs.plugins.ksp)
  alias(libs.plugins.composeHotReload)
  alias(libs.plugins.kover)
  id("jacoco")
}

jacoco { toolVersion = "0.8.15" }

kotlin {
  // Android configuration
  androidLibrary {
    namespace = "proj.memorchess.axl.library"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    // Package Compose Multiplatform resources into the AAR (CMP-9547). Without this the
    // resources are missing from the final APK and the app crashes at runtime.
    androidResources.enable = true
  }

  // JVM/Desktop configuration
  jvm()

  // iOS configuration
  listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  // WebAssembly configuration
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    outputModuleName = "composeApp"
    browser {
      commonWebpackConfig {
        outputFileName = "composeApp.js"
        devServer =
          (devServer ?: KotlinWebpackConfig.DevServer()).apply {
            // Serve sources to debug inside browser
            static(project.rootDir.path)
            static(project.projectDir.path)
          }
      }
      testTask { useKarma { useFirefoxHeadless() } }
    }
    binaries.executable()
  }

  // Hierarchy template configuration. withAndroidTarget() only matches the legacy
  // KotlinAndroidTarget, so the new AGP provided androidLibrary target is matched by
  // platform type instead.
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    common {
      group("debug") {
        withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
        withJvm()
      }
      group("nonJs") {
        withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
        withJvm()
        group("ios") { withIos() }
      }
    }
  }

  // Source sets configuration
  sourceSets {
    commonMain.dependencies {
      // Compose dependencies
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.components.ui.tooling.preview)

      // Lifecycle dependencies
      implementation(libs.androidx.lifecycle.viewmodel)
      implementation(libs.androidx.lifecycle.runtime.compose)

      // Kotlin extensions
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization.json)

      // Navigation and UI
      implementation(libs.navigation.compose)
      implementation(libs.material.icons)
      implementation(libs.xfeather.z)
      implementation(libs.material3.adaptive)

      // Chess engine
      implementation(libs.chess.core)
      implementation(libs.stockfish.multiplatform)

      // File picker
      implementation(libs.filekit.dialogs.compose)

      // Utilities
      implementation(libs.multiplatform.settings)
      implementation(libs.kermit.logging)

      // Dependency injection
      implementation(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.compose.viewmodel.navigation)

      // Networking
      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.kotlinx.json)
    }

    // Debug source set configuration
    val debugMain by getting {
      dependencies {
        // Hot Preview
        implementation(libs.hotpreview)
      }
    }

    @SuppressWarnings("unused")
    val nonJsMain by getting {
      dependencies {
        implementation(libs.androidx.room.runtime)
        implementation(libs.sqlite.bundled)
      }
      configurations.named("nonJsMainImplementation") {
        exclude(group = "org.jetbrains", module = "annotations")
      }
    }

    androidMain.dependencies {
      implementation(libs.compose.ui.tooling.preview)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.material3.android)
      implementation(libs.androidx.browser)
      implementation(libs.ktor.client.cio)
    }

    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.slf4j.api)
      implementation(libs.ktor.client.cio)
    }

    iosMain.dependencies { implementation(libs.ktor.client.darwin) }

    wasmJsMain.dependencies {
      implementation(libs.indexeddb)
      implementation(libs.ktor.client.js)
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.compose.ui.test)
      implementation(libs.kotest.assertions)
      implementation(libs.ktor.client.mock)
    }

    sourceSets.all {
      languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
      languageSettings.optIn("kotlin.time.ExperimentalTime")
    }
  }

  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}

// Code formatting configuration
ktfmt { googleStyle() }

tasks.withType<KtfmtBaseTask> { exclude("**/generated/**") }

// Room database configuration
room { schemaDirectory("$projectDir/schemas") }

dependencies {
  // KSP processors for Room on different platforms
  add("kspAndroid", libs.androidx.room.compiler)
  add("kspIosSimulatorArm64", libs.androidx.room.compiler)
  add("kspIosArm64", libs.androidx.room.compiler)
  add("kspJvm", libs.androidx.room.compiler)
}

tasks.withType<ComposeHotRun>().configureEach { mainClass = "proj.memorchess.axl.MainKt" }

// Disable the jacoco plugin agent on JVM tests — Kover attaches its own JaCoCo agent via
// useJacoco(), and two JaCoCo agents on the same JVM crash at class definition time.
tasks.named<Test>("jvmTest") { extensions.configure<JacocoTaskExtension> { isEnabled = false } }

// Disable configuration cache for all tasks involving wasmJs
tasks.withType<KotlinWebpack>().configureEach {
  notCompatibleWithConfigurationCache("Kotlin/JS Webpack tasks store Project references")
}

tasks.withType<KotlinJsTest>().configureEach {
  notCompatibleWithConfigurationCache(
    "Kotlin/JS testing tasks store Project references and use non-serializable types."
  )
}

// Listing the source sets explicitly disables the scanner's Kotlin Multiplatform
// auto-detection, which keeps the indexed directories under our control after the
// androidApp module split.
extensions.configure<org.sonarqube.gradle.SonarExtension> {
  properties {
    property(
      "sonar.sources",
      listOf(
          "src/androidMain/kotlin",
          "src/commonMain/kotlin",
          "src/debugMain/kotlin",
          "src/iosMain/kotlin",
          "src/jvmMain/kotlin",
          "src/nonJsMain/kotlin",
          "src/wasmJsMain/kotlin",
        )
        .joinToString(","),
    )
    property("sonar.tests", listOf("src/commonTest/kotlin", "src/jvmTest/kotlin").joinToString(","))
  }
}

kover {
  useJacoco("0.8.14")

  reports {
    filters {
      excludes {
        files(
          "**/R.class",
          "**/Res.class",
          "**/BuildConfig.*",
          "**/Manifest*.*",
          "**/*Test*.*",
          "**/di/**",
          "**/generated/**",
        )
      }
    }
  }
}
