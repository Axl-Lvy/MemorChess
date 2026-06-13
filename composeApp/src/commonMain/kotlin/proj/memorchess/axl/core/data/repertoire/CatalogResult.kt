package proj.memorchess.axl.core.data.repertoire

/** Outcome of a [RepertoireCatalogClient] call. */
sealed class CatalogResult<out T> {

  /** The request succeeded and [value] passed validation. */
  data class Ok<out T>(val value: T) : CatalogResult<T>()

  /** The request failed before an HTTP status was obtained, for example no connectivity. */
  data class NetworkError(val message: String) : CatalogResult<Nothing>()

  /** The server answered with a non success HTTP [status], for example 404. */
  data class HttpError(val status: Int) : CatalogResult<Nothing>()

  /** The manifest was downloaded but its JSON is invalid or its schema version is unsupported. */
  data class MalformedManifest(val message: String) : CatalogResult<Nothing>()

  /** The PGN file was downloaded but does not parse or contains no moves. */
  data class MalformedPgn(val message: String) : CatalogResult<Nothing>()
}
