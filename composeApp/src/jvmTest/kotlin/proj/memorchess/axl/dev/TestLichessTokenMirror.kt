package proj.memorchess.axl.dev

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.core.auth.AccessToken
import proj.memorchess.axl.core.auth.Account
import proj.memorchess.axl.core.auth.LichessToken
import proj.memorchess.axl.core.auth.OAuthTokenStore
import proj.memorchess.axl.core.auth.OidcTokenStore
import proj.memorchess.axl.test_util.TestSettings

class TestLichessTokenMirror {

  private val sourceSettings =
    TestSettings().also {
      OAuthTokenStore(it).save(LichessToken("lichess-token"), "magnus")
      OidcTokenStore(it)
        .save(
          AccessToken("sync-token"),
          null,
          Instant.parse("2030-01-01T00:00:00Z"),
          Account("s", null),
        )
    }

  @Test
  fun copiesTokenAndUsername() {
    val targetSettings = TestSettings()
    val target = OAuthTokenStore(targetSettings)

    mirrorLichessToken(OAuthTokenStore(sourceSettings), target)

    target.account.value shouldBe OAuthTokenStore(sourceSettings).account.value
    OidcTokenStore(targetSettings).currentAccount.value.shouldBeNull()
  }

  @Test
  fun neverWritesSource() {
    val before = sourceSettings.keys.associateWith { sourceSettings.getStringOrNull(it) }

    mirrorLichessToken(OAuthTokenStore(sourceSettings), OAuthTokenStore(TestSettings()))

    sourceSettings.keys.associateWith { sourceSettings.getStringOrNull(it) } shouldBe before
  }

  @Test
  fun sourceWithoutTokenKeepsTarget() {
    val target = OAuthTokenStore(TestSettings()).also { it.save(LichessToken("dev"), "dev") }

    mirrorLichessToken(OAuthTokenStore(TestSettings()), target)

    target.getToken() shouldBe LichessToken("dev")
  }

  @Test
  fun absentNodeIsNotCreated() {
    val path = "proj/memorchess/axl-test-${UUID.randomUUID()}"

    sharedLichessTokenStore(path).shouldBeNull()

    Preferences.userRoot().nodeExists(path) shouldBe false
  }
}
