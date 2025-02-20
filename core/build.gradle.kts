import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "proj.ankichess.axl"
version = "unspecified"

dependencies {
}
kotlin {
    jvm() // For ComposeApp or backend modules

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Core"
            isStatic = true
        }
    }
    sourceSets {
         commonMain.dependencies {
                implementation(libs.kotlinx.datetime) // Common dependencies used in core
            }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "core"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "core.js"
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
    }

}