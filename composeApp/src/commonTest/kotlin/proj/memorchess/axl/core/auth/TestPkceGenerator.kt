package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/** Verifies PKCE pair shape and stable challenge derivation. */
class TestPkceGenerator {

  @Test
  fun verifierAndChallengeAreBase64UrlWithoutPadding() = runTest {
    val pair = PkceGenerator.generate()
    val pattern = Regex("^[A-Za-z0-9_-]+$")
    pair.verifier shouldMatch pattern
    pair.challenge shouldMatch pattern
  }

  @Test
  fun verifierIsAtLeast43Chars() = runTest {
    val pair = PkceGenerator.generate()
    (pair.verifier.length >= 43) shouldBe true
  }

  @Test
  fun challengeIsExactly43CharsFor256BitDigest() = runTest {
    // BASE64URL(SHA256(...)) = ceil(32 * 4 / 3) = 43 chars without padding
    val pair = PkceGenerator.generate()
    pair.challenge.length shouldBe 43
  }

  @Test
  fun twoCallsProduceDifferentVerifiers() = runTest {
    val a = PkceGenerator.generate()
    val b = PkceGenerator.generate()
    a.verifier shouldNotBe b.verifier
    a.challenge shouldNotBe b.challenge
  }
}
