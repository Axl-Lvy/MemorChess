package proj.memorchess.axl.core.data

import androidx.room.Room
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest

/** Runs [PositionKeyStorageContract] against a real isolated SQLite database. */
class TestRoomPackedKeys : PositionKeyStorageContract() {

  private val database: CustomDatabase =
    getRoomDatabase(
      Room.databaseBuilder<CustomDatabase>(
        name =
          File(System.getProperty("java.io.tmpdir"), "room_packed_keys_${UUID.randomUUID()}.db")
            .absolutePath,
      ),
    )

  override suspend fun freshManager(): DatabaseQueryManager =
    NonJsLocalDatabaseQueryManager(database)

  @AfterTest
  fun tearDown() {
    database.close()
  }
}
