package proj.memorchess.axl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Kinetic shape defaults: 20.dp for cards, buttons and sheets, 12.dp or 16.dp for pills and chips.
 */
val kineticShapes: Shapes =
  Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
  )
