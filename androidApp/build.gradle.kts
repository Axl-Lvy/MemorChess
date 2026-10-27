import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ktfmt)
  id("jacoco")
}

jacoco { toolVersion = "0.8.15" }

android {
  namespace = "proj.memorchess.axl"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "proj.memorchess.axl"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 1
    versionName = "0.0.1"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  buildTypes {
    getByName("release") { isMinifyEnabled = false }
    getByName("debug") { enableAndroidTestCoverage = true }
    // Measured by :macrobenchmark, which rejects debuggable targets. Debug signed so it installs
    // on any device.
    create("benchmark") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
      isDebuggable = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

ktfmt { googleStyle() }

// The ktfmt plugin does not detect source sets of AGP 9's built-in Kotlin support.
val ktfmtFormatAndroidSources =
  tasks.register<KtfmtFormatTask>("ktfmtFormatAndroidSources") {
    source = fileTree("src") { include("**/*.kt") }
  }

val ktfmtCheckAndroidSources =
  tasks.register<KtfmtCheckTask>("ktfmtCheckAndroidSources") {
    source = fileTree("src") { include("**/*.kt") }
  }

tasks.named("ktfmtFormat") { dependsOn(ktfmtFormatAndroidSources) }

tasks.named("ktfmtCheck") { dependsOn(ktfmtCheckAndroidSources) }

tasks.register<JacocoReport>("jacocoAndroidTestReport") {
  dependsOn("compileDebugKotlin")
  dependsOn(":composeApp:compileAndroidMain")
  dependsOn(":shared:compileAndroidMain")
  group = "verification"
  description =
    "Generate test coverage reports for Android instrumented tests across androidApp, composeApp and shared"

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

  val appClasses =
    fileTree(
      "${layout.buildDirectory.get()}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
    ) {
      exclude(fileFilter)
    }
  val libraryClasses =
    fileTree("${rootProject.projectDir}/composeApp/build/classes/kotlin/android/main") {
      exclude(fileFilter)
    }
  val sharedClasses =
    fileTree("${rootProject.projectDir}/shared/build/classes/kotlin/android/main") {
      exclude(fileFilter)
    }

  val composeAppSrc = "${rootProject.projectDir}/composeApp/src"

  sourceDirectories.setFrom(
    files(
      "$composeAppSrc/commonMain/kotlin",
      "$composeAppSrc/androidMain/kotlin",
      "$composeAppSrc/nonJsMain/kotlin",
      "$composeAppSrc/debugMain/kotlin",
      "${project.projectDir}/src/main/kotlin",
      "${rootProject.projectDir}/shared/src/commonMain/kotlin",
    )
  )
  classDirectories.setFrom(files(appClasses, libraryClasses, sharedClasses))

  executionData.setFrom(
    fileTree(layout.buildDirectory.get()) { include("outputs/code_coverage/**/*.ec") }
  )

  onlyIf { executionData.files.any { it.exists() } }
}

dependencies {
  implementation(project(":composeApp"))
  implementation(libs.compose.runtime)
  implementation(libs.compose.foundation)
  implementation(libs.compose.ui)
  implementation(libs.androidx.activity.compose)
  implementation(libs.filekit.dialogs.compose)

  // Per composable trace sections for the :macrobenchmark TraceSectionMetric.
  "benchmarkImplementation"(libs.compose.runtime.tracing)

  // Without it the benchmark library sideloads an older Perfetto binary that the app's
  // tracing-perfetto runtime rejects with a checksum error.
  "benchmarkImplementation"(libs.androidx.tracing.perfetto.binary)

  androidTestImplementation(libs.kotlin.test.junit)
  androidTestImplementation(libs.androidx.room.runtime)
  androidTestImplementation(libs.androidx.ui.test.junit4.android)
  androidTestImplementation(libs.androidx.espresso.intents)
  debugImplementation(libs.androidx.ui.test.manifest)
}
