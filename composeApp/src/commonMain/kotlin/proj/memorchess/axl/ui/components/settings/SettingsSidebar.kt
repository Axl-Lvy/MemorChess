package proj.memorchess.axl.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.settings_sidebar_caption
import memorchess.composeapp.generated.resources.settings_sidebar_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** One entry of a [SettingsSidebar] group: a section the user can jump to. */
data class SettingsNavItem(val id: String, val label: StringResource, val number: String)

/** A group of [SettingsNavItem]s sharing a header in the sidebar. */
data class SettingsNavGroup(val title: StringResource, val sections: List<SettingsNavItem>)

/**
 * Desktop sidebar of the Settings page listing [groups], highlighting the item with [selectedId].
 *
 * @param onSelect receives the clicked item's id.
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
        .drawBehind {
          // Trailing edge only: a four-sided stroke would double the window and content edges.
          val strokePx = 1.5.dp.toPx()
          drawRect(
            color = palette.line,
            topLeft = Offset(size.width - strokePx, 0f),
            size = Size(strokePx, size.height),
          )
        }
        .verticalScroll(scroll)
        .padding(vertical = 24.dp)
  ) {
    Text(
      text = stringResource(Res.string.settings_sidebar_caption),
      style = typography.labelSm.copy(color = palette.actionText),
      modifier = Modifier.padding(horizontal = 22.dp),
    )
    Text(
      text = stringResource(Res.string.settings_sidebar_title),
      style = typography.displayLg.copy(color = palette.ink),
      modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
    )

    groups.forEach { group ->
      Text(
        text = stringResource(group.title).uppercase(),
        style = typography.labelSm.copy(color = palette.ink4),
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
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
  val numColor = if (active) palette.actionText else palette.ink4
  val background = if (active) palette.panel else Color.Transparent
  val itemShape = RoundedCornerShape(14.dp)

  Row(
    modifier =
      Modifier.fillMaxWidth()
        // Outer inset so the pill doesn't touch the sidebar's own edges.
        .padding(horizontal = 10.dp)
        .clip(itemShape)
        .background(color = background, shape = itemShape)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = stringResource(item.label), style = typography.display.copy(color = labelColor))
    Text(text = item.number, style = typography.labelSm.copy(color = numColor))
  }
}
