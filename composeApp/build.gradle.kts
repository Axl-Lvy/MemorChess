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
}

kotlin {
  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
  }

  listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  jvm("desktop")

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    moduleName = "composeApp"
    browser {
      val rootDirPath = project.rootDir.path
      val projectDirPath = project.projectDir.path
      commonWebpackConfig {
        outputFileName = "composeApp.js"
        devServer =
          (devServer ?: KotlinWebpackConfig.DevServer()).apply {
            static =
              (static ?: mutableListOf()).apply {
                // Serve sources to debug inside browser
                add(rootDirPath)
                add(projectDirPath)
              }
          }
      }
    }
    binaries.executable()
  }

  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    // create a new group that
    // depends on `common`
    common {
      // Define group name without
      // `Main` as suffix
      group("nonJs") {
        withAndroidTarget()
        withJvm()
        group("ios") { withIos() }
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.ui)
      implementation(compose.components.resources)
      implementation(compose.components.uiToolingPreview)
      implementation(libs.androidx.lifecycle.viewmodel)
      implementation(libs.androidx.lifecycle.runtime.compose)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.navigation.compose)
      implementation(libs.material.icons)
      api(libs.logging)
      implementation(libs.xfeather.z)
      implementation(libs.multiplatform.settings)
      implementation(libs.supabase.database)
      implementation(libs.supabase.auth)
      implementation(libs.ktor.client.core)
    }
    jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
    androidMain.dependencies {
      implementation(compose.preview)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.material3.android)
      implementation(libs.ktor.client.okhttp)
    }
    val desktopTest by getting
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class) implementation(compose.uiTest)
    }
    // Adds the desktop test dependency
    desktopTest.dependencies { implementation(compose.desktop.currentOs) }
    val nonJsMain by getting {
      dependencies {
        implementation(libs.androidx.room.runtime)
        implementation(libs.androidx.room.compiler)
        implementation(libs.sqlite.bundled)
      }
      configurations { implementation { exclude(group = "org.jetbrains", module = "annotations") } }
    }
    iosMain.dependencies { implementation(libs.ktor.client.darwin) }
    wasmJsMain.dependencies { implementation(libs.ktor.client.js) }
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

ktfmt { googleStyle() }

room { schemaDirectory("$projectDir/schemas") }

dependencies {
  // Android
  add("kspAndroid", libs.androidx.room.compiler)
  // iOS
  add("kspIosSimulatorArm64", libs.androidx.room.compiler)
  add("kspIosX64", libs.androidx.room.compiler)
  add("kspIosArm64", libs.androidx.room.compiler)

  androidTestImplementation(libs.androidx.ui.test.junit4.android)
  debugImplementation(libs.androidx.ui.test.manifest)
}
