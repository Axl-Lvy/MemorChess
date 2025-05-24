package proj.ankichess.axl.core.engine.graph

import kotlin.test.BeforeTest
import proj.ankichess.axl.core.intf.data.DatabaseHolder
import proj.ankichess.axl.test_util.TestDataBase

class TestNodeCreation {
  @BeforeTest
  fun setUp() {
    DatabaseHolder.init(TestDataBase.vienna())
  }
}
