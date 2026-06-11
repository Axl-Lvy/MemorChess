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
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

ktfmt { googleStyle() }

// The ktfmt plugin does not detect source sets compiled by the Kotlin support built into
// AGP 9, so the Kotlin sources of this module are wired up manually.
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
  group = "verification"
  description =
    "Generate test coverage reports for Android instrumented tests across androidApp and composeApp"

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

  val composeAppSrc = "${rootProject.projectDir}/composeApp/src"

  sourceDirectories.setFrom(
    files(
      "$composeAppSrc/commonMain/kotlin",
      "$composeAppSrc/androidMain/kotlin",
      "$composeAppSrc/nonJsMain/kotlin",
      "$composeAppSrc/debugMain/kotlin",
      "${project.projectDir}/src/main/kotlin",
    )
  )
  classDirectories.setFrom(files(appClasses, libraryClasses))

  // Use both possible locations for coverage execution data
  executionData.setFrom(
    fileTree(layout.buildDirectory.get()) { include("outputs/code_coverage/**/*.ec") }
  )

  // Only run if coverage data exists
  onlyIf { executionData.files.any { it.exists() } }
}

// The scanner's Android detection already contributes src/main and src/androidTest for
// this module, so listing them explicitly would index every file twice and abort the
// analysis. Empty values also stop the root configuration, which lists composeApp source
// sets that do not exist here, from being inherited.
extensions.configure<org.sonarqube.gradle.SonarExtension> {
  properties {
    property("sonar.sources", "")
    property("sonar.tests", "")
  }
}

dependencies {
  implementation(project(":composeApp"))
  implementation(libs.compose.runtime)
  implementation(libs.androidx.activity.compose)
  implementation(libs.filekit.dialogs.compose)

  androidTestImplementation(libs.kotlin.test.junit)
  androidTestImplementation(libs.androidx.room.runtime)
  androidTestImplementation(libs.androidx.ui.test.junit4.android)
  androidTestImplementation(libs.androidx.espresso.intents)
  debugImplementation(libs.androidx.ui.test.manifest)
}
