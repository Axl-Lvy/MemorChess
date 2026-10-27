package proj.memorchess.axl.core.data

/** Lazy process wide singleton over [CustomDatabase], so every layer shares one Room file. */
internal val customDatabase: CustomDatabase by lazy { getRoomDatabase(databaseBuilder()) }
