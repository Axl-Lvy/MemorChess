package proj.memorchess.axl.ui.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.ui.setKineticContent

/** Coverage for [fontFamilies], the eager-preload list [proj.memorchess.axl.ui.App] gates on. */
@OptIn(ExperimentalTestApi::class)
internal class TestKineticTypography {

  @Test
  fun `fontFamilies returns the three distinct kinetic families`() = runComposeUiTest {
    lateinit var typography: KineticTypography
    setKineticContent { typography = LocalKineticTypography.current }

    val families = typography.fontFamilies()

    families shouldHaveSize 3
    families.distinct() shouldHaveSize 3
  }

  @Test
  fun `fontFamilies covers every family a text role actually uses`() = runComposeUiTest {
    lateinit var typography: KineticTypography
    setKineticContent { typography = LocalKineticTypography.current }

    val used =
      setOf(
        typography.brand.fontFamily,
        typography.displayLg.fontFamily,
        typography.body.fontFamily,
        typography.label.fontFamily,
        typography.mono.fontFamily,
      )

    used shouldBe typography.fontFamilies().toSet()
  }
}
