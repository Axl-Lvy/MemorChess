package proj.ankichess.axl.ui.components.control.board_control

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Ignore
import kotlin.test.Test
import proj.ankichess.axl.test_util.TestDataBase

class TestControllableBoard {

  @OptIn(ExperimentalTestApi::class)
  @Test
  @Ignore
  fun testControlBarButtons() = runComposeUiTest {
    setContent { ControllableBoardPage(dataBase = TestDataBase) }
  }
}
