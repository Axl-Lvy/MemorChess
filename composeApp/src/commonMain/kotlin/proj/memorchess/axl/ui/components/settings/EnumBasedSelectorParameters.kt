package proj.memorchess.axl.ui.components.settings

import proj.memorchess.axl.core.config.EnumBasedAppConfigItem

class EnumBasedSelectorParameters<T : Enum<T>>(val config: EnumBasedAppConfigItem<T>) :
  ButtonParameters
