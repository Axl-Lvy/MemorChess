package proj.memorchess.axl.online

import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.online.auth.BasicAuthManager
import proj.memorchess.axl.core.data.online.createSupabaseClient
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager
import proj.memorchess.axl.utils.TestFromMainActivity

class TestRemoteDatabaseQueryManager : TestFromMainActivity() {
  @Test
  fun testThings() {
    runBlocking {
      val supabaseClient = createSupabaseClient()
      val authManager = BasicAuthManager(supabaseClient)
      authManager.signInFromEmail(Secrets.testUserMail, Secrets.testUserPassword)
      delay(1000)
      val remoteDatabaseQueryManager =
        SupabaseQueryManager(supabaseClient, authManager = authManager)
      val nodes = remoteDatabaseQueryManager.getAllNodes(true)
      val lastupdate = remoteDatabaseQueryManager.getLastUpdate()
      assertTrue { nodes.isNotEmpty() }
      assertTrue { lastupdate != null }
    }
  }
}
