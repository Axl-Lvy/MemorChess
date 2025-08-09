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

        // Coverage configuration - using absolute path from project root
        property("sonar.coverage.jacoco.xmlReportPaths", "${projectDir}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")

        // Exclusions for source files - exclude generated files but NOT test directories (already handled by sonar.tests)
        property("sonar.exclusions", "**/build/**,**/generated/**,**/*.gradle.kts,**/R.java,**/BuildConfig.java,**/Manifest*.xml,**/debugMain/**")
    }
}
