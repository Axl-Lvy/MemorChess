package proj.memorchess.axl.ui.util

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/** Matches nodes whose click action carries [label]. */
fun hasClickLabel(label: String) =
  SemanticsMatcher("Clickable action with label: $label") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
  }
