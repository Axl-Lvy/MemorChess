package proj.memorchess.axl.core

import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import proj.memorchess.axl.core.date.DateUtil

/** Tests for [DateUtil]. */
class TestDateUtil {

  @Test
  fun testNowReturnsRecentInstant() {
    val now = DateUtil.now()
    val systemNow = Clock.System.now()

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

    assertEquals(instant.epochSeconds, truncated.epochSeconds)
    assertEquals(0, truncated.nanosecondsOfSecond)
  }
}
