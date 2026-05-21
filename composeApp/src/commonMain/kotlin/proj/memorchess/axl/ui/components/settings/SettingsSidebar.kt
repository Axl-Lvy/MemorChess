package proj.memorchess.axl.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * One entry inside a [SettingsSidebar] group — represents a single section the user can jump to.
 *
 * @property id stable identifier of the section, matched against the sidebar's `selectedId`.
 * @property label display label in the sidebar.
 * @property number two digit mono number shown on the right of the item.
 */
data class SettingsNavItem(val id: String, val label: String, val number: String)

/**
 * A logical group of [SettingsNavItem]s sharing a header in the sidebar (e.g. "Appearance",
 * "Practice").
 *
 * @property title small uppercase mono caption shown above the group.
 * @property sections nav items, in display order.
 */
data class SettingsNavGroup(val title: String, val sections: List<SettingsNavItem>)

/**
 * Desktop sidebar nav for the Settings page. Mirrors the `.sidebar`, `.side-head`, `.sn-label`, and
 * `.sn-item` blocks from `design-proposals/kinetic-settings-desktop.html`.
 *
 * A 280.dp wide column listing [groups]. Each item is a clickable row carrying the section's number
 * and label. The currently selected item is highlighted with a 2.dp accent left border, a `panel`
 * background, and an `ink` label color.
 *
 * @param groups Grouped nav items to render, in display order.
 * @param selectedId The id of the currently selected section, used for highlight.
 * @param onSelect Invoked with a nav item's id when the user clicks it.
 * @param modifier External modifier applied to the outer column.
 */
@Composable
fun SettingsSidebar(
  groups: List<SettingsNavGroup>,
  selectedId: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val scroll = rememberScrollState()

  Column(
    modifier =
      modifier
        .width(280.dp)
        .fillMaxHeight()
        .background(palette.bg2)
        .border(width = 1.dp, color = palette.line)
        .verticalScroll(scroll)
        .padding(vertical = 24.dp)
  ) {
    Text(
      text = "CONFIGURATION",
      style = typography.monoSm.copy(color = palette.accentText),
      modifier = Modifier.padding(horizontal = 22.dp),
    )
    Text(
      text = "Settings",
      style = typography.displayLg.copy(color = palette.ink),
      modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
    )

    groups.forEachIndexed { groupIndex, group ->
      Text(
        text = group.title.uppercase(),
        style = typography.monoSm.copy(color = palette.ink4),
        modifier =
          Modifier.padding(
            start = 22.dp,
            end = 22.dp,
            top = if (groupIndex == 0) 14.dp else 14.dp,
            bottom = 6.dp,
          ),
      )
      group.sections.forEach { item ->
        SidebarItem(item = item, active = item.id == selectedId, onClick = { onSelect(item.id) })
      }
    }
  }
}

@Composable
private fun SidebarItem(item: SettingsNavItem, active: Boolean, onClick: () -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val labelColor = if (active) palette.ink else palette.ink2
  val numColor = if (active) palette.accentText else palette.ink4
  val accent = palette.accent
  val background = if (active) palette.panel else Color.Transparent

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(background)
        .drawBehind {
          if (active) {
            val strokePx = 2.dp.toPx()
            drawRect(color = accent, topLeft = Offset(0f, 0f), size = Size(strokePx, size.height))
          }
        }
        .clickable(onClick = onClick)
        .padding(horizontal = 22.dp, vertical = 10.dp),
    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = item.label, style = typography.display.copy(color = labelColor))
    Text(text = item.number, style = typography.monoSm.copy(color = numColor))
  }
}
