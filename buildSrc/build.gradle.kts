import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
  options.isIncremental = true
}

// Improve up-to-date checking
tasks.withType<Copy>().configureEach {
  includeEmptyDirs = false
}

// Optimize caching for buildSrc
java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

// Update to use compilerOptions instead of kotlinOptions (fix for deprecation)
tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

dependencies {
    implementation(gradleApi())
}

normalization {
  runtimeClasspath {
    metaInf {
      ignoreAttribute("Implementation-Version")
      ignoreAttribute("Built-By")
      ignoreAttribute("Build-Date")
    }
  }
}
