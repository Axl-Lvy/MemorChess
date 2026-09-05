@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package proj.memorchess.axl.core.auth

/** Parses and URL-decodes a `location.search` string (leading `?` optional) into a param map. */
internal fun parseDecodedQuery(search: String): Map<String, String> {
  if (search.isEmpty()) return emptyMap()
  return search
    .removePrefix("?")
    .split('&')
    .mapNotNull {
      val idx = it.indexOf('=')
      if (idx <= 0) null else it.substring(0, idx) to decodeUriComponent(it.substring(idx + 1))
    }
    .toMap()
}

private fun decodeUriComponent(value: String): String = decodeUriComponentJs(value)

// `value` is referenced inside the `js(...)` snippet, not by real Kotlin code, so static analysis
// sees it as unused; same pattern already accepted in OAuthLauncher.wasmJs.kt.
@Suppress("UNUSED_PARAMETER")
private fun decodeUriComponentJs(value: String): String = js("globalThis.decodeURIComponent(value)")
