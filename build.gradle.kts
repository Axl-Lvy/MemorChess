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

sonar {
    properties {
        property("sonar.projectKey", "Axl-Lvy_MemorChess")
        property("sonar.organization", "axl-lvy")
        property("sonar.host.url", "https://sonarcloud.io")

        // Coverage configuration
        property("sonar.coverage.jacoco.xmlReportPaths", "composeApp/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
        property("sonar.androidLint.reportPaths", "composeApp/build/reports/lint-results.xml")
        property("sonar.kotlin.detekt.reportPaths", "composeApp/build/reports/detekt/detekt.xml")

        // Source directories
        property("sonar.sources", "composeApp/src/commonMain,composeApp/src/androidMain")
        property("sonar.tests", "composeApp/src/commonTest,composeApp/src/androidTest")

        // Exclusions
        property("sonar.exclusions", "**/build/**,**/generated/**,**/*.gradle.kts,**/R.java,**/BuildConfig.java")
        property("sonar.test.exclusions", "**/build/**,**/generated/**")
    }
}
