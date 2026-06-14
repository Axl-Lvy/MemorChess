package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.library_error_http
import memorchess.composeapp.generated.resources.library_error_malformed
import memorchess.composeapp.generated.resources.library_error_network
import memorchess.composeapp.generated.resources.library_install_error_import
import memorchess.composeapp.generated.resources.library_install_error_malformed_pgn
import memorchess.composeapp.generated.resources.library_retry
import memorchess.composeapp.generated.resources.repertoire_view_not_found
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.explorer.CachedExplorer
import proj.memorchess.axl.core.data.repertoire.CachedManifestResult
import proj.memorchess.axl.core.data.repertoire.CachedRepertoireCatalog
import proj.memorchess.axl.core.data.repertoire.CatalogResult
import proj.memorchess.axl.core.data.repertoire.RepertoireCatalogClient
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.repertoire.RepertoireDescriptor
import proj.memorchess.axl.core.interactions.RepertoireExplorer
import proj.memorchess.axl.core.pgn.PgnImportException
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonLabel
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Test tag identifying the repertoire viewer page root. */
const val REPERTOIRE_VIEW_TEST_TAG = "repertoire_view"

/**
 * Read only viewer for a single catalog repertoire.
 *
 * Fetches the repertoire's PGN every time it opens (no offline copy is kept), replays it into a
 * throwaway opening graph and lets the user navigate it on a board through [ExplorerContent] in
 * read-only mode: no save or delete, and only moves present in the PGN can be played. Works for any
 * repertoire whether or not it is installed, so it doubles as a preview before install.
 *
 * @param repertoireId Id of the repertoire to display, as listed in the catalog manifest.
 * @param catalog Resolves the descriptor (name, color, PGN path) from the manifest.
 * @param client Downloads and parses the PGN.
 * @param cachedExplorer Backs the Lichess opening explorer panels of [ExplorerContent].
 */
@Composable
fun RepertoireView(
  repertoireId: String,
  catalog: CachedRepertoireCatalog = koinInject(),
  client: RepertoireCatalogClient = koinInject(),
  cachedExplorer: CachedExplorer = koinInject(),
) {
  var reloadKey by remember { mutableStateOf(0) }
  var state by
    remember(repertoireId) { mutableStateOf<RepertoireViewState>(RepertoireViewState.Loading) }
  LaunchedEffect(repertoireId, reloadKey) {
    state = RepertoireViewState.Loading
    state = loadRepertoire(repertoireId, catalog, client)
  }

  Box(
    modifier = Modifier.fillMaxSize().testTag(REPERTOIRE_VIEW_TEST_TAG),
    contentAlignment = Alignment.Center,
  ) {
    when (val current = state) {
      is RepertoireViewState.Loading -> CircularProgressIndicator()
      is RepertoireViewState.Error ->
        RepertoireViewError(error = current.error, onRetry = { reloadKey++ })
      is RepertoireViewState.Ready ->
        RepertoireViewReady(
          explorer = current.explorer,
          descriptor = current.descriptor,
          cachedExplorer = cachedExplorer,
        )
    }
  }
}

/** Wires the explorer view model and renders the read-only [ExplorerContent]. */
@Composable
private fun RepertoireViewReady(
  explorer: RepertoireExplorer,
  descriptor: RepertoireDescriptor,
  cachedExplorer: CachedExplorer,
) {
  val explorerViewModel = rememberExplorerViewModel(explorer, cachedExplorer)
  ExplorerContent(
    explorer = explorer,
    explorerViewModel = explorerViewModel,
    onSave = {},
    onDelete = {},
    viewerMode =
      ExplorerViewerMode(
        initialInverted = descriptor.color == RepertoireColor.BLACK,
        cornerTag = descriptor.name,
      ),
  )
}

/** Error body with a retry action, mirroring the catalog error styling of the library page. */
@Composable
private fun RepertoireViewError(error: RepertoireViewError, onRetry: () -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(
    modifier = Modifier.padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = error.resolveMessage(), style = typography.bodySm.copy(color = palette.red))
    KineticButton(onClick = onRetry) {
      KineticButtonLabel(stringResource(Res.string.library_retry))
    }
  }
}

/** Resolves the localized message for a [RepertoireViewError]. */
@Composable
private fun RepertoireViewError.resolveMessage(): String =
  when (this) {
    is RepertoireViewError.Network -> stringResource(Res.string.library_error_network, message)
    is RepertoireViewError.Http -> stringResource(Res.string.library_error_http, status)
    is RepertoireViewError.MalformedManifest ->
      stringResource(Res.string.library_error_malformed, message)
    is RepertoireViewError.MalformedPgn ->
      stringResource(Res.string.library_install_error_malformed_pgn, message)
    is RepertoireViewError.Import ->
      stringResource(Res.string.library_install_error_import, message)
    is RepertoireViewError.NotFound -> stringResource(Res.string.repertoire_view_not_found, id)
  }

/**
 * Loads the repertoire: resolves its descriptor from the manifest, downloads and parses its PGN,
 * then builds the read-only explorer. Every failure maps to a typed [RepertoireViewError].
 */
private suspend fun loadRepertoire(
  repertoireId: String,
  catalog: CachedRepertoireCatalog,
  client: RepertoireCatalogClient,
): RepertoireViewState {
  val manifest =
    when (val result = catalog.getManifest()) {
      is CachedManifestResult.Fresh -> result.manifest
      is CachedManifestResult.Stale -> result.manifest
      is CachedManifestResult.NetworkError ->
        return RepertoireViewState.Error(RepertoireViewError.Network(result.message))
      is CachedManifestResult.HttpError ->
        return RepertoireViewState.Error(RepertoireViewError.Http(result.status))
      is CachedManifestResult.MalformedManifest ->
        return RepertoireViewState.Error(RepertoireViewError.MalformedManifest(result.message))
    }
  val descriptor =
    manifest.repertoires.find { it.id == repertoireId }
      ?: return RepertoireViewState.Error(RepertoireViewError.NotFound(repertoireId))

  val games =
    when (val result = client.fetchPgn(descriptor.file)) {
      is CatalogResult.Ok -> result.value
      is CatalogResult.NetworkError ->
        return RepertoireViewState.Error(RepertoireViewError.Network(result.message))
      is CatalogResult.HttpError ->
        return RepertoireViewState.Error(RepertoireViewError.Http(result.status))
      is CatalogResult.MalformedPgn ->
        return RepertoireViewState.Error(RepertoireViewError.MalformedPgn(result.message))
      // A PGN fetch never reports MalformedManifest; mapped defensively for exhaustiveness.
      is CatalogResult.MalformedManifest ->
        return RepertoireViewState.Error(RepertoireViewError.MalformedPgn(result.message))
    }

  return try {
    RepertoireViewState.Ready(RepertoireExplorer.build(games), descriptor)
  } catch (e: PgnImportException) {
    RepertoireViewState.Error(RepertoireViewError.Import(e.message ?: "Import failed"))
  }
}

/** Loading lifecycle of the repertoire viewer. */
private sealed interface RepertoireViewState {

  /** The descriptor and PGN are being fetched. */
  data object Loading : RepertoireViewState

  /** Loading failed; [error] describes how. */
  data class Error(val error: RepertoireViewError) : RepertoireViewState

  /** The PGN is loaded and [explorer] is ready to navigate [descriptor]. */
  data class Ready(val explorer: RepertoireExplorer, val descriptor: RepertoireDescriptor) :
    RepertoireViewState
}

/** Reason a repertoire could not be opened. */
private sealed interface RepertoireViewError {

  /** Manifest or PGN download failed before an HTTP status was obtained. */
  data class Network(val message: String) : RepertoireViewError

  /** A download answered with a non success HTTP [status]. */
  data class Http(val status: Int) : RepertoireViewError

  /** The manifest is invalid. */
  data class MalformedManifest(val message: String) : RepertoireViewError

  /** The PGN does not parse or contains no moves. */
  data class MalformedPgn(val message: String) : RepertoireViewError

  /** The PGN parsed but contains an illegal move and could not be replayed. */
  data class Import(val message: String) : RepertoireViewError

  /** No repertoire with [id] exists in the manifest. */
  data class NotFound(val id: String) : RepertoireViewError
}
