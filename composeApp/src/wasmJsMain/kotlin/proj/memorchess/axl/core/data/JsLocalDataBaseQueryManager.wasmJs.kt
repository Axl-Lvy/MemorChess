package proj.memorchess.axl.core.data

/** Basically, this database does not do anything. */
object JsLocalDatabaseQueryManager : NoOpDatabaseQueryManager()

actual fun getLocalDatabase(): DatabaseQueryManager {
  return JsLocalDatabaseQueryManager
}
