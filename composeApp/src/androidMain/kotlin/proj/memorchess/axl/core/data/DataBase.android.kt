package proj.memorchess.axl.core.data

import androidx.room.Room
import androidx.room.RoomDatabase
import proj.memorchess.axl.AndroidContextProvider

actual fun databaseBuilder(): RoomDatabase.Builder<CustomDatabase> {
  val appContext = AndroidContextProvider.context
  val dbFile = appContext.getDatabasePath("my_room.db")
  println("Database file path: ${dbFile.absolutePath}")
  return Room.databaseBuilder<CustomDatabase>(context = appContext, name = dbFile.absolutePath)
}
