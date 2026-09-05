package proj.memorchess.axl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Kinetic shape defaults. Cards, buttons, and sheets round to a chunky 20.dp. Pills, chips, and
 * badges use a smaller, distinct scale (12.dp / 16.dp) so the two families read apart. Anything
 * that needs a one-off radius still declares its own [RoundedCornerShape] inline.
 */
val kineticShapes: Shapes =
  Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
  )
