package proj.memorchess.axl.ui.components.popup

import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import proj.memorchess.axl.AndroidContextProvider

private var currentToast: Toast? = null
private val toastScope = CoroutineScope(Dispatchers.Main)

actual fun getPlatformSpecificToastRenderer() = ToastRenderer { message, _ ->
  toastScope.launch {
    val text = getString(message)
    currentToast?.cancel()
    val newToast = Toast.makeText(AndroidContextProvider.context, text, Toast.LENGTH_LONG)
    newToast.show()
    currentToast = newToast
  }
}
