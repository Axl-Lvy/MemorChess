plugins {
  // this is necessary to avoid the plugins to be loaded multiple times
  // in each subproject's classloader
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidKmpLibrary) apply false
  alias(libs.plugins.androidTest) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.composeHotReload) apply false
  alias(libs.plugins.sonar)
}

// Load properties from local.properties
val localProperties = java.util.Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
  localProperties.load(localPropertiesFile.inputStream())
}

sonar {
  properties {
    property("sonar.projectKey", "Axl-Lvy_MemorChess")
    property("sonar.organization", "axl-lvy")
    property("sonar.host.url", "https://sonarcloud.io")

    // Coverage configuration - using absolute path from project root
    property(
      "sonar.coverage.jacoco.xmlReportPaths",
      "${projectDir}/androidApp/build/reports/jacoco/jacocoAndroidTestReport/jacocoAndroidTestReport.xml," +
        "${projectDir}/composeApp/build/reports/kover/reportJvm.xml",
    )

    property(
      "sonar.sources",
      "src/commonMain/kotlin,src/androidMain/kotlin,src/nonJsMain/kotlin,src/wasmJsMain/kotlin,src/jvmMain/kotlin,src/iosMain/kotlin",
    )

    property("sonar.tests", "src/commonTest/kotlin,src/androidTest/kotlin")

    // Exclusions for source files - exclude generated files but NOT test directories (already
    // handled by sonar.tests)
    property(
      "sonar.exclusions",
      "**/build/**,**/generated/**,**/*.gradle.kts,**/R.java,**/BuildConfig.java,**/*Manifest*.xml,**/debugMain/**,**/main.kt",
    )

    property(
      "sonar.coverage.exclusions",
      // wasmJsMain and iosMain are excluded from coverage because Kover does not support
      // Kotlin/JS or Kotlin/Native instrumentation (kotlinx-kover#293). Files in these
      // source sets are still analyzed for code quality (bugs, smells, vulnerabilities).
      // ui/** is excluded because @Composable functions emit synthetic branches that
      // JaCoCo can't filter, inflating uncovered-condition counts on otherwise covered code.
      "**/build/**,**/generated/**,**/*.gradle.kts,**/R.java,**/BuildConfig.java,**/*Manifest*.xml,**/debugMain/**,**/wasmJsMain/**,**/iosMain/**,**/ui/**,**/main.kt,**/core/auth/OAuthLauncher.*.kt,**/core/auth/LichessOAuthRedirectActivity.kt,**/core/auth/LichessRedirectUri.*.kt",
    )

    // PL/SQL specific configuration for SQL files
    property("sonar.plsql.file.suffixes", ".sql")
  }
}
