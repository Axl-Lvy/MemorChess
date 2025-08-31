plugins {
  // this is necessary to avoid the plugins to be loaded multiple times
  // in each subproject's classloader
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidLibrary) apply false
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
      "${projectDir}/composeApp/build/reports/jacoco/jacocoAndroidTestReport/jacocoAndroidTestReport.xml," +
        "${projectDir}/composeApp/build/reports/kover/reportJvm.xml",
    )

    property(
      "sonar.sources",
      "src/commonMain/kotlin,src/androidMain/kotlin,src/nonJsMain/kotlin,src/wasmMain/kotlin,src/jvmMain/kotlin,supabase/functions",
    )

    property("sonar.tests", "src/commonTest/kotlin,src/androidTest/kotlin")

    // Exclusions for source files - exclude generated files but NOT test directories (already
    // handled by sonar.tests)
    // Exclude also ui files as jacoco handles them very poorly.
    property(
      "sonar.exclusions",
      "**/build/**,**/generated/**,**/*.gradle.kts,**/R.java,**/BuildConfig.java,**/*Manifest*.xml,**/debugMain/**,**/main.kt",
    )

    property(
      "sonar.coverage.exclusions",
      "**/build/**,**/generated/**,**/*.gradle.kts,**/R.java,**/BuildConfig.java,**/*Manifest*.xml,**/debugMain/**,**/ui/**,supabase/functions/**",
    )

    // PL/SQL specific configuration for SQL files
    property("sonar.plsql.file.suffixes", ".sql")
  }
}
