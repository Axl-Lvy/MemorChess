package proj.memorchess.axl.server

import java.io.InputStream
import java.util.Properties

/** Resource generated at build time by `:server`'s `processResources`, see `build.gradle.kts`. */
private const val RESOURCE_NAME = "build-info.properties"

/** Value used when the resource is absent or carries no `sha`, e.g. a local, non-Docker build. */
private const val UNKNOWN_SHA = "dev"

/** Identifies the running build, exposed over `GET /v1/version`. */
internal object BuildInfo {

  /** Short git sha of the commit this build was produced from, baked in at Gradle build time. */
  val sha: String = shaFrom(BuildInfo::class.java.classLoader.getResourceAsStream(RESOURCE_NAME))

  /**
   * Reads `sha` out of the properties resource.
   *
   * A parameter rather than a direct classpath read, so the absent, blank and present cases are
   * each reachable from a test without touching the actual build resource.
   */
  internal fun shaFrom(resource: InputStream?): String {
    val properties = Properties()
    resource?.use(properties::load)
    return properties.getProperty("sha")?.takeIf { it.isNotBlank() } ?: UNKNOWN_SHA
  }
}
