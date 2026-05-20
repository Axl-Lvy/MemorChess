package proj.memorchess.axl.core.data.explorer

import kotlinx.serialization.json.Json

private val cache: ExplorerCache by lazy { JsExplorerCache(Json { ignoreUnknownKeys = true }) }

actual fun getPlatformSpecificExplorerCache(): ExplorerCache = cache
