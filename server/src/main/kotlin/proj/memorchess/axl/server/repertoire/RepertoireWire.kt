package proj.memorchess.axl.server.repertoire

import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.repertoire.RepertoireDescriptor

/**
 * Body of `POST /v1/repertoires`.
 *
 * @property id The catalog slug to publish or republish under.
 * @property side `"white"` or `"black"`.
 * @property pgn The repertoire payload, validated by [RepertoirePgnValidator] before it is stored.
 */
@Serializable
internal data class PublishRepertoireRequest(
  val id: String,
  val title: String,
  val description: String,
  val side: String,
  val pgn: String,
)

/** Body of `POST /admin/repertoires/{id}/status`. */
@Serializable internal data class RepertoireStatusRequest(val status: String)

/** Body of `GET /v1/repertoires`. */
@Serializable
internal data class RepertoireCatalogPage(
  val nextCursor: String?,
  val repertoires: List<RepertoireDescriptor>,
)

/**
 * Maps a stored version to the public catalog contract.
 *
 * The PGN path is content addressed by [RepertoireRow.payloadSha256] rather than by id and version,
 * so it never changes for an already published version and can be cached immutably.
 */
internal fun RepertoireRow.toDescriptor(): RepertoireDescriptor =
  RepertoireDescriptor(
    id = id,
    name = title,
    color = if (side == "white") RepertoireColor.WHITE else RepertoireColor.BLACK,
    description = description,
    moveCount = moveCount,
    file = "pgn/$payloadSha256.pgn",
  )
