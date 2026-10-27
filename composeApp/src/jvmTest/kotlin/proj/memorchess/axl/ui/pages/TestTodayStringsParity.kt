package proj.memorchess.axl.ui.pages

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.test.Test

/**
 * Checks the English and French `strings.xml` declare the same keys. JVM only, since reading the
 * files needs `java.io.File`.
 */
class TestTodayStringsParity {

  private val keyPattern = Regex("""<(?:string|plurals)\s+name="([^"]+)"""")

  private fun keysIn(file: File): Set<String> =
    keyPattern.findAll(file.readText()).map { it.groupValues[1] }.toSet()

  @Test
  fun englishAndFrenchStringsDeclareTheSameKeys() {
    val english = File("src/commonMain/composeResources/values/strings.xml")
    val french = File("src/commonMain/composeResources/values-fr/strings.xml")

    val englishKeys = keysIn(english)
    val frenchKeys = keysIn(french)
    val stragglers = (englishKeys - frenchKeys) + (frenchKeys - englishKeys)

    stragglers.shouldBeEmpty()
  }

  @Test
  fun everyTodayKeyIsPresentInBothFiles() {
    val english = keysIn(File("src/commonMain/composeResources/values/strings.xml"))
    val french = keysIn(File("src/commonMain/composeResources/values-fr/strings.xml"))
    val todayKeys =
      listOf(
        "today_greeting",
        "today_weekday_monday",
        "today_weekday_tuesday",
        "today_weekday_wednesday",
        "today_weekday_thursday",
        "today_weekday_friday",
        "today_weekday_saturday",
        "today_weekday_sunday",
        "today_streak_label",
        "today_goal_progress",
        "today_goal_done",
        "today_start_review_cta",
        "today_pickup_title",
        "today_pickup_progress",
        "today_pickup_empty",
      )

    todayKeys.forEach { key ->
      (key in english) shouldBe true
      (key in french) shouldBe true
    }
  }
}
