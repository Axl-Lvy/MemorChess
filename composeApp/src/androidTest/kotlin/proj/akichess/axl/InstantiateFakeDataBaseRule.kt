package proj.akichess.axl

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import proj.ankichess.axl.core.intf.data.DatabaseHolder
import proj.ankichess.axl.test_util.TestDataBase

class InstantiateFakeDataBaseRule() : TestRule {
  override fun apply(base: Statement?, description: Description?): Statement? {
    return object : Statement() {
      override fun evaluate() {
        DatabaseHolder.init(TestDataBase.vienna())
      }
    }
  }
}
