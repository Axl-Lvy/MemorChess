package proj.memorchess.axl.ui.components.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.core.engine.evaluation.EvaluationScore

/**
 * Horizontal evaluation bar showing white/black advantage.
 *
 * The bar is split into a light (White) section and a dark (Black) section, with proportions driven
 * by the evaluation score. A text label displays the numeric score and current engine depth.
 *
 * @param evaluation The current evaluation, or `null` for a neutral display.
 * @param currentDepth The depth currently reached by the engine, or `null` if idle.
 * @param modifier Modifier for the bar container.
 */
@Composable
fun EvaluationBar(evaluation: EvaluationScore?, currentDepth: Int?, modifier: Modifier = Modifier) {
  val whiteFraction = evaluationToFraction(evaluation)
  val animatedFraction by
    animateFloatAsState(targetValue = whiteFraction, animationSpec = tween(durationMillis = 300))

  val whiteColor = MaterialTheme.colorScheme.surfaceBright
  val blackColor = MaterialTheme.colorScheme.surfaceDim

  Box(
    modifier =
      modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp)).background(blackColor)
  ) {
    Row(modifier = Modifier.matchParentSize()) {
      // White section
      Box(
        modifier = Modifier.fillMaxHeight().weight(animatedFraction.coerceAtLeast(0.01f)),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier =
            Modifier.matchParentSize().background(whiteColor).clip(RoundedCornerShape(8.dp))
        )
      }
      // Black section
      Box(modifier = Modifier.fillMaxHeight().weight((1f - animatedFraction).coerceAtLeast(0.01f)))
    }
    // Score + depth label centered on the bar
    Box(modifier = Modifier.matchParentSize().padding(horizontal = 8.dp)) {
      val label = formatLabel(evaluation, currentDepth)
      val labelColor =
        if (whiteFraction >= 0.5f) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = labelColor,
        modifier = Modifier.align(Alignment.Center),
      )
    }
  }
}

/** Converts an [EvaluationScore] to a fraction [0.0, 1.0] representing White's advantage. */
private fun evaluationToFraction(evaluation: EvaluationScore?): Float {
  return when (evaluation) {
    is EvaluationScore.Centipawns -> (0.5f + evaluation.value / 1000f).coerceIn(0f, 1f)
    is EvaluationScore.Mate -> if (evaluation.moves > 0) 1f else 0f
    null -> 0.5f
  }
}

/** Formats score and depth as a combined label. */
private fun formatLabel(evaluation: EvaluationScore?, currentDepth: Int?): String {
  val score = formatScore(evaluation)
  val depth = if (currentDepth != null) " (d$currentDepth)" else ""
  return "$score$depth"
}

/** Formats an [EvaluationScore] as a human-readable label. */
private fun formatScore(evaluation: EvaluationScore?): String {
  return when (evaluation) {
    is EvaluationScore.Centipawns -> {
      val pawns = kotlin.math.round(evaluation.value / 10f) / 10f
      val sign = if (pawns >= 0) "+" else ""
      val formatted = if (pawns == pawns.toLong().toFloat()) "${pawns.toLong()}.0" else "$pawns"
      "$sign$formatted"
    }
    is EvaluationScore.Mate -> {
      val sign = if (evaluation.moves > 0) "+" else "-"
      "${sign}M${kotlin.math.abs(evaluation.moves)}"
    }
    null -> "0.0"
  }
}
