package proj.memorchess.axl.core

import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import proj.memorchess.axl.core.date.DateUtil

/** Test suite for [DateUtil]. Covers all public functions and extension functions. */
class TestDateUtil {

  @Test
  fun testNowReturnsRecentInstant() {
    val now = DateUtil.now()
    val systemNow = Clock.System.now()
    // Should be within 5 seconds
    assertTrue(now.epochSeconds - systemNow.epochSeconds in -5..5)
  }

  @Test
  fun testTodayReturnsSystemDate() {
    val todayUtil = DateUtil.today()
    val todaySystem = Clock.System.todayIn(TimeZone.currentSystemDefault())
    assertEquals(todaySystem, todayUtil)
  }

  @Test
  fun testTruncateToSecondsRemovesNanoseconds() {
    val instant = Instant.parse("2025-10-03T12:34:56.789123456Z")
    val truncated = with(DateUtil) { instant.truncateToSeconds() }
    // Should have same epoch seconds, but nanoseconds set to 0
    assertEquals(instant.epochSeconds, truncated.epochSeconds)
    assertEquals(0, truncated.nanosecondsOfSecond)
  }
}
