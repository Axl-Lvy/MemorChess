package proj.memorchess.axl.core.date

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone

class TestDateUtil {

  @Test
  fun todayWithNoArgumentMatchesTheSystemDefaultZone() {
    DateUtil.today() shouldBe DateUtil.today(TimeZone.currentSystemDefault())
  }

  @Test
  fun todayWithAnExplicitZoneCanDifferFromTheSystemDefaultZone() {
    // 26 hours apart, so their current local dates can never coincide. Proves today(zone) is
    // actually keyed on the given zone rather than always returning the same value.
    val zoneAhead = UtcOffset(hours = 14).asTimeZone()
    val zoneBehind = UtcOffset(hours = -12).asTimeZone()
    (DateUtil.today(zoneAhead) == DateUtil.today(zoneBehind)) shouldBe false
  }
}
