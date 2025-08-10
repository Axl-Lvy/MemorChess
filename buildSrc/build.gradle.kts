import org.jetbrains.kotlin.daemon.common.configureDaemonJVMOptions

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
