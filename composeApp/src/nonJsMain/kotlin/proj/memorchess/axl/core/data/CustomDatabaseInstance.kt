package proj.memorchess.axl.core.data

/**
 * Lazy process wide singleton over [CustomDatabase]. Shared between
 * [proj.memorchess.axl.core.data.NonJsLocalDatabaseQueryManager] and
 * [proj.memorchess.axl.core.data.explorer.NonJsExplorerCache] so both layers talk to the same Room
 * file.
 */
internal val customDatabase: CustomDatabase by lazy { getRoomDatabase(databaseBuilder()) }
