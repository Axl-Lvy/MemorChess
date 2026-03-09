import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidApplication)
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

kotlin {
  // Android configuration
  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
  }

  // JVM/Desktop configuration
  jvm()

  // iOS configuration
  listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
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
      commonWebpackConfig { outputFileName = "composeApp.js" }
      testTask { useKarma { useFirefoxHeadless() } }
    }
    binaries.executable()
  }

  // Hierarchy template configuration
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    common {
      group("debug") {
        withAndroidTarget()
        withJvm()
      }
      group("nonJs") {
        withAndroidTarget()
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
      implementation(libs.compose.components.uiToolingPreview)

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

      // File picker
      implementation(libs.filekit.dialogs.compose)

      // Utilities
      implementation(libs.multiplatform.settings)
      implementation(libs.kermit.logging)

      // Backend and networking
      implementation(libs.supabase.database)
      implementation(libs.supabase.auth)
      implementation(libs.ktor.client.core)

      // Dependency injection
      implementation(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.compose.viewmodel.navigation)
    }

    // Debug source set configuration
    named("debugMain") {
      dependencies {
        // Hot Preview
        implementation(libs.hotpreview)
      }
    }

    named("nonJsMain") {
      dependencies {
        implementation(libs.androidx.room.runtime)
        implementation(libs.sqlite.bundled)
      }
      configurations { implementation { exclude(group = "org.jetbrains", module = "annotations") } }
    }

    androidMain.dependencies {
      implementation(libs.compose.ui.tooling.preview)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.material3.android)
      implementation(libs.ktor.client.okhttp)
    }

    jvmMain.dependencies {
      @Suppress("DEPRECATION") implementation(compose.desktop.currentOs)
      implementation(libs.ktor.client.java)
      implementation(libs.slf4j.api)
    }

    iosMain.dependencies { implementation(libs.ktor.client.darwin) }

    wasmJsMain.dependencies { implementation(libs.indexeddb) }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.compose.ui.test)
      implementation(libs.kotest.assertions)
    }

    sourceSets.all {
      languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
      languageSettings.optIn("kotlin.time.ExperimentalTime")
    }
  }

  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}

android {
  namespace = "proj.memorchess.axl"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "proj.memorchess.axl"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testInstrumentationRunnerArguments["numShards"] =
      project.findProperty("numShards")?.toString() ?: "1"
    testInstrumentationRunnerArguments["shardIndex"] =
      project.findProperty("shardIndex")?.toString() ?: "0"
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  buildTypes {
    getByName("release") { isMinifyEnabled = false }
    getByName("debug") { enableAndroidTestCoverage = true }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
    unitTests.isIncludeAndroidResources = true
  }
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
  add("kspIosX64", libs.androidx.room.compiler)
  add("kspIosArm64", libs.androidx.room.compiler)
  add("kspJvm", libs.androidx.room.compiler)

  // Test dependencies
  androidTestImplementation(libs.androidx.ui.test.junit4.android)
  debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.withType<ComposeHotRun>().configureEach { mainClass = "proj.memorchess.axl.MainKt" }

// Disable configuration cache for all tasks involving wasmJs
tasks.withType<KotlinWebpack>().configureEach {
  notCompatibleWithConfigurationCache("Kotlin/JS Webpack tasks store Project references")
}

tasks.withType<KotlinJsTest>().configureEach {
  notCompatibleWithConfigurationCache(
    "Kotlin/JS testing tasks store Project references and use non-serializable types."
  )
}

tasks.register<JacocoReport>("jacocoAndroidTestReport") {
  dependsOn("compileDebugKotlinAndroid")
  group = "verification"
  description = "Generate test coverage reports for Android instrumented tests"

  reports {
    xml.required.set(true)
    html.required.set(true)
  }

  val fileFilter =
    listOf(
      "**/R.class",
      "**/Res.class",
      "**/BuildConfig.*",
      "**/Manifest*.*",
      "**/*Test*.*",
      "android/**/*.*",
      "**/generated/**",
      "**/build/**",
      "**/tmp/**",
    )

  val debugTree =
    fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") { exclude(fileFilter) }

  val mainSrc = "${project.projectDir}/src/commonMain/kotlin"
  val androidSrc = "${project.projectDir}/src/androidMain/kotlin"
  val nonJsSrc = "${project.projectDir}/src/jsMain/kotlin"
  val jvmSrc = "${project.projectDir}/src/jvmMain/kotlin"

  sourceDirectories.setFrom(files(listOf(mainSrc, androidSrc, nonJsSrc, jvmSrc)))
  classDirectories.setFrom(files(listOf(debugTree)))

  // Use both possible locations for coverage execution data
  executionData.setFrom(
    fileTree(layout.buildDirectory.get()) { include("outputs/code_coverage/**/*.ec") }
  )

  // Only run if coverage data exists
  onlyIf { executionData.files.any { it.exists() } }
}

kover {
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
          "**/ui/**",
        )
      }
    }
  }
}

apply(from = "pre-compile.gradle.kts")
