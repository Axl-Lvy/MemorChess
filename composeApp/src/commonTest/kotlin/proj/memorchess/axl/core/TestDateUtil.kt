package proj.memorchess.axl.core

import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
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
  fun testFarInThePastReturnsEpoch() {
    val farPast = DateUtil.farInThePast()
    assertEquals("1970-01-01T00:00:00Z", farPast.toString())
    assertEquals(0, farPast.epochSeconds)
  }

  @Test
  fun testTodayReturnsSystemDate() {
    val todayUtil = DateUtil.today()
    val todaySystem = Clock.System.todayIn(TimeZone.currentSystemDefault())
    assertEquals(todaySystem, todayUtil)
  }

  @Test
  fun testTomorrowIsTodayPlusOne() {
    val today = DateUtil.today()
    val tomorrow = DateUtil.tomorrow()
    assertEquals(today.plus(DatePeriod(days = 1)), tomorrow)
  }

  @Test
  fun testDateInDaysPositiveZeroNegative() {
    val today = DateUtil.today()
    assertEquals(today, DateUtil.dateInDays(0))
    assertEquals(today.plus(DatePeriod(days = 5)), DateUtil.dateInDays(5))
    assertEquals(today.minus(DatePeriod(days = 3)), DateUtil.dateInDays(-3))
  }

  @Test
  fun testDaysUntilFuturePastToday() {
    val today = DateUtil.today()
    val future = today.plus(DatePeriod(days = 10))
    val past = today.minus(DatePeriod(days = 7))
    assertEquals(10, DateUtil.daysUntil(future))
    assertEquals(7, DateUtil.daysUntil(past))
    assertEquals(0, DateUtil.daysUntil(today))
  }

  @Test
  fun testTruncateToSecondsRemovesNanoseconds() {
    val instant = Instant.parse("2025-10-03T12:34:56.789123456Z")
    val truncated = with(DateUtil) { instant.truncateToSeconds() }
    // Should have same epoch seconds, but nanoseconds set to 0
    assertEquals(instant.epochSeconds, truncated.epochSeconds)
    assertEquals(0, truncated.nanosecondsOfSecond)
  }

  @Test
  fun testIsAlmostEqualWithinTolerance() {
    val a = Instant.fromEpochSeconds(1000)
    val b = Instant.fromEpochSeconds(1004)
    val c = Instant.fromEpochSeconds(1006)
    with(DateUtil) {
      assertTrue(a.isAlmostEqual(b, tolerance = 5))
      assertFalse(a.isAlmostEqual(c, tolerance = 5))
      assertFalse(a.isAlmostEqual(null, tolerance = 5))
    }
  }

  @Test
  fun testMaxOfBothNullOneNullBothNonNull() {
    val a: Instant? = null
    val b: Instant? = null
    val c = Instant.fromEpochSeconds(1000)
    val d = Instant.fromEpochSeconds(2000)
    assertNull(DateUtil.maxOf(a, b))
    assertEquals(c, DateUtil.maxOf(c, null))
    assertEquals(d, DateUtil.maxOf(null, d))
    assertEquals(d, DateUtil.maxOf(c, d))
    assertEquals(d, DateUtil.maxOf(d, c))
  }

  @Test
  fun testLocalDateTimeToInstantMatchesExpected() {
    val ldt = LocalDateTime(2025, 10, 3, 12, 0, 0)
    val expected = ldt.toInstant(TimeZone.currentSystemDefault())
    val actual = with(DateUtil) { ldt.toInstant() }
    assertEquals(expected, actual)
  }
}
