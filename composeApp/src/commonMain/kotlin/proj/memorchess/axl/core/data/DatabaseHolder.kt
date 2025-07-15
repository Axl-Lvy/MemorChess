package proj.memorchess.axl.core.data

/**
 * Object that holds the database. Before the first database retrieval, it is possible to initialize
 * it with a custom [ILocalDatabase].
 */
object DatabaseHolder {
  private var database: ILocalDatabase? = null

  /**
   * Initializes the database with a custom [ILocalDatabase]. This should be called before the
   * first call to [getDatabase].
   */
  fun init(db: ILocalDatabase) {
    database = db
  }

  /**
   * Retrieves the database. If it has not been initialized yet, it will be initialized with the
   * default [getCommonDatabase] implementation.
   */
  fun getDatabase(): ILocalDatabase {
    val finalDataBase = database
    if (finalDataBase != null) {
      return finalDataBase
    }
    val newDataBase = getCommonDatabase()
    database = newDataBase
    return newDataBase
  }
}
