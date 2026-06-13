package proj.memorchess.axl.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * A label-on-left, boxed numeric input on the right, used by the settings sections for integer
 * settings. Sibling of [KineticToggleRow] for the boolean case.
 *
 * Only digits are accepted: the input is filtered to `0-9` and capped at [maxDigits] characters, so
 * negatives, decimals and other text are structurally rejected rather than validated after the
 * fact, and the value can never overflow [Int] (six digits stays below `Int.MAX_VALUE`). The field
 * may be left empty while editing; an empty field commits nothing and keeps the previous value. A
 * valid number commits immediately through [onValueCommit].
 *
 * The displayed buffer re-seeds from [value] whenever [value] changes, so an external reset (the
 * settings Danger Zone) is reflected without the caller forcing a recomposition key.
 *
 * @param value Current integer value, shown when the field is not being edited.
 * @param onValueCommit Called with the parsed value whenever the field holds a valid number.
 * @param label Text shown on the left.
 * @param modifier External modifier applied to the root row.
 * @param maxDigits Maximum number of digits accepted. Defaults to six.
 * @param testTag Test tag applied to the text field, conventionally the backing setting's name.
 */
@Composable
fun KineticNumberField(
  value: Int,
  onValueCommit: (Int) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  maxDigits: Int = 6,
  testTag: String? = null,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  var text by remember(value) { mutableStateOf(value.toString()) }
  val shape = RoundedCornerShape(8.dp)

  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = typography.body.copy(color = palette.ink2))
    BasicTextField(
      value = text,
      onValueChange = { raw ->
        val digits = raw.filter { it.isDigit() }.take(maxDigits)
        text = digits
        digits.toIntOrNull()?.let(onValueCommit)
      },
      singleLine = true,
      textStyle = typography.mono.copy(color = palette.ink, textAlign = TextAlign.End),
      cursorBrush = SolidColor(palette.accent),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier =
        Modifier.widthIn(min = 72.dp)
          .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
      decorationBox = { inner ->
        Box(
          modifier =
            Modifier.background(color = palette.panel3, shape = shape)
              .border(width = 1.dp, color = palette.line, shape = shape)
              .padding(horizontal = 12.dp, vertical = 8.dp),
          contentAlignment = Alignment.CenterEnd,
        ) {
          inner()
        }
      },
    )
  }
}
