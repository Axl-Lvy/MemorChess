package proj.ankichess.axl.core.data

import androidx.room.Room
import androidx.room.RoomDatabase
import proj.ankichess.axl.MAIN_ACTIVITY

actual fun databaseBuilder(): RoomDatabase.Builder<CustomDatabase> {
  val appContext = MAIN_ACTIVITY.applicationContext
  val dbFile = appContext.getDatabasePath("my_room.db")
  println("Database file path: ${dbFile.absolutePath}")
  return Room.databaseBuilder<CustomDatabase>(context = appContext, name = dbFile.absolutePath)
}
