package proj.memorchess.axl.test_util

import androidx.compose.runtime.Composable

@Composable
actual fun ProvideTestResourceReader(content: @Composable () -> Unit) {
  content()
}
