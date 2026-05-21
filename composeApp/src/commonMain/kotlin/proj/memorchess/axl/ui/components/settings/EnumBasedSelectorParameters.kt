package proj.memorchess.axl.ui.components.settings

import proj.memorchess.axl.core.config.EnumBasedAppConfigItem
import proj.memorchess.axl.core.util.CanDisplayName

@Deprecated(
  "Replaced by KineticSegmentedControl/KineticSwatchPicker call sites under ui/components/settings/sections/."
)
class EnumBasedSelectorParameters<T>(val config: EnumBasedAppConfigItem<T>) : ButtonParameters
  where T : Enum<T>, T : CanDisplayName
