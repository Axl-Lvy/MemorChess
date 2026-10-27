import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidTest)
  alias(libs.plugins.ktfmt)
}

android {
  namespace = "proj.memorchess.axl.macrobenchmark"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // TraceSectionMetric needs Macrobenchmark to enable Perfetto SDK tracing.
    testInstrumentationRunnerArguments["androidx.benchmark.fullTracing.enable"] = "true"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildTypes {
    // Matches :androidApp's benchmark build type; only the target app must not be debuggable.
    create("benchmark") {
      isDebuggable = true
      signingConfig = getByName("debug").signingConfig
      matchingFallbacks += listOf("release")
    }
  }

  targetProjectPath = ":androidApp"
  // Runs against the separately installed target app instead of loading it in-process.
  experimentalProperties["android.experimental.self-instrumenting"] = true

  testOptions {
    managedDevices {
      localDevices {
        create("pixel6Api34") {
          device = "Pixel 6"
          apiLevel = 34
          systemImageSource = "aosp"
        }
      }
    }
  }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

androidComponents { beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" } }

ktfmt { googleStyle() }

// ktfmt does not detect source sets compiled by AGP 9's built-in Kotlin, so wire them manually.
val ktfmtFormatBenchmarkSources =
  tasks.register<KtfmtFormatTask>("ktfmtFormatBenchmarkSources") {
    source = fileTree("src") { include("**/*.kt") }
  }

val ktfmtCheckBenchmarkSources =
  tasks.register<KtfmtCheckTask>("ktfmtCheckBenchmarkSources") {
    source = fileTree("src") { include("**/*.kt") }
  }

tasks.named("ktfmtFormat") { dependsOn(ktfmtFormatBenchmarkSources) }

tasks.named("ktfmtCheck") { dependsOn(ktfmtCheckBenchmarkSources) }

dependencies {
  implementation(libs.androidx.benchmark.macro.junit4)
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.uiautomator)
}
