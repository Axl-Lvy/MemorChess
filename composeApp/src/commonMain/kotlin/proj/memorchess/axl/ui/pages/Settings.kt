package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.settings_group_accounts
import memorchess.composeapp.generated.resources.settings_group_appearance
import memorchess.composeapp.generated.resources.settings_group_practice
import memorchess.composeapp.generated.resources.settings_group_storage
import memorchess.composeapp.generated.resources.settings_nav_board
import memorchess.composeapp.generated.resources.settings_nav_danger
import memorchess.composeapp.generated.resources.settings_nav_display
import memorchess.composeapp.generated.resources.settings_nav_engine
import memorchess.composeapp.generated.resources.settings_nav_io
import memorchess.composeapp.generated.resources.settings_nav_lichess
import memorchess.composeapp.generated.resources.settings_nav_training
import memorchess.composeapp.generated.resources.settings_section_board
import memorchess.composeapp.generated.resources.settings_section_danger
import memorchess.composeapp.generated.resources.settings_section_display
import memorchess.composeapp.generated.resources.settings_section_engine
import memorchess.composeapp.generated.resources.settings_section_io
import memorchess.composeapp.generated.resources.settings_section_lichess
import memorchess.composeapp.generated.resources.settings_section_training
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.components.settings.LichessAccountSection
import proj.memorchess.axl.ui.components.settings.SettingsNavGroup
import proj.memorchess.axl.ui.components.settings.SettingsNavItem
import proj.memorchess.axl.ui.components.settings.SettingsSection
import proj.memorchess.axl.ui.components.settings.SettingsSidebar
import proj.memorchess.axl.ui.components.settings.sections.BoardStyleSection
import proj.memorchess.axl.ui.components.settings.sections.DangerZoneSection
import proj.memorchess.axl.ui.components.settings.sections.DisplayAndThemeSection
import proj.memorchess.axl.ui.components.settings.sections.EngineAndAnalysisSection
import proj.memorchess.axl.ui.components.settings.sections.ImportExportSection
import proj.memorchess.axl.ui.components.settings.sections.TrainingBehaviorSection
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.util.BasicReloader

/**
 * Test tag on the scrollable [LazyColumn] of settings sections. Exposed so tests can scroll the
 * list to a section that is initially off-screen (and therefore not yet composed) before
 * interacting with it.
 */
internal const val SETTINGS_SECTION_LIST_TAG = "settingsSectionList"

/** Identifier for one settings section in the page model. */
private data class SettingsPageSection(
  val id: String,
  val title: StringResource,
  val description: String? = null,
  val danger: Boolean = false,
)

private val PAGE_SECTIONS: List<SettingsPageSection> =
  listOf(
    SettingsPageSection(id = "display", title = Res.string.settings_section_display),
    SettingsPageSection(id = "board", title = Res.string.settings_section_board),
    SettingsPageSection(id = "training", title = Res.string.settings_section_training),
    SettingsPageSection(id = "engine", title = Res.string.settings_section_engine),
    SettingsPageSection(id = "lichess", title = Res.string.settings_section_lichess),
    SettingsPageSection(id = "io", title = Res.string.settings_section_io),
    SettingsPageSection(id = "danger", title = Res.string.settings_section_danger, danger = true),
  )

private val NAV_GROUPS: List<SettingsNavGroup> =
  listOf(
    SettingsNavGroup(
      title = Res.string.settings_group_appearance,
      sections =
        listOf(
          SettingsNavItem(id = "display", label = Res.string.settings_nav_display, number = "01"),
          SettingsNavItem(id = "board", label = Res.string.settings_nav_board, number = "02"),
        ),
    ),
    SettingsNavGroup(
      title = Res.string.settings_group_practice,
      sections =
        listOf(
          SettingsNavItem(id = "training", label = Res.string.settings_nav_training, number = "03"),
          SettingsNavItem(id = "engine", label = Res.string.settings_nav_engine, number = "04"),
        ),
    ),
    SettingsNavGroup(
      title = Res.string.settings_group_accounts,
      sections =
        listOf(
          SettingsNavItem(id = "lichess", label = Res.string.settings_nav_lichess, number = "05")
        ),
    ),
    SettingsNavGroup(
      title = Res.string.settings_group_storage,
      sections =
        listOf(
          SettingsNavItem(id = "io", label = Res.string.settings_nav_io, number = "06"),
          SettingsNavItem(id = "danger", label = Res.string.settings_nav_danger, number = "07"),
        ),
    ),
  )

/**
 * Settings page. Branches on the current [WindowSizeClass]: wide layouts get a sidebar nav on the
 * left and a scrollable section list on the right; narrow layouts get just the section list.
 *
 * The sections are explicit (no enum loop) so each one can carry its own controls and copy. A
 * [BasicReloader] threaded into every section forces a re-read of the underlying
 * [proj.memorchess .axl.core.config.ConfigItem]s when the user resets settings or changes the
 * active theme.
 */
@Composable
fun Settings() {
  val reloader = remember { BasicReloader() }
  val coroutineScope = rememberCoroutineScope()
  val lazyListState = rememberLazyListState()
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
  val isWide =
    windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

  var selectedId by remember { mutableStateOf(PAGE_SECTIONS.first().id) }

  LaunchedEffect(lazyListState) {
    snapshotFlow { lazyListState.firstVisibleItemIndex }
      .collectLatest { index -> PAGE_SECTIONS.getOrNull(index)?.let { selectedId = it.id } }
  }

  val sectionList: @Composable () -> Unit = {
    SectionList(reloader = reloader, lazyListState = lazyListState)
  }

  Box(modifier = Modifier.fillMaxSize().testTag(Route.SettingsRoute.getLabel())) {
    if (isWide) {
      Row(modifier = Modifier.fillMaxSize()) {
        SettingsSidebar(
          groups = NAV_GROUPS,
          selectedId = selectedId,
          onSelect = { id ->
            selectedId = id
            val index = PAGE_SECTIONS.indexOfFirst { it.id == id }
            if (index >= 0) {
              coroutineScope.launch { lazyListState.animateScrollToItem(index) }
            }
          },
        )
        Box(modifier = Modifier.weight(1f).fillMaxSize()) { sectionList() }
      }
    } else {
      sectionList()
    }
  }
}

@Composable
private fun SectionList(reloader: BasicReloader, lazyListState: LazyListState) {
  val reloadKey = reloader.getKey()
  LazyColumn(
    state = lazyListState,
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 16.dp)
        .testTag(SETTINGS_SECTION_LIST_TAG),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    items(items = PAGE_SECTIONS, key = { it.id }) { section ->
      SettingsSection(
        title = stringResource(section.title),
        description = section.description,
        danger = section.danger,
        modifier = Modifier.fillMaxWidth(),
      ) {
        SectionBody(id = section.id, reloadKey = reloadKey, onReload = { reloader.reload() })
      }
    }
  }
}

@Composable
private fun SectionBody(id: String, reloadKey: Any, onReload: () -> Unit) {
  when (id) {
    "display" -> DisplayAndThemeSection(reloadKey = reloadKey, onReload = onReload)
    "board" -> BoardStyleSection(reloadKey = reloadKey, onReload = onReload)
    "training" -> TrainingBehaviorSection(reloadKey = reloadKey)
    "engine" -> EngineAndAnalysisSection(reloadKey = reloadKey)
    "lichess" -> LichessAccountSection()
    "io" -> ImportExportSection()
    "danger" -> DangerZoneSection(onReset = onReload)
    else -> Column {}
  }
}
