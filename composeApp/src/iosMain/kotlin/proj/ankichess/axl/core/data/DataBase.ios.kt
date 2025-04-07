package proj.ankichess.axl.core.data

import androidx.room.RoomDatabase

actual fun databaseBuilder(): RoomDatabase.Builder<CustomDatabase> {
  val dbFilePath = documentDirectory() + "/my_room.db"
  return Room.databaseBuilder<AppDatabase>(name = dbFilePath)
}

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
