import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import java.util.Properties
import kotlin.apply
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
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
  id("secrets-generation")
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

      // Hot Preview
      implementation(libs.hotpreview)
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

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class) implementation(compose.uiTest)
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

    iosMain.dependencies { implementation(libs.ktor.client.darwin) }
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
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  buildTypes { getByName("release") { isMinifyEnabled = false } }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions { unitTests.isReturnDefaultValues = true }
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

val applySupabaseFunctions by
  tasks.registering {
    val supabaseDbLink = createSupabaseDbLink()
    if (supabaseDbLink.isEmpty()) {
      logger.error(
        "Could not create Supabase database link. Please check your local.properties file."
      )
      return@registering
    }

    val sqlDir = file("../supabase/functions")
    val sqlFiles = sqlDir.listFiles()?.sortedBy { it.name } ?: emptyList()

    if (sqlFiles.isEmpty()) {
      logger.lifecycle("No SQL files found in the directory: ${sqlDir.absolutePath}")
      return@registering
    }

    doFirst {
      logger.lifecycle("Applying ${sqlFiles.size} SQL functions to Supabase database...")
      sqlFiles.forEach { file -> logger.lifecycle("  - ${file.name}") }
    }

    doLast {
      sqlFiles.forEach { sqlFile ->
        val processBuilder =
          ProcessBuilder("psql", "-f", sqlFile.absolutePath, supabaseDbLink).apply {
            directory(sqlDir)
            redirectErrorStream(false)
          }
        val process = processBuilder.start()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
          logger.lifecycle("Successfully applied: ${sqlFile.name}")
        } else {
          logger.warn("Failed to apply: ${sqlFile.name} (exit code: $exitCode)")
        }
      }
      logger.lifecycle("Finished applying SQL functions to database.")
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

tasks
  .matching { it.name.contains("compile", ignoreCase = true) }
  .configureEach { dependsOn(applySupabaseFunctions) }

tasks.withType<ComposeHotRun>().configureEach { mainClass = "proj.memorchess.axl.MainKt" }
