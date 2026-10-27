package proj.memorchess.axl.server.repertoire

import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.data.repertoire.RepertoireDescriptor

/** Body of `POST /admin/repertoires/{id}/status`. */
@Serializable internal data class RepertoireStatusRequest(val status: RepertoireStatus)

/** Body of `GET /v1/repertoires`. */
@Serializable
internal data class RepertoireCatalogPage(
  val nextCursor: String?,
  val repertoires: List<RepertoireDescriptor>,
)

/** Maps a stored version to the public catalog contract. */
internal fun RepertoireRow.toDescriptor(downloadCount: Long = 0): RepertoireDescriptor =
  RepertoireDescriptor(
    id = id,
    name = title,
    color = side,
    description = description,
    moveCount = moveCount,
    version = version,
    downloadCount = downloadCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
  )
