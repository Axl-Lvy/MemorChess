package proj.memorchess.axl.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlin.test.Test

class TestBuildInfo {

  @Test
  fun `sha is never blank`() {
    BuildInfo.sha.shouldNotBeBlank()
  }

  @Test
  fun `sha falls back to dev when the build did not set one`() {
    // The test classpath's resource is generated from the template with no -PbuildSha passed,
    // so this pins the fallback value rather than whatever a CI build happened to set.
    BuildInfo.sha shouldBe "dev"
  }
}
