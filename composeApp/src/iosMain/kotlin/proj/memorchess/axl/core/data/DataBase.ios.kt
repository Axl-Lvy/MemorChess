package proj.memorchess.axl.core.data

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun databaseBuilder(): RoomDatabase.Builder<CustomDatabase> {
  val dbFilePath = documentDirectory() + "/my_room.db"
  return Room.databaseBuilder<CustomDatabase>(name = dbFilePath)
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun documentDirectory(): String {
  val documentDirectory =
    NSFileManager.defaultManager.URLForDirectory(
      directory = NSDocumentDirectory,
      inDomain = NSUserDomainMask,
      appropriateForURL = null,
      create = false,
      error = null,
    )
  return requireNotNull(documentDirectory?.path)
}
