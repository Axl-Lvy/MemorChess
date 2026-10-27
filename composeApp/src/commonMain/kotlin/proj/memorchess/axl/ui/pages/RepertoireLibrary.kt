package proj.memorchess.axl.ui.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Eye
import compose.icons.feathericons.Plus
import compose.icons.feathericons.RefreshCw
import kotlinx.coroutines.CoroutineScope
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.library_badge_in_training
import memorchess.composeapp.generated.resources.library_badge_new
import memorchess.composeapp.generated.resources.library_color_black
import memorchess.composeapp.generated.resources.library_color_white
import memorchess.composeapp.generated.resources.library_create
import memorchess.composeapp.generated.resources.library_create_error_blank_name
import memorchess.composeapp.generated.resources.library_create_error_duplicate_id
import memorchess.composeapp.generated.resources.library_create_error_invalid_pgn
import memorchess.composeapp.generated.resources.library_empty
import memorchess.composeapp.generated.resources.library_error_http
import memorchess.composeapp.generated.resources.library_error_malformed
import memorchess.composeapp.generated.resources.library_error_network
import memorchess.composeapp.generated.resources.library_explore
import memorchess.composeapp.generated.resources.library_fetching
import memorchess.composeapp.generated.resources.library_filter_all
import memorchess.composeapp.generated.resources.library_filter_black
import memorchess.composeapp.generated.resources.library_filter_empty
import memorchess.composeapp.generated.resources.library_filter_mine
import memorchess.composeapp.generated.resources.library_filter_white
import memorchess.composeapp.generated.resources.library_fork
import memorchess.composeapp.generated.resources.library_hero_badge
import memorchess.composeapp.generated.resources.library_hero_cta
import memorchess.composeapp.generated.resources.library_hero_progress
import memorchess.composeapp.generated.resources.library_importing
import memorchess.composeapp.generated.resources.library_install
import memorchess.composeapp.generated.resources.library_install_error_http
import memorchess.composeapp.generated.resources.library_install_error_id_taken
import memorchess.composeapp.generated.resources.library_install_error_import
import memorchess.composeapp.generated.resources.library_install_error_malformed_repertoire
import memorchess.composeapp.generated.resources.library_install_error_network
import memorchess.composeapp.generated.resources.library_install_summary
import memorchess.composeapp.generated.resources.library_move_count
import memorchess.composeapp.generated.resources.library_my_repertoires_title
import memorchess.composeapp.generated.resources.library_preview_checking
import memorchess.composeapp.generated.resources.library_preview_in_common
import memorchess.composeapp.generated.resources.library_preview_in_common_error
import memorchess.composeapp.generated.resources.library_preview_question
import memorchess.composeapp.generated.resources.library_publish
import memorchess.composeapp.generated.resources.library_publish_error_forbidden
import memorchess.composeapp.generated.resources.library_publish_error_invalid
import memorchess.composeapp.generated.resources.library_publish_error_nothing_to_publish
import memorchess.composeapp.generated.resources.library_publish_error_quota
import memorchess.composeapp.generated.resources.library_publish_error_rate_limited
import memorchess.composeapp.generated.resources.library_publish_error_removed
import memorchess.composeapp.generated.resources.library_publish_error_server
import memorchess.composeapp.generated.resources.library_publish_error_signed_out
import memorchess.composeapp.generated.resources.library_publish_error_too_large
import memorchess.composeapp.generated.resources.library_refresh
import memorchess.composeapp.generated.resources.library_reinstall
import memorchess.composeapp.generated.resources.library_retry
import memorchess.composeapp.generated.resources.library_stale_hint
import memorchess.composeapp.generated.resources.library_subtitle
import memorchess.composeapp.generated.resources.library_title
import memorchess.composeapp.generated.resources.library_train
import memorchess.composeapp.generated.resources.library_unpublish
import memorchess.composeapp.generated.resources.library_unpublish_confirm_message
import memorchess.composeapp.generated.resources.library_unpublish_confirm_title
import memorchess.composeapp.generated.resources.library_update
import memorchess.composeapp.generated.resources.library_view
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.data.DataRepertoire
import proj.memorchess.axl.core.data.RepertoireMasterySnapshot
import proj.memorchess.axl.core.data.repertoire.CachedRepertoireCatalog
import proj.memorchess.axl.core.data.repertoire.CreationError
import proj.memorchess.axl.core.data.repertoire.CreationState
import proj.memorchess.axl.core.data.repertoire.InstallError
import proj.memorchess.axl.core.data.repertoire.LibraryCatalogState
import proj.memorchess.axl.core.data.repertoire.PublishError
import proj.memorchess.axl.core.data.repertoire.PublishState
import proj.memorchess.axl.core.data.repertoire.PublishedRepertoireStore
import proj.memorchess.axl.core.data.repertoire.RepertoireCatalogClient
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.repertoire.RepertoireCreationViewModel
import proj.memorchess.axl.core.data.repertoire.RepertoireDescriptor
import proj.memorchess.axl.core.data.repertoire.RepertoireInstallState
import proj.memorchess.axl.core.data.repertoire.RepertoireLibraryViewModel
import proj.memorchess.axl.core.data.repertoire.RepertoirePreviewState
import proj.memorchess.axl.core.data.repertoire.RepertoirePublishClient
import proj.memorchess.axl.core.data.repertoire.RepertoirePublishViewModel
import proj.memorchess.axl.core.data.repertoire.slugify
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.RepertoireMoveExporter
import proj.memorchess.axl.core.graph.RepertoireMoveImporter
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImporter
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonLabel
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.buttons.KineticOnAccentLime
import proj.memorchess.axl.ui.components.buttons.kineticAccentLimeColor
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.components.repertoire.CreateRepertoireDialog
import proj.memorchess.axl.ui.components.repertoire.ForkRepertoireDialog
import proj.memorchess.axl.ui.components.repertoire.PublishRepertoireDialog
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.KineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

private const val TEST_TAG_CARD = "library_repertoire_card"
private const val TEST_TAG_MY_REPERTOIRE_ROW = "library_my_repertoire_row"

private const val CATALOG_SKELETON_ROWS = 3

/**
 * Repertoire library page. Lists the repertoires of the remote catalog and lets the user install
 * any of them into the opening graph after a metadata preview.
 */
@Composable
fun RepertoireLibrary(
  catalog: CachedRepertoireCatalog = koinInject(),
  client: RepertoireCatalogClient = koinInject(),
  treeStore: TreeStore = koinInject(),
  tagStore: RepertoireTagStore = koinInject(),
) {
  val coroutineScope = rememberCoroutineScope()
  val viewModel =
    remember(catalog, client, treeStore, tagStore, coroutineScope) {
      RepertoireLibraryViewModel(
        loadManifest = catalog::getManifest,
        reloadManifest = catalog::refresh,
        fetchMoves = { descriptor, onProgress ->
          client.fetchMoves(descriptor.id, descriptor.version, onProgress)
        },
        importMoves = { descriptor, moves, onProgress ->
          RepertoireMoveImporter(treeStore, tagStore).import(moves, descriptor.id, onProgress)
        },
        previewMoves = { moves -> RepertoireMoveImporter(treeStore, tagStore).preview(moves) },
        reportInstall = client::reportInstall,
        loadRepertoire = tagStore::repertoire,
        registerInstall = { descriptor ->
          tagStore.register(
            descriptor.id,
            descriptor.name,
            descriptor.color,
            installedVersion = descriptor.version,
          )
        },
        loadMyRepertoires = tagStore::repertoires,
        loadMasterySnapshots = tagStore::masterySnapshots,
        scope = coroutineScope,
      )
    }
  val catalogState by viewModel.catalogState.collectAsState()
  val installStates by viewModel.installStates.collectAsState()
  val navigator = LocalNavigator.current
  val previewStates by viewModel.previewStates.collectAsState()
  val myRepertoires by viewModel.myRepertoires.collectAsState()
  val masterySnapshots by viewModel.masterySnapshots.collectAsState()
  RepertoireLibraryContent(
    catalogState = catalogState,
    installStates = installStates,
    previewStates = previewStates,
    myRepertoires = myRepertoires,
    masterySnapshots = masterySnapshots,
    installedIds = myRepertoires.filter { it.installedVersion != null }.map { it.id }.toSet(),
    actions =
      RepertoireLibraryActions(
        onInstall = viewModel::install,
        onPreviewRequest = viewModel::requestPreview,
        onRetry = viewModel::refresh,
        onRefresh = viewModel::forceRefresh,
        onView = { descriptor -> navigator.navigateTo(Route.RepertoireViewRoute(descriptor.id)) },
        onExplore = { id -> navigator.navigateTo(Route.ExploreRoute(repertoireId = id)) },
        onTrain = { id -> navigator.navigateTo(Route.TrainingRoute(repertoireId = id)) },
        onRepertoireListChanged = viewModel::refreshMyRepertoires,
      ),
    modifier = Modifier.fillMaxSize().testTag(Route.LibraryRoute.getLabel()),
  )
}

private fun RepertoireColor.toPlayer(): Player =
  when (this) {
    RepertoireColor.WHITE -> Player.WHITE
    RepertoireColor.BLACK -> Player.BLACK
  }

/** User actions raised by [RepertoireLibraryContent]. */
internal data class RepertoireLibraryActions(
  val onInstall: (RepertoireDescriptor) -> Unit,
  val onPreviewRequest: (RepertoireDescriptor) -> Unit,
  val onRetry: () -> Unit,
  val onRefresh: () -> Unit = {},
  val onView: (RepertoireDescriptor) -> Unit = {},
  val onExplore: (repertoireId: String) -> Unit = {},
  val onTrain: (repertoireId: String) -> Unit = {},
  val onRepertoireListChanged: () -> Unit = {},
)

/** Stateless rendering of [RepertoireLibrary]. */
@Composable
internal fun RepertoireLibraryContent(
  catalogState: LibraryCatalogState,
  installStates: Map<String, RepertoireInstallState>,
  previewStates: Map<String, RepertoirePreviewState>,
  myRepertoires: List<DataRepertoire>,
  actions: RepertoireLibraryActions,
  masterySnapshots: Map<String, RepertoireMasterySnapshot> = emptyMap(),
  installedIds: Set<String> = emptySet(),
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
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(Res.string.library_title),
        style = typography.labelSm.copy(color = palette.actionText),
      )
      KineticButton(
        onClick = actions.onRefresh,
        iconOnly = true,
        modifier = Modifier.testTag("library_refresh_button"),
      ) {
        Icon(
          imageVector = FeatherIcons.RefreshCw,
          contentDescription = stringResource(Res.string.library_refresh),
        )
      }
    }
    Text(
      text = stringResource(Res.string.library_subtitle),
      style = typography.bodySm.copy(color = palette.ink3),
    )
    MyRepertoiresSection(
      repertoires = myRepertoires,
      installedIds = installedIds,
      onExplore = actions.onExplore,
      onTrain = actions.onTrain,
      onRepertoireListChanged = actions.onRepertoireListChanged,
    )
    when (catalogState) {
      is LibraryCatalogState.Loading ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          repeat(CATALOG_SKELETON_ROWS) {
            SkeletonBlock(
              modifier = Modifier.fillMaxWidth().height(88.dp),
              shape = MaterialTheme.shapes.medium,
              color = palette.panel2,
              testTag = "library_catalog_skeleton",
            )
          }
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
          previewStates = previewStates,
          masterySnapshots = masterySnapshots,
          onPreviewRequest = actions.onPreviewRequest,
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

/** The user's own repertoires, each with Explore and Train actions scoped to it. */
@Composable
private fun MyRepertoiresSection(
  repertoires: List<DataRepertoire>,
  installedIds: Set<String>,
  onExplore: (repertoireId: String) -> Unit,
  onTrain: (repertoireId: String) -> Unit,
  onRepertoireListChanged: () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(Res.string.library_my_repertoires_title),
        style = typography.labelSm.copy(color = palette.actionText),
      )
      CreateRepertoireAction(onRepertoireListChanged = onRepertoireListChanged)
    }
    for (repertoire in repertoires) {
      MyRepertoireRow(
        name = repertoire.name,
        id = repertoire.id,
        color = repertoire.color,
        // An installed repertoire is not the user's own, and a mixed-color one has no single side.
        canPublish = repertoire.id !in installedIds && repertoire.color != null,
        onExplore = onExplore,
        onTrain = onTrain,
        onRepertoireListChanged = onRepertoireListChanged,
      )
    }
  }
}

/**
 * One row of [MyRepertoiresSection]: the repertoire's name, its Explore/Train/Fork buttons, and,
 * for a self authored single sided repertoire ([canPublish]), its Publish/Update/Unpublish action.
 */
@Composable
private fun MyRepertoireRow(
  name: String,
  id: String,
  color: RepertoireColor?,
  canPublish: Boolean,
  onExplore: (repertoireId: String) -> Unit,
  onTrain: (repertoireId: String) -> Unit,
  onRepertoireListChanged: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().testTag("$TEST_TAG_MY_REPERTOIRE_ROW:$id"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = name)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      KineticButton(onClick = { onExplore(id) }) {
        KineticButtonLabel(stringResource(Res.string.library_explore))
      }
      KineticButton(onClick = { onTrain(id) }) {
        KineticButtonLabel(stringResource(Res.string.library_train))
      }
      ForkRepertoireAction(
        sourceId = id,
        sourceName = name,
        sourceColor = color,
        onRepertoireListChanged = onRepertoireListChanged,
      )
      if (canPublish) {
        PublishAction(localId = id, name = name, color = color!!)
      }
    }
  }
}

/** "Create repertoire" icon action; calls [onRepertoireListChanged] once a create completes. */
@Composable
private fun CreateRepertoireAction(onRepertoireListChanged: () -> Unit) {
  val treeStore: TreeStore = koinInject()
  val tagStore: RepertoireTagStore = koinInject()
  val coroutineScope = rememberCoroutineScope()
  val viewModel =
    remember(treeStore, tagStore, coroutineScope) {
      repertoireCreationViewModel(treeStore, tagStore, coroutineScope)
    }
  val state by viewModel.state.collectAsState()
  var showDialog by remember { mutableStateOf(false) }
  val presentation = creationDialogPresentation(state)
  LaunchedEffect(state) {
    if (presentation.done) {
      showDialog = false
      onRepertoireListChanged()
      viewModel.reset()
    }
  }

  KineticButton(
    onClick = { showDialog = true },
    iconOnly = true,
    modifier = Modifier.testTag("library_create_button"),
  ) {
    Icon(
      imageVector = FeatherIcons.Plus,
      contentDescription = stringResource(Res.string.library_create),
    )
  }
  CreateRepertoireDialog(
    visible = showDialog,
    working = presentation.working,
    errorMessage = presentation.errorMessage,
    onSubmit = { name, color, pgnText -> viewModel.create(name, color, pgnText) },
    onDismiss = {
      showDialog = false
      viewModel.reset()
    },
  )
}

/** "Fork repertoire" icon action duplicating [sourceId]'s tagged edges into a new repertoire. */
@Composable
private fun ForkRepertoireAction(
  sourceId: String,
  sourceName: String,
  sourceColor: RepertoireColor?,
  onRepertoireListChanged: () -> Unit,
) {
  val treeStore: TreeStore = koinInject()
  val tagStore: RepertoireTagStore = koinInject()
  val coroutineScope = rememberCoroutineScope()
  val viewModel =
    remember(sourceId, treeStore, tagStore, coroutineScope) {
      repertoireCreationViewModel(treeStore, tagStore, coroutineScope)
    }
  val state by viewModel.state.collectAsState()
  var showDialog by remember { mutableStateOf(false) }
  val presentation = creationDialogPresentation(state)
  LaunchedEffect(state) {
    if (presentation.done) {
      showDialog = false
      onRepertoireListChanged()
      viewModel.reset()
    }
  }

  KineticButton(
    onClick = { showDialog = true },
    iconOnly = true,
    modifier = Modifier.testTag("library_fork_button:$sourceId"),
  ) {
    Icon(
      imageVector = FeatherIcons.Copy,
      contentDescription = stringResource(Res.string.library_fork),
    )
  }
  ForkRepertoireDialog(
    visible = showDialog,
    sourceName = sourceName,
    sourceColor = sourceColor,
    working = presentation.working,
    errorMessage = presentation.errorMessage,
    onSubmit = { newName, color -> viewModel.fork(sourceId, newName, color) },
    onDismiss = {
      showDialog = false
      viewModel.reset()
    },
  )
}

private fun repertoireCreationViewModel(
  treeStore: TreeStore,
  tagStore: RepertoireTagStore,
  scope: CoroutineScope,
) =
  RepertoireCreationViewModel(
    existingIds = { tagStore.repertoires().map { it.id }.toSet() },
    registerRepertoire = tagStore::register,
    importGames = { repertoireId, color, games ->
      PgnImporter(treeStore, tagStore).import(games, color?.toPlayer(), repertoireId)
    },
    forkRepertoire = treeStore::fork,
    scope = scope,
  )

@Composable
private fun creationErrorMessage(error: CreationError): String =
  when (error) {
    CreationError.BlankName -> stringResource(Res.string.library_create_error_blank_name)
    CreationError.DuplicateId -> stringResource(Res.string.library_create_error_duplicate_id)
    is CreationError.InvalidPgn ->
      stringResource(Res.string.library_create_error_invalid_pgn, error.reason)
  }

/** Dialog facing projection of a [CreationState]: mid flight flag, error message, done flag. */
private data class CreationDialogPresentation(
  val working: Boolean,
  val errorMessage: String?,
  val done: Boolean,
)

@Composable
private fun creationDialogPresentation(state: CreationState): CreationDialogPresentation =
  when (state) {
    CreationState.Idle ->
      CreationDialogPresentation(working = false, errorMessage = null, done = false)
    CreationState.Working ->
      CreationDialogPresentation(working = true, errorMessage = null, done = false)
    is CreationState.Failed ->
      CreationDialogPresentation(
        working = false,
        errorMessage = creationErrorMessage(state.error),
        done = false,
      )
    is CreationState.Done ->
      CreationDialogPresentation(working = false, errorMessage = null, done = true)
  }

/**
 * Publish, or Update and Unpublish once published, for one repertoire row; disabled mid flight, and
 * replaced by a sign in hint while signed out.
 */
@Composable
private fun PublishAction(localId: String, name: String, color: RepertoireColor) {
  val exporter: RepertoireMoveExporter = koinInject()
  val publishClient: RepertoirePublishClient = koinInject()
  val publishedStore: PublishedRepertoireStore = koinInject()
  val authProvider: AuthProvider = koinInject()
  val coroutineScope = rememberCoroutineScope()
  val viewModel =
    remember(localId, exporter, publishClient, publishedStore, authProvider, coroutineScope) {
      RepertoirePublishViewModel(
        localId = localId,
        exportRepertoire = { exporter.export(localId) },
        accessToken = authProvider::accessToken,
        publish = { token, slug, title, description, side, moves ->
          publishClient.publish(token, slug, title, description, side, moves)
        },
        remove = { token, slug -> publishClient.remove(token, slug) },
        publishedStore = publishedStore,
        scope = coroutineScope,
      )
    }
  val state by viewModel.state.collectAsState()
  var showDialog by remember { mutableStateOf(false) }
  val unpublishDialog = remember { ConfirmationDialog(okText = Res.string.library_unpublish) }
  unpublishDialog.DrawDialog()

  val busy = state is PublishState.Publishing || state is PublishState.Removing
  when (val current = state) {
    is PublishState.Published -> {
      KineticButton(onClick = { showDialog = true }, enabled = !busy) {
        KineticButtonLabel(stringResource(Res.string.library_update))
      }
      KineticButton(
        onClick = {
          unpublishDialog.show(confirm = { viewModel.remove() }) {
            Column {
              Text(stringResource(Res.string.library_unpublish_confirm_title))
              Text(stringResource(Res.string.library_unpublish_confirm_message))
            }
          }
        },
        enabled = !busy,
      ) {
        KineticButtonLabel(stringResource(Res.string.library_unpublish))
      }
    }
    is PublishState.Failed ->
      if (current.error == PublishError.SignedOut) {
        Text(stringResource(Res.string.library_publish_error_signed_out))
      } else {
        Column {
          KineticButton(onClick = { showDialog = true }, enabled = !busy) {
            KineticButtonLabel(stringResource(Res.string.library_publish))
          }
          Text(publishErrorMessage(current.error))
        }
      }
    PublishState.NotPublished,
    PublishState.Publishing,
    PublishState.Removing ->
      KineticButton(onClick = { showDialog = true }, enabled = !busy) {
        KineticButtonLabel(stringResource(Res.string.library_publish))
      }
  }

  val initialSlug = (state as? PublishState.Published)?.slug ?: slugify(name)
  PublishRepertoireDialog(
    visible = showDialog,
    repertoireName = name,
    initialSlug = initialSlug,
    side = if (color == RepertoireColor.WHITE) "white" else "black",
    onSubmit = { slug, title, description, side ->
      showDialog = false
      viewModel.publish(slug, title, description, side)
    },
    onDismiss = { showDialog = false },
  )
}

@Composable
private fun publishErrorMessage(error: PublishError): String =
  when (error) {
    is PublishError.InvalidPayload ->
      stringResource(Res.string.library_publish_error_invalid, error.reason)
    is PublishError.PayloadTooLarge ->
      stringResource(Res.string.library_publish_error_too_large, error.reason)
    PublishError.Forbidden -> stringResource(Res.string.library_publish_error_forbidden)
    PublishError.RemovedOnServer -> stringResource(Res.string.library_publish_error_removed)
    is PublishError.QuotaExceeded ->
      stringResource(Res.string.library_publish_error_quota, error.reason)
    PublishError.SignedOut -> stringResource(Res.string.library_publish_error_signed_out)
    PublishError.RateLimited -> stringResource(Res.string.library_publish_error_rate_limited)
    PublishError.ServerError -> stringResource(Res.string.library_publish_error_server)
    PublishError.NothingToPublish ->
      stringResource(Res.string.library_publish_error_nothing_to_publish)
  }

/** Error body for a catalog that could not be loaded at all, with a retry action. */
@Composable
private fun CatalogError(message: String, onRetry: () -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(text = message, style = typography.bodySm.copy(color = palette.destructive))
    KineticButton(onClick = onRetry) {
      KineticButtonLabel(stringResource(Res.string.library_retry))
    }
  }
}

/**
 * Loaded catalog body: stale hint, hero pick, color filter chips, and one card per filtered
 * repertoire. Requests the hero's overlap preview whenever the pick changes.
 */
@Composable
private fun CatalogList(
  state: LibraryCatalogState.Loaded,
  installStates: Map<String, RepertoireInstallState>,
  previewStates: Map<String, RepertoirePreviewState>,
  masterySnapshots: Map<String, RepertoireMasterySnapshot>,
  onPreviewRequest: (RepertoireDescriptor) -> Unit,
  onInstallRequest: (RepertoireDescriptor) -> Unit,
  onView: (RepertoireDescriptor) -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (state.isStale) {
      Text(
        text = stringResource(Res.string.library_stale_hint),
        style = typography.bodySm.copy(color = palette.actionText),
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
      var filter by remember { mutableStateOf(LibraryColorFilter.ALL) }
      val hero =
        remember(state.repertoires, installStates) {
          pickHeroRepertoire(state.repertoires, installStates)
        }
      LaunchedEffect(hero) { hero?.let(onPreviewRequest) }
      if (hero != null) {
        HeroPackCard(
          descriptor = hero,
          previewState = previewStates[hero.id],
          onInstallRequest = { onInstallRequest(hero) },
        )
      }
      FilterChipRow(
        repertoires = state.repertoires,
        installStates = installStates,
        selected = filter,
        onSelect = { filter = it },
      )
      val filtered = filterRepertoires(state.repertoires, installStates, filter)
      if (filtered.isEmpty()) {
        Text(
          text = stringResource(Res.string.library_filter_empty),
          style = typography.bodySm.copy(color = palette.ink3),
          modifier = Modifier.testTag("library_filter_empty"),
        )
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(filtered, key = { it.id }) { descriptor ->
            RepertoireCard(
              descriptor = descriptor,
              installState = installStates[descriptor.id] ?: RepertoireInstallState.NotInstalled,
              mastery = masterySnapshots[descriptor.id],
              onInstallRequest = { onInstallRequest(descriptor) },
              onView = { onView(descriptor) },
            )
          }
        }
      }
    }
  }
}

/**
 * The most downloaded repertoire not installed yet, or the most downloaded overall when all are.
 * Ties keep catalog order.
 */
private fun pickHeroRepertoire(
  repertoires: List<RepertoireDescriptor>,
  installStates: Map<String, RepertoireInstallState>,
): RepertoireDescriptor? {
  val notInstalled = repertoires.filterNot {
    installStates[it.id] is RepertoireInstallState.Installed
  }
  val candidates = notInstalled.ifEmpty { repertoires }
  return candidates.maxByOrNull { it.downloadCount }
}

/** Filter chip selection; [MINE] means installed from the catalog. */
private enum class LibraryColorFilter {
  ALL,
  WHITE,
  BLACK,
  MINE,
}

private fun filterRepertoires(
  repertoires: List<RepertoireDescriptor>,
  installStates: Map<String, RepertoireInstallState>,
  filter: LibraryColorFilter,
): List<RepertoireDescriptor> =
  when (filter) {
    LibraryColorFilter.ALL -> repertoires
    LibraryColorFilter.WHITE -> repertoires.filter { it.color == RepertoireColor.WHITE }
    LibraryColorFilter.BLACK -> repertoires.filter { it.color == RepertoireColor.BLACK }
    LibraryColorFilter.MINE ->
      repertoires.filter { installStates[it.id] is RepertoireInstallState.Installed }
  }

/**
 * Rounded progress bar exposing its value as [ProgressBarRangeInfo]. [fraction] is never read
 * during composition, so an animated caller does not recompose every frame.
 */
@Composable
private fun LibraryProgressBar(
  fraction: () -> Float,
  trackColor: Color,
  fillColor: Color,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(5.dp)
  Box(
    modifier
      .fillMaxWidth()
      .height(8.dp)
      .testTag(testTag)
      .semantics {
        val clamped = fraction().coerceIn(0f, 1f)
        progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
      }
      .background(trackColor, shape)
      .clip(shape)
      .drawBehind {
        val clamped = fraction().coerceIn(0f, 1f)
        drawRoundRect(
          color = fillColor,
          topLeft = Offset.Zero,
          size = Size(size.width * clamped, size.height),
          cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
        )
      }
  )
}

/** Lowest alpha of the skeleton's breathing pulse. */
private const val SKELETON_ALPHA_LOW = 0.4f

/**
 * Flat [color] block on [shape] whose alpha breathes. Uses [rememberInfiniteTransition] rather than
 * a `withFrameNanos` loop, which would hang Compose tests in `waitForIdle()`.
 */
@Composable
private fun SkeletonBlock(modifier: Modifier, shape: Shape, color: Color, testTag: String? = null) {
  val transition = rememberInfiniteTransition(label = "librarySkeleton")
  val alpha by
    transition.animateFloat(
      initialValue = SKELETON_ALPHA_LOW,
      targetValue = 1f,
      animationSpec =
        remember {
          // Safe cast: loadingSkeleton() is typed FiniteAnimationSpec but is always a tween.
          infiniteRepeatable(
            animation =
              KineticMotion.Routine.loadingSkeleton<Float>() as DurationBasedAnimationSpec<Float>,
            repeatMode = RepeatMode.Reverse,
          )
        },
      label = "librarySkeletonAlpha",
    )
  Box(
    modifier
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
      .graphicsLayer { this.alpha = alpha }
      .background(color, shape)
  )
}

/**
 * Install progress of a pack card: fetching covers 0 to 30%, importing 30 to 100%. `null` when no
 * progress bar is shown.
 */
private fun installProgressFraction(state: RepertoireInstallState): Float? =
  when (state) {
    is RepertoireInstallState.Fetching -> 0.30f * state.fraction.coerceIn(0f, 1f)
    is RepertoireInstallState.Importing -> 0.30f + 0.70f * state.fraction.coerceIn(0f, 1f)
    is RepertoireInstallState.Installed,
    is RepertoireInstallState.NotInstalled,
    is RepertoireInstallState.Failed -> null
  }

/** [HeroPackCard]'s colors for the current light or dark palette. */
private data class HeroCardTheme(
  val cardBackground: Modifier,
  val colorBadgeBackground: Color,
  val colorBadgeContent: Color,
  val iconTileBackground: Color,
  val titleColor: Color,
  val descriptionColor: Color,
  val progressTrackColor: Color,
  val progressLabelColor: Color,
)

@Composable
private fun heroCardTheme(palette: KineticPalette, shape: Shape): HeroCardTheme =
  if (palette.isLight) {
    HeroCardTheme(
      cardBackground = Modifier.background(palette.action, shape),
      colorBadgeBackground = Color.White.copy(alpha = 0.16f),
      colorBadgeContent = Color.White,
      iconTileBackground = Color.White.copy(alpha = 0.14f),
      titleColor = Color.White,
      descriptionColor = Color.White.copy(alpha = 0.78f),
      progressTrackColor = Color.White.copy(alpha = 0.2f),
      progressLabelColor = Color.White,
    )
  } else {
    HeroCardTheme(
      // A bordered panel instead of the violet fill, so the lime CTA stays the brightest thing.
      cardBackground =
        Modifier.background(palette.panel2, shape).border(1.5.dp, palette.lineBright, shape),
      colorBadgeBackground = palette.actionDim,
      colorBadgeContent = palette.ink2,
      iconTileBackground = palette.actionDim,
      titleColor = palette.ink,
      descriptionColor = palette.ink2,
      progressTrackColor = palette.panel3,
      progressLabelColor = palette.ink2,
    )
  }

/**
 * Hero "picked for you" pack card, showing how many of its moves the user already has once known.
 * Ignores install state, since [pickHeroRepertoire] only picks an installed one when all are.
 */
@Composable
private fun HeroPackCard(
  descriptor: RepertoireDescriptor,
  previewState: RepertoirePreviewState?,
  onInstallRequest: () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val shape = MaterialTheme.shapes.medium
  val overlap =
    (previewState as? RepertoirePreviewState.Ready)?.preview?.takeIf { it.totalMoves > 0 }
  val accent = kineticAccentLimeColor(palette)
  val theme = heroCardTheme(palette, shape)
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("library_hero_card")
        .then(theme.cardBackground)
        .clip(shape)
        .padding(15.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LibraryBadge(
        text = stringResource(Res.string.library_hero_badge),
        background = accent,
        content = KineticOnAccentLime,
      )
      LibraryBadge(
        text =
          when (descriptor.color) {
            RepertoireColor.WHITE -> stringResource(Res.string.library_color_white)
            RepertoireColor.BLACK -> stringResource(Res.string.library_color_black)
          },
        background = theme.colorBadgeBackground,
        content = theme.colorBadgeContent,
      )
    }
    Row(
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        Modifier.size(50.dp).background(theme.iconTileBackground, RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Text(descriptor.name.take(1).uppercase(), style = typography.displayLg)
      }
      Column(Modifier.weight(1f)) {
        Text(
          descriptor.name,
          style =
            typography.displayLg.copy(
              fontSize = 21.sp,
              fontWeight = FontWeight.ExtraBold,
              color = theme.titleColor,
            ),
        )
        Text(
          descriptor.description,
          style =
            typography.bodySm.copy(
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = theme.descriptionColor,
            ),
        )
      }
    }
    if (overlap != null) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LibraryProgressBar(
          fraction = { overlap.movesInCommon.toFloat() / overlap.totalMoves },
          trackColor = theme.progressTrackColor,
          fillColor = accent,
          testTag = "library_hero_progress_bar",
          modifier = Modifier.weight(1f),
        )
        Text(
          stringResource(
            Res.string.library_hero_progress,
            overlap.movesInCommon,
            overlap.totalMoves,
          ),
          style = typography.labelSm.copy(fontSize = 10.5.sp, color = theme.progressLabelColor),
        )
      }
    }
    KineticButton(
      onClick = onInstallRequest,
      style = KineticButtonStyle.Accent,
      large = true,
      modifier = Modifier.fillMaxWidth().testTag("library_hero_card:cta"),
    ) {
      // Not KineticButtonLabel, which would upper-case the text at the default small size.
      Text(
        stringResource(Res.string.library_hero_cta),
        style = typography.display.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
      )
    }
  }
}

@Composable
private fun LibraryBadge(text: String, background: Color, content: Color) {
  val typography = LocalKineticTypography.current
  Text(
    text = text,
    style = typography.labelSm.copy(fontSize = 8.5.sp, letterSpacing = 0.08.em, color = content),
    modifier =
      Modifier.background(background, RoundedCornerShape(9.dp))
        .padding(horizontal = 7.dp, vertical = 3.dp),
  )
}

/** Row of color filter chips with live per-color (and installed) counts off [repertoires]. */
@Composable
private fun FilterChipRow(
  repertoires: List<RepertoireDescriptor>,
  installStates: Map<String, RepertoireInstallState>,
  selected: LibraryColorFilter,
  onSelect: (LibraryColorFilter) -> Unit,
) {
  val counts =
    remember(repertoires, installStates) {
      mapOf(
        LibraryColorFilter.ALL to repertoires.size,
        LibraryColorFilter.WHITE to repertoires.count { it.color == RepertoireColor.WHITE },
        LibraryColorFilter.BLACK to repertoires.count { it.color == RepertoireColor.BLACK },
        LibraryColorFilter.MINE to
          repertoires.count { installStates[it.id] is RepertoireInstallState.Installed },
      )
    }
  Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
    for (filter in LibraryColorFilter.entries) {
      FilterChip(filter, counts.getValue(filter), selected == filter) { onSelect(filter) }
    }
  }
}

/** One filter chip: selected renders on an ink fill with inverted text, unselected on a panel. */
@Composable
private fun FilterChip(
  filter: LibraryColorFilter,
  count: Int,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val shape = MaterialTheme.shapes.extraSmall
  val label = stringResource(labelResFor(filter), count)
  Row(
    modifier =
      Modifier.testTag("library_filter_chip:${filter.name.lowercase()}")
        .clickable(onClick = onClick)
        .then(
          if (isSelected) Modifier.background(palette.ink, shape)
          else Modifier.background(palette.panel, shape).border(1.5.dp, palette.line, shape)
        )
        .clip(shape)
        .padding(horizontal = 13.dp, vertical = 7.dp)
  ) {
    Text(
      label,
      style =
        typography.label.copy(
          fontSize = 11.5.sp,
          fontWeight = FontWeight.ExtraBold,
          color = if (isSelected) palette.onAction else palette.ink2,
        ),
    )
  }
}

private fun labelResFor(filter: LibraryColorFilter): StringResource =
  when (filter) {
    LibraryColorFilter.ALL -> Res.string.library_filter_all
    LibraryColorFilter.WHITE -> Res.string.library_filter_white
    LibraryColorFilter.BLACK -> Res.string.library_filter_black
    LibraryColorFilter.MINE -> Res.string.library_filter_mine
  }

/** One catalog entry card, with its install action or progress. */
@Composable
private fun RepertoireCard(
  descriptor: RepertoireDescriptor,
  installState: RepertoireInstallState,
  mastery: RepertoireMasterySnapshot?,
  onInstallRequest: () -> Unit,
  onView: () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val cardShape = MaterialTheme.shapes.medium
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("$TEST_TAG_CARD:${descriptor.id}")
        .background(palette.panel, cardShape)
        .border(width = 1.5.dp, color = palette.line, shape = cardShape)
        .clip(cardShape)
        .padding(13.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // RepertoireDescriptor has no glyph, so the name's first letter stands in.
      Box(
        modifier = Modifier.size(34.dp).background(palette.panel2, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = descriptor.name.take(1).uppercase(),
          style = typography.display.copy(color = palette.actionText),
        )
      }
      Text(
        text = descriptor.name,
        style = typography.display.copy(color = palette.ink),
        modifier = Modifier.weight(1f),
      )
      ColorTag(descriptor.color)
      LibraryBadge(
        text =
          if (installState is RepertoireInstallState.Installed)
            stringResource(Res.string.library_badge_in_training)
          else stringResource(Res.string.library_badge_new),
        background =
          if (installState is RepertoireInstallState.Installed) kineticAccentLimeColor(palette)
          else palette.panel2,
        content =
          if (installState is RepertoireInstallState.Installed) KineticOnAccentLime
          else palette.ink3,
      )
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
      style = typography.labelSm.copy(color = palette.ink3),
    )
    InstallStatusRow(
      descriptor = descriptor,
      installState = installState,
      mastery = mastery,
      onInstallRequest = onInstallRequest,
    )
  }
}

/** Small bordered tag naming the side the repertoire is built for. */
@Composable
private fun ColorTag(color: RepertoireColor) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val shape = MaterialTheme.shapes.extraSmall
  val label =
    when (color) {
      RepertoireColor.WHITE -> stringResource(Res.string.library_color_white)
      RepertoireColor.BLACK -> stringResource(Res.string.library_color_black)
    }
  Text(
    text = label,
    style = typography.labelSm.copy(color = palette.ink2),
    modifier =
      Modifier.background(palette.panel2, shape)
        .border(width = 1.5.dp, color = palette.lineBright, shape = shape)
        .clip(shape)
        .padding(horizontal = 6.dp, vertical = 2.dp),
  )
}

/**
 * Bottom row of a card for its install state. One [Animatable] spans every state, so the progress
 * bar glides across state changes instead of jumping.
 */
@Composable
private fun InstallStatusRow(
  descriptor: RepertoireDescriptor,
  installState: RepertoireInstallState,
  mastery: RepertoireMasterySnapshot?,
  onInstallRequest: () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val animatedFraction = remember { Animatable(0f) }
  LaunchedEffect(installState) {
    val target = installProgressFraction(installState) ?: return@LaunchedEffect
    // Reinstalling restarts at 0 instead of draining backward from a prior install.
    if (installState is RepertoireInstallState.Fetching && animatedFraction.value > target) {
      animatedFraction.snapTo(0f)
    }
    animatedFraction.animateTo(target, KineticMotion.Routine.loadingSkeleton())
  }
  when (installState) {
    is RepertoireInstallState.NotInstalled ->
      KineticButton(onClick = onInstallRequest, style = KineticButtonStyle.Primary) {
        KineticButtonLabel(stringResource(Res.string.library_install))
      }
    is RepertoireInstallState.Fetching ->
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LibraryProgressBar(
          fraction = { animatedFraction.value },
          trackColor = palette.panel3,
          fillColor = kineticAccentLimeColor(palette),
          testTag = "library_progress:${descriptor.id}",
        )
        InstallProgressLine(stringResource(Res.string.library_fetching))
      }
    is RepertoireInstallState.Importing ->
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LibraryProgressBar(
          fraction = { animatedFraction.value },
          trackColor = palette.panel3,
          fillColor = kineticAccentLimeColor(palette),
          testTag = "library_progress:${descriptor.id}",
        )
        InstallProgressLine(stringResource(Res.string.library_importing))
      }
    is RepertoireInstallState.Installed ->
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (mastery != null && mastery.totalCount > 0) {
          LibraryProgressBar(
            fraction = { mastery.solidCount.toFloat() / mastery.totalCount },
            trackColor = palette.panel3,
            fillColor = palette.progress,
            testTag = "library_progress:${descriptor.id}",
          )
        }
        val summary = installState.summary
        if (summary != null) {
          Text(
            text =
              stringResource(
                Res.string.library_install_summary,
                summary.movesAdded,
                summary.movesAlreadyPresent,
              ),
            style = typography.bodySm.copy(color = palette.progress),
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
          style = typography.bodySm.copy(color = palette.destructive),
        )
        KineticButton(onClick = onInstallRequest, style = KineticButtonStyle.Primary) {
          KineticButtonLabel(stringResource(Res.string.library_install))
        }
      }
  }
}

/** Breathing skeleton plus a label, shown while an install step is running. */
@Composable
private fun InstallProgressLine(label: String) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SkeletonBlock(
      modifier = Modifier.size(16.dp),
      shape = RoundedCornerShape(4.dp),
      color = palette.panel3,
    )
    Text(text = label, style = typography.bodySm.copy(color = palette.ink3))
  }
}

@Composable
private fun installErrorText(error: InstallError): String =
  when (error) {
    is InstallError.Network ->
      stringResource(Res.string.library_install_error_network, error.message)
    is InstallError.Http -> stringResource(Res.string.library_install_error_http, error.status)
    is InstallError.MalformedRepertoire ->
      stringResource(Res.string.library_install_error_malformed_repertoire, error.message)
    is InstallError.ImportFailed ->
      stringResource(Res.string.library_install_error_import, error.message)
    InstallError.IdTaken -> stringResource(Res.string.library_install_error_id_taken)
  }

/** Install confirmation content: repertoire metadata and how many of its moves the user has. */
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
      style = typography.labelSm.copy(color = palette.ink3),
    )
    PreviewOverlap(previewState)
    Text(
      text = stringResource(Res.string.library_preview_question),
      style = typography.bodySm.copy(color = palette.ink3),
    )
  }
}

/**
 * The "moves in common" line of the preview dialog. A failure shows a muted notice, so it never
 * blocks the install.
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
          style = typography.bodySm.copy(color = palette.actionText),
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
