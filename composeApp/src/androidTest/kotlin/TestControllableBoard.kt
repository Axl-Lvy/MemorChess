import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import proj.ankichess.axl.test_util.TestDataBase
import proj.ankichess.axl.ui.components.control.board_control.ControllableBoardPage

@RunWith(AndroidJUnit4::class)
class TestControllableBoard {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun testControlBarButtons() {
    composeTestRule.setContent { ControllableBoardPage(dataBase = TestDataBase) }
  }
}
