package proj.memorchess.axl.ui.components.settings

/**
 * [IButtonParams] representing a slider button.
 *
 * @property min Minimum value of the slider
 * @property max Maximum value of the slider
 * @property steps Number of steps in the slider
 * @property setCallBack Callback function after each slider change
 * @property convertToUnit Function to convert the value from settings to float
 * @property displayText Function to generate the text to display based on the value
 */
class SliderParams(
  val min: Float,
  val max: Float,
  val steps: Int,
  val setCallBack: (Float) -> Unit,
  val convertToUnit: (Any) -> Float,
  val displayText: (Float) -> String,
) : IButtonParams
