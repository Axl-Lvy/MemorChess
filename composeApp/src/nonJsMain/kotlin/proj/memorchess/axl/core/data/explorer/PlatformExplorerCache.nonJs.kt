package proj.memorchess.axl.core.data.explorer

import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.data.customDatabase

private val cache: ExplorerCache by lazy {
  NonJsExplorerCache(customDatabase, Json { ignoreUnknownKeys = true })
}

actual fun getPlatformSpecificExplorerCache(): ExplorerCache = cache
