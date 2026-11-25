package proj.memorchess.axl.core.data

/** Runs [PositionKeyStorageContract] against the browser's IndexedDB. */
class TestIndexedDbPackedKeys : PositionKeyStorageContract() {

  override suspend fun freshManager(): DatabaseQueryManager =
    getPlatformSpecificLocalDatabase().also { it.eraseAll() }
}
