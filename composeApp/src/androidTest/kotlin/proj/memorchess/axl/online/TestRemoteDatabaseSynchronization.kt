package proj.memorchess.axl.online

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.core.data.online.database.RemoteDatabaseQueryManager
import proj.memorchess.axl.core.data.online.database.isSynced
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.utils.TestWithAuthentication

class TestRemoteDatabaseSynchronization : TestWithAuthentication() {

  private val databaseSynchronizer by inject<DatabaseSynchronizer>()
  private val localDatabase by inject<DatabaseQueryManager>(named("local"))
  private val remoteDatabase by inject<RemoteDatabaseQueryManager>()

  private val globalDatabase by inject<DatabaseQueryManager>()

  private val refDatabase = TestDatabaseQueryManager.vienna()

  override fun setUp() {
    super.setUp()
    runTest {
      authManager.signInFromEmail(Secrets.testUserMail, Secrets.testUserPassword)
      // Clear remote database to start with clean state
      remoteDatabase.deleteAll()
      localDatabase.deleteAll()
    }
  }

  @Test
  fun testSyncFromLocal() = runTest {
    // Arrange: Set up initial local data
    refDatabase.getAllNodes().forEach { localDatabase.insertPosition(it) }

    // Verify local has data, remote is empty
    val remoteNodesBefore = remoteDatabase.getAllNodes()
    assertTrue(remoteNodesBefore.isEmpty())

    // Act: Sync from local to remote
    databaseSynchronizer.syncFromLocal()

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
    assertTrue(isSynced)
  }

  @Test
  fun testSyncFromRemote() = runTest {
    // Arrange: Set up initial local data
    refDatabase.getAllNodes().forEach { remoteDatabase.insertPosition(it) }

    // Verify local has data, remote is empty
    val remoteNodesBefore = localDatabase.getAllNodes()
    assertTrue(remoteNodesBefore.isEmpty())

    // Act: Sync from local to remote
    databaseSynchronizer.syncFromRemote()

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
    assertTrue(isSynced)
  }

  @Test
  fun testSyncNonEmptyFromLocal() = runTest {
    // Arrange: Set up initial local data
    refDatabase.getAllNodes().forEach { localDatabase.insertPosition(it) }

    // Verify local has data, remote is empty
    TestDatabaseQueryManager.london().getAllNodes().forEach { remoteDatabase.insertPosition(it) }

    // Act: Sync from local to remote
    databaseSynchronizer.syncFromLocal()

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
    assertTrue(isSynced)
  }

  @Test
  fun testSyncNonEmptyFromRemote() = runTest {
    // Arrange: Set up initial local data
    refDatabase.getAllNodes().forEach { remoteDatabase.insertPosition(it) }

    // Verify local has data, remote is empty
    TestDatabaseQueryManager.london().getAllNodes().forEach { localDatabase.insertPosition(it) }

    // Act: Sync from local to remote
    databaseSynchronizer.syncFromRemote()

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
    assertTrue(isSynced)
  }

  @Test
  fun testBothDatabasesUpdatedWhenSynced() = runTest {
    databaseSynchronizer.syncFromLocal()
    assertTrue(isSynced)

    refDatabase.getAllNodes().forEach { globalDatabase.insertPosition(it) }
    assertTrue(isSynced)
    assertDatabaseAreSame()
  }

  private fun assertDatabaseAreSame() = runTest {
    val localNodes = localDatabase.getAllNodes()
    val remoteNodes = remoteDatabase.getAllNodes().toSet()
    localNodes.forEach { assertContains(remoteNodes, it) }
    assertTrue(localNodes.size == remoteNodes.size)
  }
}
