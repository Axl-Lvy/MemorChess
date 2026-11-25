package proj.memorchess.axl.core.data

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File
import proj.memorchess.axl.dev.currentDevLaunchConfig
import proj.memorchess.axl.dev.resolveDatabaseFile

actual fun databaseBuilder(): RoomDatabase.Builder<CustomDatabase> {
  val dbFile =
    resolveDatabaseFile(currentDevLaunchConfig, File(System.getProperty("java.io.tmpdir")))
  return Room.databaseBuilder<CustomDatabase>(name = dbFile.absolutePath)
}
