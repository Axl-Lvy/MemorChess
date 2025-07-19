plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.sonar)
}

sonar {
    properties {
        property("sonar.projectKey", "Axl-Lvy_MemorChess")
        property("sonar.organization", "axl-lvy")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}
