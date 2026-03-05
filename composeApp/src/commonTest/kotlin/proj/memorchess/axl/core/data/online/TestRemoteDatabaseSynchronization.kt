package proj.memorchess.axl.core.data.online

import io.kotest.assertions.nondeterministic.eventually
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.core.data.online.database.DatabaseUploader
import proj.memorchess.axl.core.data.online.database.KtorQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.test_util.TestAuthenticated
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

class TestRemoteDatabaseSynchronization : TestAuthenticated() {

  private val databaseSynchronizer by inject<DatabaseSynchronizer>()
  private val localDatabase by inject<DatabaseQueryManager>(named("local"))
  private val remoteDatabase by inject<KtorQueryManager>()

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
    localDatabase.insertMoves(refDatabase.dataMoves, refDatabase.dataPositions.values.toList())

    // Verify local has data, remote is empty
    val remotePositionsBefore = remoteDatabase.getAllPositions(false)
    assertTrue(remotePositionsBefore.isEmpty())

    // Act: Sync from local to remote
    databaseSynchronizer.syncFromLocal()
    eventually { assertTrue { databaseUploader.isIdle } }

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
  }

  @Test
  fun testSyncFromRemote() = runTest {
    // Arrange: Set up initial remote data
    remoteDatabase.insertMoves(refDatabase.dataMoves, refDatabase.dataPositions.values.toList())

    // Verify local is empty
    val localPositionsBefore = localDatabase.getAllPositions()
    assertTrue(localPositionsBefore.isEmpty())

    // Act: Sync from remote to local
    databaseSynchronizer.syncFromRemote()
    eventually { assertTrue { databaseUploader.isIdle } }

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
  }

  @Test
  fun testSyncNonEmptyFromLocal() = runTest {
    // Arrange: Set up initial local data
    localDatabase.insertMoves(refDatabase.dataMoves, refDatabase.dataPositions.values.toList())

    // Set up remote with different data
    val londonDb = TestDatabaseQueryManager.london()
    remoteDatabase.insertMoves(londonDb.dataMoves, londonDb.dataPositions.values.toList())

    // Act: Sync from local to remote
    databaseSynchronizer.syncFromLocal()
    eventually { assertTrue { databaseUploader.isIdle } }

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
  }

  @Test
  fun testSyncNonEmptyFromRemote() = runTest {
    // Arrange: Set up initial remote data
    remoteDatabase.insertMoves(refDatabase.dataMoves, refDatabase.dataPositions.values.toList())

    // Set up local with different data
    val londonDb = TestDatabaseQueryManager.london()
    localDatabase.insertMoves(londonDb.dataMoves, londonDb.dataPositions.values.toList())

    // Act: Sync from remote to local
    databaseSynchronizer.syncFromRemote()
    eventually { assertTrue { databaseUploader.isIdle } }

    // Assert: Remote should now have the same data as local
    assertDatabaseAreSame()
  }

  @Test
  fun testBothDatabasesUpdatedWhenSynced() = runTest {
    databaseSynchronizer.syncFromLocal()
    assertTrue(databaseSynchronizer.isSynced)

    globalDatabase.insertMoves(refDatabase.dataMoves, refDatabase.dataPositions.values.toList())
    assertTrue(databaseSynchronizer.isSynced)
    eventually { assertTrue { databaseUploader.isIdle } }
    assertDatabaseAreSame()
  }

  private fun assertDatabaseAreSame() = runTest {
    val localPositions = localDatabase.getAllPositions(false)
    val remotePositions = remoteDatabase.getAllPositions(false).toSet()
    localPositions.forEach {
      assertContains(
        remotePositions,
        it,
        "Local position: $it\nRemote position: ${remotePositions.find { remoteIt -> remoteIt.positionIdentifier == it.positionIdentifier }}",
      )
    }
    assertEquals(localPositions.size, remotePositions.size)
  }
}
