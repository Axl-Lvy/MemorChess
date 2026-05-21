package proj.memorchess.axl.ui.components.settings

import proj.memorchess.axl.core.config.BooleanBasedConfigItem

@Deprecated("Replaced by KineticToggle call sites under ui/components/settings/sections/.")
data class BooleanBasedSelectorParameters(val config: BooleanBasedConfigItem) : ButtonParameters
