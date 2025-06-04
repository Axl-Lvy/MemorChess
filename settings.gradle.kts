rootProject.name = "MemorChess"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")

// Task to install git hooks
gradle.rootProject {
    afterEvaluate {
        val installHooks = tasks.register("installGitHooks") {
            group = "git hooks"
            description = "Install git hooks"

            doLast {
                val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                val rootDir = rootProject.projectDir.absolutePath
                val hooksDir = "$rootDir/.git/hooks"

                // Create hooks directory if it doesn't exist
                mkdir(hooksDir)

                // Copy hooks
                copy {
                    from("$rootDir/ci/hooks/pre-commit.sh")
                    into(hooksDir)
                    rename("pre-commit.sh", "pre-commit")
                }
                copy {
                    from("$rootDir/ci/hooks/commit-msg.sh")
                    into(hooksDir)
                    rename("commit-msg.sh", "commit-msg")
                }

                // Make hooks executable (not needed on Windows)
                if (!isWindows) {
                    exec {
                        commandLine("chmod", "+x", "$hooksDir/pre-commit", "$hooksDir/commit-msg")
                    }
                }

                println("Git hooks installed successfully.")
            }
        }

        // Run the task automatically when the project is evaluated
        tasks.named("prepareKotlinBuildScriptModel").configure {
            dependsOn(installHooks)
        }
    }
}
