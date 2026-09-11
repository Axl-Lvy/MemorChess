package proj.memorchess.axl.server

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/** Verifies [RateLimitTiers] carries each field through untouched, and [PRODUCTION_RATE_LIMITS]. */
class TestRateLimitTiers {

  @Test
  fun `carries each tier under the field it was given, not another one`() {
    // ARRANGE: four distinct limits and four distinct refill periods, so a field swap between
    // properties (for example syncRead getting admin's budget) would be caught.
    val syncWrite = RateLimitTier(1, 1.minutes)
    val syncRead = RateLimitTier(2, 2.minutes)
    val publicRead = RateLimitTier(3, 3.minutes)
    val admin = RateLimitTier(4, 4.minutes)

    // ACT
    val tiers =
      RateLimitTiers(
        syncWrite = syncWrite,
        syncRead = syncRead,
        publicRead = publicRead,
        admin = admin,
      )

    // ASSERT
    tiers.syncWrite shouldBe syncWrite
    tiers.syncRead shouldBe syncRead
    tiers.publicRead shouldBe publicRead
    tiers.admin shouldBe admin
  }

  @Test
  fun `production rate limits carry the documented stock values`() {
    PRODUCTION_RATE_LIMITS.syncWrite shouldBe RateLimitTier(60, 1.minutes)
    PRODUCTION_RATE_LIMITS.syncRead shouldBe RateLimitTier(300, 1.minutes)
    PRODUCTION_RATE_LIMITS.publicRead shouldBe RateLimitTier(300, 1.minutes)
    PRODUCTION_RATE_LIMITS.admin shouldBe RateLimitTier(30, 1.minutes)
  }
}
