package proj.memorchess.axl.core.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(entities = [NodeEntity::class, MoveEntity::class], version = 1, autoMigrations = [])
@TypeConverters(DateConverters::class)
@ConstructedBy(DatabaseConstructor::class)
abstract class CustomDatabase : RoomDatabase() {
  abstract fun getNodeEntityDao(): NodeEntityDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object DatabaseConstructor : RoomDatabaseConstructor<CustomDatabase> {
  override fun initialize(): CustomDatabase
}

expect fun databaseBuilder(): RoomDatabase.Builder<CustomDatabase>

fun getRoomDatabase(builder: RoomDatabase.Builder<CustomDatabase>): CustomDatabase {
  return builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
}
