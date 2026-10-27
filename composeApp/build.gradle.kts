import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import java.io.File
import java.security.MessageDigest
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
  androidLibrary {
    namespace = "proj.memorchess.axl.library"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    // Without this, CMP resources are missing from the APK and the app crashes (CMP-9547).
    androidResources.enable = true
  }

  jvm()

  listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    outputModuleName = "composeApp"
    browser {
      commonWebpackConfig {
        outputFileName = "composeApp.js"
        devServer =
          (devServer ?: KotlinWebpackConfig.DevServer()).apply {
            static(project.rootDir.path)
            static(project.projectDir.path)
            open = false
          }
      }
      testTask { useKarma { useFirefoxHeadless() } }
    }
    binaries.executable()
  }

  // withAndroidTarget() only matches the legacy KotlinAndroidTarget, so androidLibrary is
  // matched by platform type.
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

  sourceSets {
    commonMain.dependencies {
      // api: shared types appear in composeApp's public signatures.
      api(projects.shared)

      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.components.ui.tooling.preview)

      implementation(libs.androidx.lifecycle.viewmodel)
      implementation(libs.androidx.lifecycle.runtime.compose)

      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization.json)

      implementation(libs.navigation.compose)
      implementation(libs.material.icons)
      implementation(libs.xfeather.z)
      implementation(libs.material3.adaptive)

      implementation(libs.chess.core)
      implementation(libs.stockfish.multiplatform)

      implementation(libs.filekit.dialogs.compose)

      implementation(libs.multiplatform.settings)
      implementation(libs.kermit.logging)

      implementation(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.compose.viewmodel.navigation)

      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.kotlinx.json)
    }

    val debugMain by getting { dependencies { implementation(libs.hotpreview) } }

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
      // Satisfies memfs's tslib@2 peer dependency, which yarn otherwise reports as unmet.
      implementation(devNpm("tslib", "2.8.1"))
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

ktfmt { googleStyle() }

tasks.withType<KtfmtBaseTask> { exclude("**/generated/**") }

room { schemaDirectory("$projectDir/schemas") }

dependencies {
  add("kspAndroid", libs.androidx.room.compiler)
  add("kspIosSimulatorArm64", libs.androidx.room.compiler)
  add("kspIosArm64", libs.androidx.room.compiler)
  add("kspJvm", libs.androidx.room.compiler)

  // The engine in :shared is mostly tested from here; without this it reads as uncovered.
  kover(project(":shared"))
}

tasks.withType<ComposeHotRun>().configureEach { mainClass = "proj.memorchess.axl.MainKt" }

// Kover attaches its own JaCoCo agent; two agents on one JVM crash at class definition time.
tasks.named<Test>("jvmTest") { extensions.configure<JacocoTaskExtension> { isEnabled = false } }

// Skiko uses System::load, which JDK 24+ flags as restricted native access.
tasks.withType<Test>().configureEach { jvmArgs("--enable-native-access=ALL-UNNAMED") }

tasks.withType<JavaExec>().configureEach { jvmArgs("--enable-native-access=ALL-UNNAMED") }

tasks.withType<KotlinWebpack>().configureEach {
  notCompatibleWithConfigurationCache("Kotlin/JS Webpack tasks store Project references")
}

tasks.withType<KotlinJsTest>().configureEach {
  notCompatibleWithConfigurationCache(
    "Kotlin/JS testing tasks store Project references and use non-serializable types."
  )
}

// Content-hashes the fixed-name entry files, since edge caches (Cloudflare) ignore
// `Cache-Control: no-cache` and would pair a stale JS with a new `.wasm`.
// `StaticFrontendRoutes.HASHED_ASSET_PATTERN` (`:server`) must keep matching these names.
val wasmJsProductionDist = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")

val hashWasmJsBundle by tasks.registering {
  dependsOn("wasmJsBrowserDistribution")
  notCompatibleWithConfigurationCache(
    "reads and rewrites files wasmJsBrowserDistribution just wrote"
  )
  doLast {
    val distDir = wasmJsProductionDist.get().asFile

    fun hashEntryFile(fileName: String, htmlAttribute: String) {
      val asset = File(distDir, fileName)
      if (!asset.exists()) {
        error("Expected $asset to exist; did the wasmJs output filename change?")
      }
      val hash =
        MessageDigest.getInstance("SHA-256")
          .digest(asset.readBytes())
          .joinToString("") { "%02x".format(it) }
          .take(20)
      val hashedName =
        "${fileName.substringBeforeLast('.')}.$hash.${fileName.substringAfterLast('.')}"
      asset.copyTo(File(distDir, hashedName), overwrite = true)
      asset.delete()

      val indexHtml = File(distDir, "index.html")
      val original = indexHtml.readText()
      val rewritten =
        original.replace("$htmlAttribute=\"$fileName\"", "$htmlAttribute=\"$hashedName\"")
      check(rewritten != original) { "$indexHtml did not reference $fileName" }
      indexHtml.writeText(rewritten)
    }

    hashEntryFile("composeApp.js", htmlAttribute = "src")
    hashEntryFile("styles.css", htmlAttribute = "href")
  }
}

tasks.named("wasmJsBrowserDistribution") { finalizedBy(hashWasmJsBundle) }

// Explicit source sets disable the scanner's KMP auto-detection.
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
