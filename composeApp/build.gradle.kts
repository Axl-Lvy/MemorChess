import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import java.util.Properties
import kotlin.apply
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

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
  id("jacoco")
}

kotlin {
  // Android configuration
  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
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
      commonWebpackConfig {
        outputFileName = "composeApp.js"
        devServer =
          (devServer ?: KotlinWebpackConfig.DevServer()).apply {
            static =
              (static ?: mutableListOf()).apply {
                // Serve sources to debug inside browser
                add(project.rootDir.path)
                add(project.projectDir.path)
              }
          }
      }
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
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.ui)
      implementation(compose.components.resources)
      implementation(compose.components.uiToolingPreview)

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

      // Utilities
      api(libs.logging)
      implementation(libs.multiplatform.settings)

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
        implementation(libs.androidx.room.compiler)
        implementation(libs.sqlite.bundled)
      }
      configurations { implementation { exclude(group = "org.jetbrains", module = "annotations") } }
    }

    androidMain.dependencies {
      implementation(compose.preview)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.material3.android)
      implementation(libs.ktor.client.okhttp)
    }

    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.ktor.client.java)
    }

    iosMain.dependencies { implementation(libs.ktor.client.darwin) }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class) implementation(compose.uiTest)
    }
  }
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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

// Task to apply Supabase SQL functions
// Gradle cache is used to avoid reapplying functions if they haven't changed
val applySupabaseFunctions by
  tasks.registering {
    val sqlDir = file("../supabase/functions")

    // Tell Gradle: these files are inputs
    inputs.files(sqlDir.listFiles() ?: emptyArray<File>())

    // Tell Gradle: this file is the "result" marker
    val outputMarker = layout.buildDirectory.file(".supabaseFunctionsApplied")
    outputs.file(outputMarker)
    val supabaseDbLink = createSupabaseDbLink()

    doLast {
      if (supabaseDbLink.isEmpty()) {
        logger.error(
          "Could not create Supabase database link. Please check your local.properties file."
        )
        return@doLast
      }

      val sqlFiles = sqlDir.listFiles()?.sortedBy { it.name } ?: emptyList()

      if (sqlFiles.isEmpty()) {
        logger.lifecycle("No SQL files found in the directory: ${sqlDir.absolutePath}")
        return@doLast
      }

      var allSucceeded = true

      sqlFiles.forEach { sqlFile ->
        val processBuilder =
          ProcessBuilder("psql", "-f", sqlFile.absolutePath, supabaseDbLink).apply {
            directory(sqlDir)
            redirectErrorStream(false)
          }

        val process = processBuilder.start()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
          logger.warn("Failed to apply: ${sqlFile.name} (exit code: $exitCode)")
          allSucceeded = false
        }
      }

      if (allSucceeded) {
        outputMarker.get().asFile.writeText("Last applied at ${System.currentTimeMillis()}")
        logger.lifecycle("Finished applying SQL functions to database.")
      } else {
        logger.warn("Some SQL files failed to apply.")
      }
    }
  }

private fun createSupabaseDbLink(): String {
  val localProperties =
    Properties().apply {
      val file = rootProject.file("local.properties")
      if (file.exists()) {
        file.inputStream().use { load(it) }
      } else {
        logger.error("local.properties file not found.")
        return ""
      }
    }
  val host = localProperties.getProperty(".supabase_db_host")
  val port = localProperties.getProperty(".supabase_db_port")
  val dbName = localProperties.getProperty(".supabase_db_name")
  val user = localProperties.getProperty(".supabase_db_user")
  val password = localProperties.getProperty(".supabase_db_password")
  if (
    host.isNullOrEmpty() ||
      port.isNullOrEmpty() ||
      dbName.isNullOrEmpty() ||
      user.isNullOrEmpty() ||
      password.isNullOrEmpty()
  ) {
    logger.error("One or more Supabase database properties are missing in local.properties.")
    return ""
  }
  return "postgresql://$user:$password@$host:$port/$dbName"
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

// Test coverage configuration
tasks.register<JacocoReport>("jacocoAndroidTestReport") {
  group = "verification"
  description = "Generate test coverage reports for Android instrumented tests"

  // Don't automatically run tests - only generate report from existing coverage data
  mustRunAfter("connectedDebugAndroidTest", "createDebugAndroidTestCoverageReport")

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

  sourceDirectories.setFrom(files(listOf(mainSrc, androidSrc)))
  classDirectories.setFrom(files(listOf(debugTree)))

  // Use both possible locations for coverage execution data
  executionData.setFrom(
    fileTree(layout.buildDirectory.get()) { include("outputs/code_coverage/**/*.ec") }
  )

  // Only run if coverage data exists
  onlyIf { executionData.files.any { it.exists() } }
}

tasks
  .matching { it.name.contains("compile", ignoreCase = true) }
  .configureEach { dependsOn(applySupabaseFunctions) }

apply(from = "secrets.gradle.kts")
