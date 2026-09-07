package proj.memorchess.axl.core.data.repertoire

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.auth.TokenResult
import proj.memorchess.axl.core.pgn.RepertoireExportResult

/**
 * Drives one repertoire's publish lifecycle: publish, update (republish under the same slug), and
 * unpublish. Mirrors `RepertoireLibraryViewModel`'s shape: the collaborators are injected as
 * suspending functions so tests can substitute trivial fakes, production wiring binds
 * [RepertoirePgnExporter.export], [proj.memorchess.axl.core.auth.AuthProvider.accessToken],
 * [RepertoirePublishClient.publish] and [RepertoirePublishClient.remove].
 *
 * @param localId The repertoire's local id (`DataRepertoire.id`), distinct from its public slug.
 * @param scope Scope tied to the screen's lifecycle (use `rememberCoroutineScope` in Compose).
 */
class RepertoirePublishViewModel(
  private val localId: String,
  private val exportRepertoire: suspend (localId: String) -> RepertoireExportResult,
  private val accessToken: suspend () -> TokenResult,
  private val publish:
    suspend (
      accessToken: String,
      slug: String,
      title: String,
      description: String,
      side: String,
      pgn: String,
    ) -> PublishOutcome,
  private val remove: suspend (accessToken: String, slug: String) -> RemoveOutcome,
  private val publishedStore: PublishedRepertoireStore,
  private val scope: CoroutineScope,
) {

  private val internalState =
    MutableStateFlow<PublishState>(
      publishedStore.publishedSlug(localId)?.let { PublishState.Published(it) }
        ?: PublishState.NotPublished
    )

  /** Current publish lifecycle state of [localId]. */
  val state: StateFlow<PublishState> = internalState.asStateFlow()

  /** Publishes (or republishes, reusing [slug] as an update) [localId] under [slug]. */
  fun publish(slug: String, title: String, description: String, side: String) {
    internalState.value = PublishState.Publishing
    scope.launch { runPublish(slug, title, description, side) }
  }

  /** Unpublishes [localId]'s currently published slug. A no-op state wise when never published. */
  fun remove() {
    val slug = (internalState.value as? PublishState.Published)?.slug ?: return
    internalState.value = PublishState.Removing
    scope.launch { runRemove(slug) }
  }

  private suspend fun runPublish(slug: String, title: String, description: String, side: String) {
    val token = resolveToken() ?: return
    val export = exportRepertoire(localId)
    if (export !is RepertoireExportResult.Pgn) {
      internalState.value = PublishState.Failed(PublishError.NothingToPublish)
      return
    }
    val capProblem = exportCapProblem(export)
    if (capProblem != null) {
      internalState.value = PublishState.Failed(capProblem)
      return
    }
    when (val outcome = publish(token, slug, title, description, side, export.text)) {
      is PublishOutcome.Published -> {
        publishedStore.recordPublished(localId, slug)
        internalState.value = PublishState.Published(slug)
      }
      is PublishOutcome.InvalidPayload -> fail(PublishError.InvalidPayload(outcome.reason))
      is PublishOutcome.PayloadTooLarge -> fail(PublishError.PayloadTooLarge(outcome.reason))
      PublishOutcome.Forbidden -> fail(PublishError.Forbidden)
      PublishOutcome.RemovedOnServer -> {
        publishedStore.clearPublished(localId)
        fail(PublishError.RemovedOnServer)
      }
      is PublishOutcome.QuotaExceeded -> fail(PublishError.QuotaExceeded(outcome.reason))
      PublishOutcome.Unauthorized -> fail(PublishError.SignedOut)
      PublishOutcome.RateLimited -> fail(PublishError.RateLimited)
      is PublishOutcome.Failed -> fail(PublishError.ServerError)
    }
  }

  private suspend fun runRemove(slug: String) {
    val token = resolveToken() ?: return
    when (remove(token, slug)) {
      RemoveOutcome.Removed,
      RemoveOutcome.NotFound -> {
        publishedStore.clearPublished(localId)
        internalState.value = PublishState.NotPublished
      }
      RemoveOutcome.Forbidden -> fail(PublishError.Forbidden)
      RemoveOutcome.Unauthorized -> fail(PublishError.SignedOut)
      RemoveOutcome.RateLimited -> fail(PublishError.RateLimited)
      is RemoveOutcome.Failed -> fail(PublishError.ServerError)
    }
  }

  /**
   * Checks [export] against the shared [RepertoirePublishLimits] before it ever reaches the
   * network: a plain comparison of the payload's byte size, distinct move count, and deepest line,
   * never a second parse and walk (the exporter already computed all three). Returns `null` when
   * every cap is satisfied.
   */
  private fun exportCapProblem(export: RepertoireExportResult.Pgn): PublishError? {
    val payloadBytes = export.text.encodeToByteArray().size
    return when {
      payloadBytes > RepertoirePublishLimits.MAX_REPERTOIRE_PAYLOAD_BYTES ->
        PublishError.PayloadTooLarge(
          "the payload is $payloadBytes bytes, the cap is" +
            " ${RepertoirePublishLimits.MAX_REPERTOIRE_PAYLOAD_BYTES}"
        )
      export.moveCount > RepertoirePublishLimits.MAX_REPERTOIRE_MOVES ->
        PublishError.PayloadTooLarge(
          "this repertoire has ${export.moveCount} moves, the cap is" +
            " ${RepertoirePublishLimits.MAX_REPERTOIRE_MOVES}"
        )
      export.maxPlyDepth > RepertoirePublishLimits.MAX_PLY_DEPTH ->
        PublishError.PayloadTooLarge(
          "a line goes past ${RepertoirePublishLimits.MAX_PLY_DEPTH} plies deep"
        )
      else -> null
    }
  }

  /** Resolves a usable access token, or fails [internalState] and returns `null`. */
  private suspend fun resolveToken(): String? =
    when (val result = accessToken()) {
      is TokenResult.Ok -> result.accessToken
      TokenResult.Failed.Transient -> {
        fail(PublishError.RateLimited)
        null
      }
      // TokenResult.Failed.Terminal means the session is already cleared; SignedOut is the same
      // terminal state the UI shows for it.
      TokenResult.SignedOut,
      TokenResult.Failed.Terminal -> {
        fail(PublishError.SignedOut)
        null
      }
    }

  private fun fail(error: PublishError) {
    LOGGER.w { "Publish of $localId failed: $error" }
    internalState.value = PublishState.Failed(error)
  }
}

/** Publish lifecycle state of one repertoire. Consumed by Compose. */
sealed class PublishState {
  data object NotPublished : PublishState()

  data object Publishing : PublishState()

  data class Published(val slug: String) : PublishState()

  data object Removing : PublishState()

  data class Failed(val error: PublishError) : PublishState()
}

/** Reason a publish or remove attempt failed. */
sealed class PublishError {
  data object NothingToPublish : PublishError()

  data class InvalidPayload(val reason: String) : PublishError()

  data class PayloadTooLarge(val reason: String) : PublishError()

  data object Forbidden : PublishError()

  data object RemovedOnServer : PublishError()

  data class QuotaExceeded(val reason: String) : PublishError()

  data object SignedOut : PublishError()

  data object RateLimited : PublishError()

  data object ServerError : PublishError()
}

private val LOGGER = Logger.withTag("RepertoirePublishViewModel")
