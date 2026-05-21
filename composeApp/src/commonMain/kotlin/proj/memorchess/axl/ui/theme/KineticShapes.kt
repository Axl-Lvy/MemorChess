package proj.memorchess.axl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Kinetic shape defaults. The design is deliberately square — Material 3 shape roles all collapse
 * to zero radius. Anything that needs rounding (toggle thumb, pills, dialog overlays) declares its
 * own [RoundedCornerShape] inline.
 */
val kineticShapes: Shapes =
  Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
  )
