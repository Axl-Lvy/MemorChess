package proj.memorchess.axl.dev

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.auth.AccessToken
import proj.memorchess.axl.core.auth.Account
import proj.memorchess.axl.core.auth.SignInResult
import proj.memorchess.axl.core.auth.TokenResult

class TestDevAuthProvider {

  private val expiresAt = Instant.parse("2030-01-01T00:00:00Z")

  private val session =
    DevSession(
      baseUrl = "http://127.0.0.1:21234",
      accessToken = AccessToken("jwt"),
      expiresAt = expiresAt,
      account = Account("dev-user", "Dev user"),
      generation = DevServerGeneration("gen-1"),
    )

  @Test
  fun validSessionGivesToken() = runTest {
    val provider = DevAuthProvider(session) { expiresAt - 1.days }

    provider.accessToken() shouldBe TokenResult.Ok(AccessToken("jwt"))
    provider.currentAccount.value shouldBe session.account
  }

  @Test
  fun noSessionIsSignedOut() = runTest {
    val provider = DevAuthProvider(null)

    provider.accessToken() shouldBe TokenResult.SignedOut
    provider.signIn() shouldBe SignInResult.Cancelled
    provider.currentAccount.value.shouldBeNull()
  }

  @Test
  fun expiredSessionIsSignedOut() = runTest {
    val provider = DevAuthProvider(session) { expiresAt + 1.days }

    provider.accessToken() shouldBe TokenResult.SignedOut
    provider.currentAccount.value.shouldBeNull()
  }

  @Test
  fun sessionExpiringWhileRunningSignsOut() = runTest {
    var now = expiresAt - 1.days
    val provider = DevAuthProvider(session) { now }
    provider.accessToken() shouldBe TokenResult.Ok(AccessToken("jwt"))

    now = expiresAt

    provider.accessToken() shouldBe TokenResult.SignedOut
    provider.currentAccount.value.shouldBeNull()
  }

  @Test
  fun signInRestoresTheDevSession() = runTest {
    val provider = DevAuthProvider(session) { expiresAt - 1.days }
    provider.signOut()

    provider.signIn() shouldBe SignInResult.Success

    provider.currentAccount.value shouldBe session.account
  }
}
