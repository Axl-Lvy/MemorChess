package proj.memorchess.axl.ui.pages

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.test.Test

/**
 * Verifies `values/strings.xml` and `values-fr/strings.xml` declare the exact same `<string>` and
 * `<plurals>` keys.
 *
 * `commonTest` cannot run this: reading the raw XML off disk needs `java.io.File`, which does not
 * exist on wasmJs or iOS. Modeled on [proj.memorchess.axl.ui.theme.TestBundledFontNaming]'s own
 * precedent for reading bundled resource files directly in a `jvmTest`.
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
