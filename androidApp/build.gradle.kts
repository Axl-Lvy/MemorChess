import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ktfmt)
}

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
