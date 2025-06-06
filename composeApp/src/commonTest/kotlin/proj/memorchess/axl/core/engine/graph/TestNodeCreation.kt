package proj.memorchess.axl.core.engine.graph

import kotlin.test.BeforeTest
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.test_util.TestDatabase

class TestNodeCreation {
  @BeforeTest
  fun setUp() {
    DatabaseHolder.init(TestDatabase.vienna())
  }
}
