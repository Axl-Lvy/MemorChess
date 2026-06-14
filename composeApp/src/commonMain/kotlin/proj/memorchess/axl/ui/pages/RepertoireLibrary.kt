package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Eye
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.library_color_black
import memorchess.composeapp.generated.resources.library_color_white
import memorchess.composeapp.generated.resources.library_empty
import memorchess.composeapp.generated.resources.library_error_http
import memorchess.composeapp.generated.resources.library_error_malformed
import memorchess.composeapp.generated.resources.library_error_network
import memorchess.composeapp.generated.resources.library_fetching
import memorchess.composeapp.generated.resources.library_importing
import memorchess.composeapp.generated.resources.library_install
import memorchess.composeapp.generated.resources.library_install_error_http
import memorchess.composeapp.generated.resources.library_install_error_import
import memorchess.composeapp.generated.resources.library_install_error_malformed_pgn
import memorchess.composeapp.generated.resources.library_install_error_network
import memorchess.composeapp.generated.resources.library_install_summary
import memorchess.composeapp.generated.resources.library_installed_badge
import memorchess.composeapp.generated.resources.library_move_count
import memorchess.composeapp.generated.resources.library_preview_checking
import memorchess.composeapp.generated.resources.library_preview_in_common
import memorchess.composeapp.generated.resources.library_preview_in_common_error
import memorchess.composeapp.generated.resources.library_preview_question
import memorchess.composeapp.generated.resources.library_reinstall
import memorchess.composeapp.generated.resources.library_retry
import memorchess.composeapp.generated.resources.library_stale_hint
import memorchess.composeapp.generated.resources.library_subtitle
import memorchess.composeapp.generated.resources.library_title
import memorchess.composeapp.generated.resources.library_view
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.repertoire.CachedRepertoireCatalog
import proj.memorchess.axl.core.data.repertoire.InstallError
import proj.memorchess.axl.core.data.repertoire.InstalledRepertoireStore
import proj.memorchess.axl.core.data.repertoire.LibraryCatalogState
import proj.memorchess.axl.core.data.repertoire.RepertoireCatalogClient
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.repertoire.RepertoireDescriptor
import proj.memorchess.axl.core.data.repertoire.RepertoireInstallState
import proj.memorchess.axl.core.data.repertoire.RepertoireLibraryViewModel
import proj.memorchess.axl.core.data.repertoire.RepertoirePreviewState
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImporter
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonLabel
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

private const val TEST_TAG_CARD = "library_repertoire_card"

/**
 * Repertoire library page. Lists the repertoires of the remote catalog and lets the user install
 * any of them into the opening graph after a metadata preview.
 *
 * All list and install state lives in [RepertoireLibraryViewModel]; this composable only wires the
 * Koin collaborators into the view model and renders its flows.
 */
@Composable
fun RepertoireLibrary(
  catalog: CachedRepertoireCatalog = koinInject(),
  client: RepertoireCatalogClient = koinInject(),
  installedStore: InstalledRepertoireStore = koinInject(),
  treeStore: TreeStore = koinInject(),
) {
  val coroutineScope = rememberCoroutineScope()
  val viewModel =
    remember(catalog, client, installedStore, treeStore, coroutineScope) {
      RepertoireLibraryViewModel(
        loadManifest = catalog::getManifest,
        fetchPgn = client::fetchPgn,
        importGames = { color, games ->
          // The importer merges into the loaded tree, so refresh it from disk first.
          treeStore.load()
          PgnImporter(treeStore).import(games, color.toPlayer())
        },
        previewGames = { color, games ->
          // The overlap is read against the loaded tree, so refresh it from disk first.
          treeStore.load()
          PgnImporter(treeStore).preview(games, color.toPlayer())
        },
        installedStore = installedStore,
        scope = coroutineScope,
      )
    }
  val catalogState by viewModel.catalogState.collectAsState()
  val installStates by viewModel.installStates.collectAsState()
  val navigator = LocalNavigator.current
  val previewStates by viewModel.previewStates.collectAsState()
  RepertoireLibraryContent(
    catalogState = catalogState,
    installStates = installStates,
    previewStates = previewStates,
    actions =
      RepertoireLibraryActions(
        onInstall = viewModel::install,
        onPreviewRequest = viewModel::requestPreview,
        onRetry = viewModel::refresh,
        onView = { descriptor -> navigator.navigateTo(Route.RepertoireViewRoute(descriptor.id)) },
      ),
    modifier = Modifier.fillMaxSize().testTag(Route.LibraryRoute.getLabel()),
  )
}

/** Maps the catalog's [RepertoireColor] to the engine [Player] the importer classifies against. */
private fun RepertoireColor.toPlayer(): Player =
  when (this) {
    RepertoireColor.WHITE -> Player.WHITE
    RepertoireColor.BLACK -> Player.BLACK
  }

/**
 * User actions raised by [RepertoireLibraryContent], grouped so the content stays within the
 * parameter budget.
 *
 * @property onInstall Install (or reinstall) the given repertoire into the opening graph.
 * @property onPreviewRequest Request the move-overlap preview for the given repertoire.
 * @property onRetry Retry loading the catalog after a failure.
 * @property onView Open the read-only viewer for the given repertoire.
 */
internal data class RepertoireLibraryActions(
  val onInstall: (RepertoireDescriptor) -> Unit,
  val onPreviewRequest: (RepertoireDescriptor) -> Unit,
  val onRetry: () -> Unit,
  val onView: (RepertoireDescriptor) -> Unit = {},
)

/**
 * Stateless rendering of the library page. Split out from [RepertoireLibrary] so tests can drive
 * each [LibraryCatalogState] and [RepertoireInstallState] without standing up a full view model.
 */
@Composable
internal fun RepertoireLibraryContent(
  catalogState: LibraryCatalogState,
  installStates: Map<String, RepertoireInstallState>,
  previewStates: Map<String, RepertoirePreviewState>,
  actions: RepertoireLibraryActions,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val previewDialog = remember { ConfirmationDialog(okText = Res.string.library_install) }
  previewDialog.DrawDialog()
  // The dialog keeps the content lambda from the moment it was shown, so read the latest preview
  // through this holder to reflect the Loading -> Ready transition while the dialog stays open.
  val latestPreviewStates = rememberUpdatedState(previewStates)
  Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = stringResource(Res.string.library_title),
      style = typography.monoSm.copy(color = palette.accentText),
    )
    Text(
      text = stringResource(Res.string.library_subtitle),
      style = typography.bodySm.copy(color = palette.ink3),
    )
    when (catalogState) {
      is LibraryCatalogState.Loading ->
        Box(
          modifier = Modifier.fillMaxWidth().padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      is LibraryCatalogState.NetworkError ->
        CatalogError(
          message = stringResource(Res.string.library_error_network, catalogState.message),
          onRetry = actions.onRetry,
        )
      is LibraryCatalogState.HttpError ->
        CatalogError(
          message = stringResource(Res.string.library_error_http, catalogState.status),
          onRetry = actions.onRetry,
        )
      is LibraryCatalogState.MalformedManifest ->
        CatalogError(
          message = stringResource(Res.string.library_error_malformed, catalogState.message),
          onRetry = actions.onRetry,
        )
      is LibraryCatalogState.Loaded ->
        CatalogList(
          state = catalogState,
          installStates = installStates,
          onInstallRequest = { descriptor ->
            actions.onPreviewRequest(descriptor)
            previewDialog.show(confirm = { actions.onInstall(descriptor) }) {
              PreviewDialogContent(descriptor, latestPreviewStates.value[descriptor.id])
            }
          },
          onView = actions.onView,
        )
    }
  }
}

/** Error body for a catalog that could not be loaded at all, with a retry action. */
@Composable
private fun CatalogError(message: String, onRetry: () -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(text = message, style = typography.bodySm.copy(color = palette.red))
    KineticButton(onClick = onRetry) {
      KineticButtonLabel(stringResource(Res.string.library_retry))
    }
  }
}

/**
 * Loaded catalog body: a stale data hint when the list comes from an expired cache, an empty
 * message when the catalog lists nothing, and otherwise one card per repertoire.
 */
@Composable
private fun CatalogList(
  state: LibraryCatalogState.Loaded,
  installStates: Map<String, RepertoireInstallState>,
  onInstallRequest: (RepertoireDescriptor) -> Unit,
  onView: (RepertoireDescriptor) -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (state.isStale) {
      Text(
        text = stringResource(Res.string.library_stale_hint),
        style = typography.bodySm.copy(color = palette.accentText),
        modifier =
          Modifier.fillMaxWidth()
            .background(palette.panel2)
            .border(width = 1.dp, color = palette.line)
            .padding(8.dp),
      )
    }
    if (state.repertoires.isEmpty()) {
      Text(
        text = stringResource(Res.string.library_empty),
        style = typography.bodySm.copy(color = palette.ink3),
      )
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.repertoires, key = { it.id }) { descriptor ->
          RepertoireCard(
            descriptor = descriptor,
            installState = installStates[descriptor.id] ?: RepertoireInstallState.NotInstalled,
            onInstallRequest = { onInstallRequest(descriptor) },
            onView = { onView(descriptor) },
          )
        }
      }
    }
  }
}

/**
 * One catalog entry: name, color tag, installed badge, description, move count, and the install
 * action or its progress and outcome.
 */
@Composable
private fun RepertoireCard(
  descriptor: RepertoireDescriptor,
  installState: RepertoireInstallState,
  onInstallRequest: () -> Unit,
  onView: () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("$TEST_TAG_CARD:${descriptor.id}")
        .background(palette.panel)
        .border(width = 1.dp, color = palette.line)
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = descriptor.name,
        style = typography.display.copy(color = palette.ink),
        modifier = Modifier.weight(1f),
      )
      ColorTag(descriptor.color)
      if (installState is RepertoireInstallState.Installed) {
        InstalledBadge()
      }
      KineticButton(
        onClick = onView,
        iconOnly = true,
        modifier = Modifier.testTag("$TEST_TAG_CARD:${descriptor.id}:view"),
      ) {
        Icon(
          imageVector = FeatherIcons.Eye,
          contentDescription = stringResource(Res.string.library_view),
        )
      }
    }
    Text(text = descriptor.description, style = typography.bodySm.copy(color = palette.ink3))
    Text(
      text =
        pluralStringResource(
          Res.plurals.library_move_count,
          descriptor.moveCount,
          descriptor.moveCount,
        ),
      style = typography.monoSm.copy(color = palette.ink3),
    )
    InstallStatusRow(installState = installState, onInstallRequest = onInstallRequest)
  }
}

/** Small bordered tag naming the side the repertoire is built for. */
@Composable
private fun ColorTag(color: RepertoireColor) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val label =
    when (color) {
      RepertoireColor.WHITE -> stringResource(Res.string.library_color_white)
      RepertoireColor.BLACK -> stringResource(Res.string.library_color_black)
    }
  Text(
    text = label,
    style = typography.monoSm.copy(color = palette.ink2),
    modifier =
      Modifier.background(palette.panel2)
        .border(width = 1.dp, color = palette.lineBright)
        .padding(horizontal = 6.dp, vertical = 2.dp),
  )
}

/** Accent badge shown on cards whose repertoire is installed. */
@Composable
private fun InstalledBadge() {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Text(
    text = stringResource(Res.string.library_installed_badge),
    style = typography.monoSm.copy(color = palette.accentText),
    modifier =
      Modifier.border(width = 1.dp, color = palette.accent)
        .padding(horizontal = 6.dp, vertical = 2.dp),
  )
}

/**
 * Bottom row of a card. Walks every [RepertoireInstallState]: the install button when nothing has
 * happened yet, a progress line while fetching or importing, the import summary once installed in
 * this session (none when restored from persistence), and the failure message plus a retry button
 * after a failed attempt.
 */
@Composable
private fun InstallStatusRow(installState: RepertoireInstallState, onInstallRequest: () -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  when (installState) {
    is RepertoireInstallState.NotInstalled ->
      KineticButton(onClick = onInstallRequest, style = KineticButtonStyle.Primary) {
        KineticButtonLabel(stringResource(Res.string.library_install))
      }
    is RepertoireInstallState.Fetching ->
      InstallProgressLine(stringResource(Res.string.library_fetching))
    is RepertoireInstallState.Importing ->
      InstallProgressLine(stringResource(Res.string.library_importing))
    is RepertoireInstallState.Installed ->
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val summary = installState.summary
        if (summary != null) {
          Text(
            text =
              stringResource(
                Res.string.library_install_summary,
                summary.movesAdded,
                summary.movesAlreadyPresent,
              ),
            style = typography.bodySm.copy(color = palette.green),
          )
        }
        // Reinstalling restores moves the user has removed since installing.
        KineticButton(onClick = onInstallRequest, style = KineticButtonStyle.Primary) {
          KineticButtonLabel(stringResource(Res.string.library_reinstall))
        }
      }
    is RepertoireInstallState.Failed ->
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = installErrorText(installState.error),
          style = typography.bodySm.copy(color = palette.red),
        )
        KineticButton(onClick = onInstallRequest, style = KineticButtonStyle.Primary) {
          KineticButtonLabel(stringResource(Res.string.library_install))
        }
      }
  }
}

/** Progress indicator plus a label, shown while an install step is running. */
@Composable
private fun InstallProgressLine(label: String) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CircularProgressIndicator(modifier = Modifier.size(16.dp))
    Text(text = label, style = typography.bodySm.copy(color = palette.ink3))
  }
}

/** Resolves the localized message for an [InstallError]. */
@Composable
private fun installErrorText(error: InstallError): String =
  when (error) {
    is InstallError.Network ->
      stringResource(Res.string.library_install_error_network, error.message)
    is InstallError.Http -> stringResource(Res.string.library_install_error_http, error.status)
    is InstallError.MalformedPgn ->
      stringResource(Res.string.library_install_error_malformed_pgn, error.message)
    is InstallError.ImportFailed ->
      stringResource(Res.string.library_install_error_import, error.message)
  }

/**
 * Metadata preview shown in the confirmation dialog before an install starts, including the live
 * overlap of [previewState] (how many of the repertoire's moves the user already has).
 *
 * @param descriptor The repertoire being previewed.
 * @param previewState Overlap computation state, or `null` while none has been published yet.
 */
@Composable
private fun PreviewDialogContent(
  descriptor: RepertoireDescriptor,
  previewState: RepertoirePreviewState?,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = descriptor.name, style = typography.display.copy(color = palette.ink))
      ColorTag(descriptor.color)
    }
    Text(text = descriptor.description, style = typography.bodySm.copy(color = palette.ink2))
    Text(
      text =
        pluralStringResource(
          Res.plurals.library_move_count,
          descriptor.moveCount,
          descriptor.moveCount,
        ),
      style = typography.monoSm.copy(color = palette.ink3),
    )
    PreviewOverlap(previewState)
    Text(
      text = stringResource(Res.string.library_preview_question),
      style = typography.bodySm.copy(color = palette.ink3),
    )
  }
}

/**
 * The "moves in common" line of the preview dialog. Renders a checking hint while the overlap loads
 * (also the `null` not-yet-requested case), the count once ready, and a muted notice on failure so
 * a download error never blocks the install. A repertoire with no moves shows nothing.
 */
@Composable
private fun PreviewOverlap(previewState: RepertoirePreviewState?) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  when (previewState) {
    null,
    is RepertoirePreviewState.Loading ->
      InstallProgressLine(stringResource(Res.string.library_preview_checking))
    is RepertoirePreviewState.Ready -> {
      val preview = previewState.preview
      if (preview.totalMoves > 0) {
        Text(
          text =
            stringResource(
              Res.string.library_preview_in_common,
              preview.movesInCommon,
              preview.totalMoves,
            ),
          style = typography.bodySm.copy(color = palette.accentText),
        )
      }
    }
    is RepertoirePreviewState.Failed ->
      Text(
        text = stringResource(Res.string.library_preview_in_common_error),
        style = typography.bodySm.copy(color = palette.ink3),
      )
  }
}
