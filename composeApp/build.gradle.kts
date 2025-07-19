import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
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
    outputModuleName = "composeApp"
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
      implementation(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.compose.viewmodel.navigation)
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

tasks.withType<KtfmtBaseTask> { exclude("**/generated/**") }

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

val kmpExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)

kmpExtension.targets.configureEach {
  // Iterate through all compilations (main, test, debug, release, etc.)
  compilations.configureEach {
    // Generate a task name, for example "generateSecretsIosSimulatorArm64Main"
    val generateSecretsTaskName = buildString {
      append("generateSecrets")
      // Capitalize the first letter of the target name
      append(target.name.replaceFirstChar { it.titlecase() })
      // Capitalize the first letter of the compilation name
      append(name.replaceFirstChar { it.titlecase() })
    }
    // Register a task that generates Secrets.kt
    val generateSecretsTask =
      tasks.register(generateSecretsTaskName) {
        group = "codegen"
        description = "Generate secrets for ${target.name}:${name}"
        doFirst { generateSecretFile(this) }
      }
    // 5) Link the generated secrets to compileKotlinTask
    compileTaskProvider.dependsOn(generateSecretsTask)
  }
}

private fun String.toCamelCase(): String {
  return this.split('_')
    .mapIndexed { index, part ->
      if (index == 0) {
        part.lowercase()
      } else {
        part.lowercase().replaceFirstChar { it.uppercase() }
      }
    }
    .joinToString("")
}

private fun generateSecretFile(task: Task) {
  // 1) Determine which local.properties to use
  val moduleProps = file("local.properties")
  val globalProps = rootProject.file("local.properties")
  val propsFile =
    when {
      moduleProps.exists() -> moduleProps
      globalProps.exists() -> globalProps
      else -> error("❌ No local.properties found for module: ${task.name}")
    }
  if (!propsFile.exists()) {
    error("local.properties file not found at ${propsFile.absolutePath}")
  }
  // 2) Load values from local.properties
  val properties = Properties().apply { load(propsFile.inputStream()) }
  // 3) Generate Secrets.kt (by default in commonMain)
  val secretsPackageDir =
    File("${projectDir}/src/commonMain/kotlin/proj/memorchess/axl/core/config/generated")
  val secretsFile = File(secretsPackageDir, "Secrets.kt")
  if (!secretsPackageDir.exists()) {
    secretsPackageDir.mkdirs()
  }
  val content = buildString {
    appendLine("package proj.memorchess.axl.core.config.generated")
    appendLine()
    appendLine("import proj.memorchess.axl.core.config.SecretsTemplate")
    appendLine()
    appendLine("/**")
    appendLine(" * Contains secrets stored in local.properties.")
    appendLine(" *")
    appendLine(" * Automatically generated file. DO NOT EDIT!")
    appendLine(" */")
    appendLine("object Secrets : SecretsTemplate() {")
    properties.forEach { (k, v) ->
      val originalKey = k.toString()
      val camelCaseKey = originalKey.toCamelCase()
      // Check that the camelCase key matches the regex for a variable
      if (Regex("^[a-zA-Z_][a-zA-Z0-9_]*$").matches(camelCaseKey)) {
        appendLine("    override val $camelCaseKey = \"$v\"")
      }
    }
    appendLine("}")
  }
  secretsFile.writeText(content)
  // 4) Add Secrets.kt to .gitignore
  val gitIgnoreFile = file(".gitignore")
  val relativePath = secretsFile.relativeTo(projectDir).path.replace("\\", "/")
  if (!gitIgnoreFile.exists()) {
    gitIgnoreFile.writeText("# Auto-generated .gitignore\n$relativePath\n")
  } else {
    val existing = gitIgnoreFile.readText()
    if (!existing.contains(relativePath)) {
      gitIgnoreFile.appendText("\n$relativePath\n")
    }
  }
}
