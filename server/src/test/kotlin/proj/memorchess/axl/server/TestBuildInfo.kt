package proj.memorchess.axl.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import java.io.ByteArrayInputStream
import kotlin.test.Test

class TestBuildInfo {

  private fun propertiesStream(content: String) = ByteArrayInputStream(content.toByteArray())

  @Test
  fun `sha is never blank`() {
    BuildInfo.sha.shouldNotBeBlank()
  }

  @Test
  fun `reads sha from the resource when it is present and non blank`() {
    BuildInfo.shaFrom(propertiesStream("sha=abc1234")) shouldBe "abc1234"
  }

  @Test
  fun `falls back to dev when the property is blank`() {
    BuildInfo.shaFrom(propertiesStream("sha=")) shouldBe "dev"
  }

  @Test
  fun `falls back to dev when the resource is absent`() {
    BuildInfo.shaFrom(null) shouldBe "dev"
  }
}
