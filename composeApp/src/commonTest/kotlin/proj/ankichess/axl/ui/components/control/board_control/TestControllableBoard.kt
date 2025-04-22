package proj.ankichess.axl.ui.components.control.board_control

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import proj.ankichess.axl.test_util.TestDataBase

class TestControllableBoard {

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun testControlBarButtons() = runComposeUiTest {
    setContent { ControllableBoardPage(dataBase = TestDataBase) }
  }
}
