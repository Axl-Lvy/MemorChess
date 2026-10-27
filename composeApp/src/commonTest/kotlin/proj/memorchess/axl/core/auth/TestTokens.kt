package proj.memorchess.axl.core.auth

import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

class TestTokens {

  @Test
  fun `a token result never prints its access token`() {
    TokenResult.Ok(AccessToken(SECRET)).toString() shouldNotContain SECRET
  }

  @Test
  fun `a refresh token never prints itself`() {
    RefreshToken(SECRET).toString() shouldNotContain SECRET
  }

  @Test
  fun `a Lichess account never prints its token`() {
    LichessAccount(LichessToken(SECRET), "alice").toString() shouldNotContain SECRET
  }

  @Test
  fun `an exchange result never prints its tokens`() {
    val result =
      OidcTokenExchangeResult.Ok(
        accessToken = AccessToken(SECRET),
        refreshToken = RefreshToken(SECRET),
        expiresInSeconds = 3600,
        account = null,
      )

    result.toString() shouldNotContain SECRET
  }

  private companion object {
    const val SECRET = "s3cr3t-value"
  }
}
