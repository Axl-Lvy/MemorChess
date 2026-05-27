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
  val title: String,
  val description: String? = null,
  val danger: Boolean = false,
)

private val PAGE_SECTIONS: List<SettingsPageSection> =
  listOf(
    SettingsPageSection(id = "display", title = "Display & theme"),
    SettingsPageSection(id = "board", title = "Board style"),
    SettingsPageSection(id = "training", title = "Training behavior"),
    SettingsPageSection(id = "engine", title = "Engine & analysis"),
    SettingsPageSection(id = "lichess", title = "Lichess account"),
    SettingsPageSection(id = "io", title = "Import / export"),
    SettingsPageSection(id = "danger", title = "Danger zone", danger = true),
  )

private val NAV_GROUPS: List<SettingsNavGroup> =
  listOf(
    SettingsNavGroup(
      title = "Appearance",
      sections =
        listOf(
          SettingsNavItem(id = "display", label = "Display", number = "01"),
          SettingsNavItem(id = "board", label = "Board", number = "02"),
        ),
    ),
    SettingsNavGroup(
      title = "Practice",
      sections =
        listOf(
          SettingsNavItem(id = "training", label = "Training", number = "03"),
          SettingsNavItem(id = "engine", label = "Engine", number = "04"),
        ),
    ),
    SettingsNavGroup(
      title = "Accounts",
      sections = listOf(SettingsNavItem(id = "lichess", label = "Lichess", number = "05")),
    ),
    SettingsNavGroup(
      title = "Storage",
      sections =
        listOf(
          SettingsNavItem(id = "io", label = "Import / Export", number = "06"),
          SettingsNavItem(id = "danger", label = "Danger zone", number = "07"),
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
        title = section.title,
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
