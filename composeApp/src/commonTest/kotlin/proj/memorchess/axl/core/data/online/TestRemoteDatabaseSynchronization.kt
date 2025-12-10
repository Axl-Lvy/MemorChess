package proj.memorchess.axl.core.data.online

import io.kotest.assertions.nondeterministic.eventually
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.core.data.online.database.DatabaseUploader
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.test_util.TestAuthenticated
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestRemoteDatabaseSynchronization : TestAuthenticated() {

  private val databaseSynchronizer by inject<DatabaseSynchronizer>()
  private val localDatabase by inject<DatabaseQueryManager>(named("local"))
  private val remoteDatabase by inject<SupabaseQueryManager>()

  private val globalDatabase by inject<DatabaseQueryManager>()
  private val databaseUploader by inject<DatabaseUploader>()

  private val refDatabase = TestDatabaseQueryManager.vienna()

  override fun setUp() {
    super.setUp()
    runTest {
      // Clear remote database to start with clean state
      remoteDatabase.deleteAll(DateUtil.farInThePast())
      localDatabase.deleteAll(DateUtil.farInThePast())
    }
  }

  @Test
  fun testSyncFromLocal() = runTest {
    // Arrange: Set up initial local data
    refDatabase.getAllNodes().forEach { localDatabase.insertNodes(it) }

    // Verify local has data, remote is empty
    val remoteNodesBefore = remoteDatabase.getAllNodes(false)
    assertTrue(remoteNodesBefore.isEmpty())

    // Act: Sync from local to remote
    databaseSynchronizer.syncFromLocal()
    eventually { assertTrue { databaseUploader.isIdle } }

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
  }

  @Test
  fun testSyncFromRemote() = runTest {
    // Arrange: Set up initial local data
    refDatabase.getAllNodes().forEach { remoteDatabase.insertNodes(it) }

    // Verify local has data, remote is empty
    val remoteNodesBefore = localDatabase.getAllNodes()
    assertTrue(remoteNodesBefore.isEmpty())

    // Act: Sync from local to remote
    databaseSynchronizer.syncFromRemote()
    eventually { assertTrue { databaseUploader.isIdle } }

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
  }

  @Test
  fun testSyncNonEmptyFromLocal() = runTest {
    // Arrange: Set up initial local data
    refDatabase.getAllNodes().forEach { localDatabase.insertNodes(it) }

    // Verify local has data, remote is empty
    TestDatabaseQueryManager.london().getAllNodes().forEach { remoteDatabase.insertNodes(it) }

    // Act: Sync from local to remote
    databaseSynchronizer.syncFromLocal()
    eventually { assertTrue { databaseUploader.isIdle } }

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
  }

  @Test
  fun testSyncNonEmptyFromRemote() = runTest {
    // Arrange: Set up initial local data
    refDatabase.getAllNodes().forEach { remoteDatabase.insertNodes(it) }

    // Verify local has data, remote is empty
    TestDatabaseQueryManager.london().getAllNodes().forEach { localDatabase.insertNodes(it) }

    // Act: Sync from local to remote
    databaseSynchronizer.syncFromRemote()
    eventually { assertTrue { databaseUploader.isIdle } }

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
  }

  @Test
  fun testBothDatabasesUpdatedWhenSynced() = runTest {
    databaseSynchronizer.syncFromLocal()
    assertTrue(databaseSynchronizer.isSynced)

    refDatabase.getAllNodes().forEach { globalDatabase.insertNodes(it) }
    assertTrue(databaseSynchronizer.isSynced)
    eventually { assertTrue { databaseUploader.isIdle } }
    assertDatabaseAreSame()
  }

  private fun assertDatabaseAreSame() = runTest {
    val localNodes = localDatabase.getAllNodes(false)
    val remoteNodes = remoteDatabase.getAllNodes(false).toSet()
    localNodes.forEach {
      assertContains(
        remoteNodes,
        it,
        "Local node: $it\nRemote node: ${remoteNodes.find { remoteIt -> remoteIt.positionIdentifier == it.positionIdentifier }}",
      )
    }
    assertEquals(localNodes.size, remoteNodes.size)
  }
}
