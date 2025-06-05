package proj.memorchess.axl.core.data

/**
 * Object that holds the database. Before the first database retrieval, it is possible to initialize
 * it with a custom [ICommonDatabase].
 */
object DatabaseHolder {
  private var database: ICommonDatabase? = null

  /**
   * Initializes the database with a custom [ICommonDatabase]. This should be called before the
   * first call to [getDatabase].
   */
  fun init(db: ICommonDatabase) {
    database = db
  }

  /**
   * Retrieves the database. If it has not been initialized yet, it will be initialized with the
   * default [getCommonDatabase] implementation.
   */
  fun getDatabase(): ICommonDatabase {
    val finalDataBase = database
    if (finalDataBase != null) {
      return finalDataBase
    }
    val newDataBase = getCommonDatabase()
    database = newDataBase
    return newDataBase
  }
}
