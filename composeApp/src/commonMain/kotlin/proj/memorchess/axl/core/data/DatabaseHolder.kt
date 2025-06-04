package proj.memorchess.axl.core.data

/**
 * Object that holds the database. Before the first database retrieval, it is possible to initialize
 * it with a custom [ICommonDataBase].
 */
object DatabaseHolder {
  private var database: ICommonDataBase? = null

  /**
   * Initializes the database with a custom [ICommonDataBase]. This should be called before the
   * first call to [getDatabase].
   */
  fun init(db: ICommonDataBase) {
    database = db
  }

  /**
   * Retrieves the database. If it has not been initialized yet, it will be initialized with the
   * default [getCommonDataBase] implementation.
   */
  fun getDatabase(): ICommonDataBase {
    val finalDataBase = database
    if (finalDataBase != null) {
      return finalDataBase
    }
    val newDataBase = getCommonDataBase()
    database = newDataBase
    return newDataBase
  }
}
