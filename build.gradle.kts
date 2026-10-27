plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidKmpLibrary) apply false
  alias(libs.plugins.androidTest) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinJvm) apply false
  alias(libs.plugins.kotlinAllopen) apply false
  alias(libs.plugins.kotlinxBenchmark) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.composeHotReload) apply false
  alias(libs.plugins.sonar)
}

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

    property(
      "sonar.coverage.jacoco.xmlReportPaths",
      "${projectDir}/androidApp/build/reports/jacoco/jacocoAndroidTestReport/jacocoAndroidTestReport.xml," +
        "${projectDir}/composeApp/build/reports/kover/reportJvm.xml," +
        // :shared is merged into composeApp's report; :server is not, and as a non-multiplatform
        // module its report file has no Jvm suffix.
        "${projectDir}/server/build/reports/kover/report.xml",
    )

    property(
      "sonar.sources",
      "src/commonMain/kotlin,src/androidMain/kotlin,src/nonJsMain/kotlin,src/wasmJsMain/kotlin,src/jvmMain/kotlin,src/iosMain/kotlin",
    )

    property("sonar.tests", "src/commonTest/kotlin,src/androidTest/kotlin")

    property(
      "sonar.exclusions",
      "**/build/**,**/generated/**,**/*.gradle.kts,**/R.java,**/BuildConfig.java,**/*Manifest*.xml,**/debugMain/**,**/main.kt",
    )

    property(
      "sonar.coverage.exclusions",
      // Kover cannot instrument Kotlin/JS or Kotlin/Native (kotlinx-kover#293). ui/** @Composables
      // emit synthetic branches JaCoCo can't filter. Benchmarks and :server's Main.kt entrypoint
      // are exercised by running them, not by tests.
      "**/build/**,**/generated/**,**/*.gradle.kts,**/R.java,**/BuildConfig.java,**/*Manifest*.xml,**/debugMain/**,**/wasmJsMain/**,**/iosMain/**,**/ui/**,**/main.kt,**/Main.kt,**/core/auth/OAuthLauncher.*.kt,**/core/auth/LichessOAuthRedirectActivity.kt,**/core/auth/LichessRedirectUri.*.kt,**/core/auth/SyncClientId.*.kt,**/macrobenchmark/**,**/microbenchmark/**",
    )

    property("sonar.plsql.file.suffixes", ".sql")
  }
}
