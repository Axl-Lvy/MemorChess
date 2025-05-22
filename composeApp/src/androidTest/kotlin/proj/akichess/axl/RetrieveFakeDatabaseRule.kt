package proj.akichess.axl

import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import proj.ankichess.axl.core.impl.graph.nodes.NodeFactory
import proj.ankichess.axl.test_util.TestDataBase

class RetrieveFakeDatabaseRule : TestRule {
  override fun apply(base: Statement?, description: Description?): Statement? {
    return object : Statement() {
      override fun evaluate() {
        runBlocking { NodeFactory.retrieveGraphFromDatabase(TestDataBase) }
        base?.evaluate()
      }
    }
  }
}
