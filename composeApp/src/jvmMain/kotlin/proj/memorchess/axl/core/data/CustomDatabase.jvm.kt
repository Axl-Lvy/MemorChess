package proj.memorchess.axl.core.data

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual fun databaseBuilder(): RoomDatabase.Builder<CustomDatabase> {
  val dbFile = File(System.getProperty("java.io.tmpdir"), "my_room.db")
  return Room.databaseBuilder<CustomDatabase>(name = dbFile.absolutePath)
}
