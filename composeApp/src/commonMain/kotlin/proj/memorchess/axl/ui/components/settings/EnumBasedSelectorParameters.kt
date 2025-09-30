package proj.memorchess.axl.ui.components.settings

import proj.memorchess.axl.core.config.EnumBasedAppConfigItem
import proj.memorchess.axl.core.util.CanDisplayName

class EnumBasedSelectorParameters<T>(val config: EnumBasedAppConfigItem<T>) : ButtonParameters
  where T : Enum<T>, T : CanDisplayName
