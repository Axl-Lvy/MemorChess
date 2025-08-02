package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SuccessIndicatorCard(
  isCorrect: Boolean,
  isVisible: Boolean,
  modifier: Modifier = Modifier.Companion,
) {
  if (!isVisible) {
    return
  }

  val backgroundColor =
    if (isCorrect) {
      Color(0xFF4CAF50).copy(alpha = 0.1f)
    } else {
      Color(0xFFF44336).copy(alpha = 0.1f)
    }

  val iconColor = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)
  val icon = if (isCorrect) Icons.Default.Done else Icons.Default.Close
  val text = if (isCorrect) "Correct!" else "Try Again"

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = backgroundColor),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = if (isCorrect) "Correct" else "Incorrect",
        tint = iconColor,
        modifier = Modifier.size(32.dp),
      )
      Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = iconColor,
      )
    }
  }
}
