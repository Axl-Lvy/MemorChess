package proj.memorchess.axl.ui.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Eye
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.library_badge_in_training
import memorchess.composeapp.generated.resources.library_badge_new
import memorchess.composeapp.generated.resources.library_color_black
import memorchess.composeapp.generated.resources.library_color_white
import memorchess.composeapp.generated.resources.library_empty
import memorchess.composeapp.generated.resources.library_error_http
import memorchess.composeapp.generated.resources.library_error_malformed
import memorchess.composeapp.generated.resources.library_error_network
import memorchess.composeapp.generated.resources.library_fetching
import memorchess.composeapp.generated.resources.library_filter_all
import memorchess.composeapp.generated.resources.library_filter_black
import memorchess.composeapp.generated.resources.library_filter_empty
import memorchess.composeapp.generated.resources.library_filter_mine
import memorchess.composeapp.generated.resources.library_filter_white
import memorchess.composeapp.generated.resources.library_hero_badge
import memorchess.composeapp.generated.resources.library_hero_cta
import memorchess.composeapp.generated.resources.library_hero_progress
import memorchess.composeapp.generated.resources.library_importing
import memorchess.composeapp.generated.resources.library_install
import memorchess.composeapp.generated.resources.library_install_error_http
import memorchess.composeapp.generated.resources.library_install_error_import
import memorchess.composeapp.generated.resources.library_install_error_malformed_pgn
import memorchess.composeapp.generated.resources.library_install_error_network
import memorchess.composeapp.generated.resources.library_install_summary
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
import org.jetbrains.compose.resources.StringResource
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
import proj.memorchess.axl.core.data.repertoire.placeholderRepertoireMastery
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImporter
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonLabel
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.buttons.KineticOnAccentLime
import proj.memorchess.axl.ui.components.buttons.kineticAccentLimeColor
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

private const val TEST_TAG_CARD = "library_repertoire_card"

/** Number of breathing skeleton rows shown while the catalog manifest is loading. */
private const val CATALOG_SKELETON_ROWS = 3

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
          // The importer reads the persisted graph on demand through the bounded cache.
          PgnImporter(treeStore).import(games, color.toPlayer())
        },
        previewGames = { color, games ->
          // The overlap is read against the persisted graph on demand through the bounded cache.
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
      style = typography.labelSm.copy(color = palette.actionText),
    )
    Text(
      text = stringResource(Res.string.library_subtitle),
      style = typography.bodySm.copy(color = palette.ink3),
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
    Text(text = message, style = typography.bodySm.copy(color = palette.destructive))
    KineticButton(onClick = onRetry) {
      KineticButtonLabel(stringResource(Res.string.library_retry))
    }
  }
}

/**
 * Loaded catalog body: a stale data hint when the list comes from an expired cache, an empty
 * message when the catalog lists nothing, and otherwise a hero pick, the color filter chips and one
 * card per (filtered) repertoire — or a "nothing matches" message when the filter excludes
 * everything.
 *
 * The stale hint, hero card, and filter chips are leading items of the same [LazyColumn] as the
 * repertoire cards, rather than fixed content composed above it: the whole page then scrolls as one
 * surface, so a short viewport (a landscape phone, say) scrolls the hero and chips away with the
 * rest of the list instead of them permanently squeezing it down to about one visible card.
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
  if (state.repertoires.isEmpty()) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (state.isStale) {
        StaleCatalogHint()
      }
      Text(
        text = stringResource(Res.string.library_empty),
        style = typography.bodySm.copy(color = palette.ink3),
      )
    }
    return
  }
  var filter by remember { mutableStateOf(LibraryColorFilter.ALL) }
  val hero = remember(state.repertoires) { pickHeroRepertoire(state.repertoires) }
  val filtered = filterRepertoires(state.repertoires, installStates, filter)
  LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (state.isStale) {
      item(key = "library-stale-hint") { StaleCatalogHint() }
    }
    if (hero != null) {
      item(key = "library-hero") {
        HeroPackCard(descriptor = hero, onInstallRequest = { onInstallRequest(hero) })
      }
    }
    item(key = "library-filters") {
      FilterChipRow(
        repertoires = state.repertoires,
        installStates = installStates,
        selected = filter,
        onSelect = { filter = it },
      )
    }
    if (filtered.isEmpty()) {
      item(key = "library-filter-empty") {
        Text(
          text = stringResource(Res.string.library_filter_empty),
          style = typography.bodySm.copy(color = palette.ink3),
          modifier = Modifier.testTag("library_filter_empty"),
        )
      }
    } else {
      items(filtered, key = { it.id }) { descriptor ->
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

/** Bordered hint shown above the list when [CatalogList] is rendering an expired cached catalog. */
@Composable
private fun StaleCatalogHint() {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val shape = MaterialTheme.shapes.medium
  Text(
    text = stringResource(Res.string.library_stale_hint),
    style = typography.bodySm.copy(color = palette.actionText),
    modifier =
      Modifier.fillMaxWidth()
        .background(palette.panel2, shape)
        .border(width = 1.5.dp, color = palette.line, shape = shape)
        .clip(shape)
        .padding(8.dp),
  )
}

/**
 * PLACEHOLDER hero pick (#282): the catalog carries no recommendation signal yet, so this simply
 * takes the first entry — including one the user has already installed (see [HeroPackCard]'s KDoc:
 * the hero card deliberately does not special-case that; #309 owns deciding what "picked for you"
 * should mean once something is already picked). #309 tracks giving the library a real "picked for
 * you" signal — swap the body of this function only when that lands, the call site should not need
 * to change.
 */
private fun pickHeroRepertoire(repertoires: List<RepertoireDescriptor>): RepertoireDescriptor? =
  repertoires.firstOrNull()

/**
 * Filter chip selection.
 *
 * [MINE] is backed by [installStates][RepertoireInstallState], not [InstalledRepertoireStore] — the
 * "installed" flag already flows into installStates from the view model, so no new dependency is
 * needed to answer "is this cheap" from the #282 brainstorming notes.
 */
private enum class LibraryColorFilter {
  ALL,
  WHITE,
  BLACK,
  MINE,
}

/**
 * Narrows [repertoires] to the ones matching [filter], per [installStates] for
 * [MINE][LibraryColorFilter.MINE].
 */
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
 * 8dp-tall, 5dp-radius progress bar shared by the hero card, the per-pack mastery bar and the
 * install-progress bar. [fraction] is read lazily — inside the [semantics] block and again inside
 * [drawBehind] — but never during composition itself, so an animated caller (the install sweep)
 * does not force this composable (or its parent) to recompose every frame; only semantics and
 * drawing re-run. [testTag] carries a [ProgressBarRangeInfo] so tests can assert the exact fraction
 * via `assertRangeInfoEquals` without reaching into a private function from another package.
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

/** Alpha under KineticMotion.Routine.loadingSkeleton()'s low end of the breathing pulse. */
private const val SKELETON_ALPHA_LOW = 0.4f

/**
 * Breathing skeleton block: a flat [color] fill on [shape] whose alpha pulses between
 * [SKELETON_ALPHA_LOW] and 1f, driven by [KineticMotion.Routine.loadingSkeleton]. Replaces the two
 * former `CircularProgressIndicator` call sites, per #273's determinate-loading spec.
 *
 * Built on [rememberInfiniteTransition]/[infiniteRepeatable], matching the existing precedent in
 * `KineticBootIndicator` — not a hand-rolled `while (isActive) { animateTo(...) }` loop. Compose's
 * test clock (`runComposeUiTest`'s default `autoAdvance = true`) only knows how to auto-cancel
 * animations driven through `withInfiniteAnimationFrameNanos` (`InfiniteAnimationPolicy`), which is
 * exactly what [rememberInfiniteTransition] uses internally; a loop built on plain `withFrameNanos`
 * always leaves a pending frame awaiter, so `waitForIdle()` never returns and every test that
 * renders a skeleton hangs. The spec passed to [infiniteRepeatable] is built once, on first
 * composition, inside the [remember] block — never freshly on every recomposition, per
 * [KineticMotion]'s own KDoc on wasmJs cost.
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
          // KineticMotion.Routine.loadingSkeleton() is declared to return FiniteAnimationSpec<T>,
          // even though its actual instance (a tween()) is a DurationBasedAnimationSpec<T> —
          // infiniteRepeatable requires that narrower static type, hence the cast. tween() always
          // implements DurationBasedAnimationSpec, so this is safe for every KineticMotion spec.
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
 * Maps [state] to the determinate install-progress fraction shown on a pack card's progress bar,
 * per #282's SEMI-REAL mapping: Fetching sweeps to 30%, Importing to 100%, driven by
 * [KineticMotion.Routine.loadingSkeleton] timing rather than real byte counts
 * ([RepertoireInstallState] carries no numeric progress — do not widen it here, that is #309's
 * job). `null` means "no install bar" — [NotInstalled][RepertoireInstallState.NotInstalled] and
 * [Failed][RepertoireInstallState.Failed] show the action button instead.
 */
private fun installProgressFraction(state: RepertoireInstallState): Float? =
  when (state) {
    is RepertoireInstallState.Fetching -> 0.30f
    is RepertoireInstallState.Importing -> 1.00f
    is RepertoireInstallState.Installed,
    is RepertoireInstallState.NotInstalled,
    is RepertoireInstallState.Failed -> null
  }

/**
 * Hero "picked for you" pack card at the top of the library. [descriptor] is chosen by
 * [pickHeroRepertoire] — a PLACEHOLDER (#282) pending #309's real recommendation signal. Its
 * progress readout also uses [placeholderRepertoireMastery] (#292's stub), so the numerator and
 * denominator shown here are not guaranteed to relate to [descriptor]'s own `moveCount` — both are
 * placeholders pending #292/#309, not a computed fact about this specific repertoire.
 *
 * Does NOT look at [descriptor]'s own [RepertoireInstallState]: the badge and CTA are identical
 * whether or not this exact repertoire is already installed. Deciding what "picked for you" should
 * show for an already-installed pick is deferred to #309 alongside the rest of the recommendation
 * signal.
 */
@Composable
private fun HeroPackCard(descriptor: RepertoireDescriptor, onInstallRequest: () -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val shape = MaterialTheme.shapes.medium
  val mastery = placeholderRepertoireMastery() // PLACEHOLDER — see this composable's own KDoc.
  val accent = kineticAccentLimeColor(palette)
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("library_hero_card")
        .then(
          if (palette.isLight) Modifier.background(palette.action, shape)
          else Modifier.background(palette.panel2, shape).border(1.5.dp, palette.lineBright, shape)
          // Dark drops the solid violet fill for a bordered panel per 1j.html's own note, "so the
          // lime CTA stays the brightest thing on screen". palette.panel2/lineBright are the
          // closest existing tokens to 1j's literal #2A1B47/#4A2E86 (no exact palette match exists
          // for those two bespoke hex values — approximated rather than adding new tokens).
        )
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
        background = if (palette.isLight) Color.White.copy(alpha = 0.16f) else palette.actionDim,
        content = if (palette.isLight) Color.White else palette.ink2,
      )
    }
    Row(
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        Modifier.size(50.dp)
          .background(
            if (palette.isLight) Color.White.copy(alpha = 0.14f) else palette.actionDim,
            RoundedCornerShape(15.dp),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          descriptor.name.take(1).uppercase(),
          // Explicit color, like every sibling Text in this card: KineticTypography styles carry
          // no color, so this would otherwise fall back to LocalContentColor (Scaffold's
          // onBackground = palette.ink), a near-black glyph on the light theme's translucent tile.
          style =
            typography.displayLg.copy(color = if (palette.isLight) Color.White else palette.ink),
        )
      }
      Column(Modifier.weight(1f)) {
        Text(
          descriptor.name,
          style =
            typography.displayLg.copy(
              fontSize = 21.sp,
              fontWeight = FontWeight.ExtraBold,
              color = if (palette.isLight) Color.White else palette.ink,
            ),
        )
        Text(
          descriptor.description,
          style =
            typography.bodySm.copy(
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = if (palette.isLight) Color.White.copy(alpha = 0.78f) else palette.ink2,
            ),
        )
      }
    }
    Row(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LibraryProgressBar(
        fraction = { mastery.solidPercent / 100f },
        trackColor = if (palette.isLight) Color.White.copy(alpha = 0.2f) else palette.panel3,
        fillColor = accent,
        testTag = "library_hero_progress_bar",
        modifier = Modifier.weight(1f),
      )
      Text(
        stringResource(Res.string.library_hero_progress, mastery.solidCount, mastery.totalCount),
        style =
          typography.labelSm.copy(
            fontSize = 10.5.sp,
            color = if (palette.isLight) Color.White else palette.ink2,
          ),
      )
    }
    KineticButton(
      onClick = onInstallRequest,
      style = KineticButtonStyle.Accent,
      large = true,
      modifier = Modifier.fillMaxWidth().testTag("library_hero_card:cta"),
    ) {
      // Plain styled Text, NOT KineticButtonLabel: KineticButtonLabel upper-cases its argument and
      // renders at KineticButton's default LocalTextStyle (Baloo 2 600 12sp), which would read
      // "ADD TO MY TRAINING" at the wrong size and case. The mockup (1d.html/1j.html) shows
      // sentence case at Baloo 2 800 16sp; the button's content slot is free-form, so this is
      // supplied directly.
      Text(
        stringResource(Res.string.library_hero_cta),
        style = typography.display.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
      )
    }
  }
}

/**
 * 9dp-radius pill badge: Nunito 900 8.5sp, 0.08em tracking. Nunito's bundled cuts stop at Bold
 * (700) — see `kineticTypography()`'s `nunitoFamily()` — so "900" renders at Bold, same accepted
 * limitation the rest of Kinetic's "800"/"900" Nunito labels already live with via
 * `typography.label*`.
 */
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
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
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
        .then(
          if (isSelected) Modifier.background(palette.ink, shape)
          else Modifier.background(palette.panel, shape).border(1.5.dp, palette.line, shape)
        )
        // Clip before the click target, so the state layer (and the hit target's visual bounds)
        // follow the pill rather than the node's square bounds; selectable (not clickable) so the
        // chip carries Role.Tab and a selected semantics flag for assistive tech.
        .clip(shape)
        .selectable(selected = isSelected, onClick = onClick, role = Role.Tab)
        .padding(horizontal = 13.dp, vertical = 7.dp)
  ) {
    Text(
      label,
      // The selected content color uses palette.onAction as the closest documented "text that
      // reads on a vivid/dark fill" token: exact match in light (mockup's white text == onAction
      // light exactly); in dark it's a close approximation of the mockup's #1A1030, not an exact
      // hex match — no palette field is an exact match in dark, so the nearest documented token is
      // used rather than a new literal, consistent with kineticPressableEdgeColor's own precedent.
      style =
        typography.label.copy(
          fontSize = 11.5.sp,
          fontWeight = FontWeight.ExtraBold,
          color = if (isSelected) palette.onAction else palette.ink2,
        ),
    )
  }
}

/** Localized label resource for one [LibraryColorFilter]. */
private fun labelResFor(filter: LibraryColorFilter): StringResource =
  when (filter) {
    LibraryColorFilter.ALL -> Res.string.library_filter_all
    LibraryColorFilter.WHITE -> Res.string.library_filter_white
    LibraryColorFilter.BLACK -> Res.string.library_filter_black
    LibraryColorFilter.MINE -> Res.string.library_filter_mine
  }

/**
 * One catalog entry: icon tile, name, color tag, NEW/IN TRAINING badge, description, move count,
 * and the install action or its progress and outcome.
 *
 * Deliberately `fillMaxWidth()` in [CatalogList]'s vertical list rather than the brainstormed 196dp
 * fixed card width: that width came from a horizontal-carousel mockup, and this page — like every
 * other list in the app — scrolls vertically, one full-width card per row.
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
      // Decorative icon tile: RepertoireDescriptor carries no per-opening glyph field (the
      // mockup's piece icons were hand-picked dressing), so the name's first letter stands in.
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
 * Bottom row of a card. Walks every [RepertoireInstallState]: the install button when nothing has
 * happened yet, the determinate install-progress bar ([installProgressFraction]) plus a status line
 * while fetching or importing, the placeholder mastery progress bar plus the import summary once
 * installed in this session (no summary when restored from persistence), and the failure message
 * plus a retry button after a failed attempt.
 *
 * The install-progress sweep is one [Animatable] hoisted for the whole row, not per branch, so
 * transitioning Fetching -> Importing continues the fill from wherever it last sat (30% -> 100%)
 * instead of resetting to 0. Entering Fetching itself always snaps the sweep back to 0% first —
 * covering the first install, a retry from [Failed][RepertoireInstallState.Failed], and a reinstall
 * from [Installed][RepertoireInstallState.Installed] — so the bar always fills forward and never
 * drains backward from wherever a previous attempt left it. [LibraryProgressBar] only reads the
 * value in the draw phase, so this composable itself never recomposes on animation frames.
 */
@Composable
private fun InstallStatusRow(
  descriptor: RepertoireDescriptor,
  installState: RepertoireInstallState,
  onInstallRequest: () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val animatedFraction = remember { Animatable(0f) }
  LaunchedEffect(installState) {
    val target = installProgressFraction(installState) ?: return@LaunchedEffect
    if (installState is RepertoireInstallState.Fetching) {
      // Every fetch (first install, retry, or reinstall) restarts the sweep at 0% instead of
      // animating from wherever a previous attempt left the bar (e.g. Importing's 100%, or a
      // stale value from a Failed attempt) — otherwise the bar would sweep backwards.
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
        // PLACEHOLDER mastery bar (#292's stub), shown only once installed — see CatalogList's
        // scope-narrowing note: an un-installed card shows no fabricated progress at all. Kept on
        // the same lime fill the install sweep just completed with (rather than palette.progress,
        // which is violet in light) so a successful install doesn't also flip hue the instant it
        // lands, on top of the value dropping from the sweep's 100% to the placeholder percentage.
        LibraryProgressBar(
          fraction = { placeholderRepertoireMastery().solidPercent / 100f },
          trackColor = palette.panel3,
          fillColor = kineticAccentLimeColor(palette),
          testTag = "library_progress:${descriptor.id}",
        )
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
