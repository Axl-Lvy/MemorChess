package proj.memorchess.axl.core.data

/**
 * Object that holds the database. Before the first database retrieval, it is possible to initialize
 * it with a custom [DatabaseQueryManager].
 */
object LocalDatabaseHolder {
  private var database: DatabaseQueryManager? = null

  /**
   * Initializes the database with a custom [DatabaseQueryManager]. This should be called before the
   * first call to [getDatabase].
   */
  fun init(db: DatabaseQueryManager) {
    database = db
  }

  fun reset() {
    database = null
  }

  /**
   * Retrieves the database. If it has not been initialized yet, it will be initialized with the
   * default [getPlatformSpecificLocalDatabase] implementation.
   */
  fun getDatabase(): DatabaseQueryManager {
    val finalDataBase = database
    if (finalDataBase != null) {
      return finalDataBase
    }
    val newDataBase = getPlatformSpecificLocalDatabase()
    database = newDataBase
    return newDataBase
  }
}
