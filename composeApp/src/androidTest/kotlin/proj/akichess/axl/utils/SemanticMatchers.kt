package proj.akichess.axl.utils

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

// matcher based on the click label
fun hasClickLabel(label: String) =
  SemanticsMatcher("Clickable action with label: $label") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
  }
