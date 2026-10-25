@file:OptIn(ExperimentalResourceApi::class)

package proj.memorchess.axl.test_util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.ResourceReader
import org.w3c.xhr.XMLHttpRequest

/**
 * Compose Multiplatform 1.12 loads web resources with an asynchronous `fetch`, which cannot resolve
 * while a test is between two event loop turns. A blocking read lets a resource load complete in
 * the single [kotlinx.coroutines.Dispatchers.Default] hop that `setTestContent` and the wait
 * helpers perform.
 */
@Composable
actual fun ProvideTestResourceReader(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalResourceReader provides BlockingResourceReader, content = content)
}

/** Reads resources with a synchronous (blocking) `XMLHttpRequest`. */
private object BlockingResourceReader : ResourceReader {
  override suspend fun read(path: String): ByteArray = readBytes(path)

  override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray =
    readBytes(path).sliceArray(offset.toInt() until (offset + size).toInt())

  override fun getUri(path: String): String = "./$path"

  private fun readBytes(path: String): ByteArray {
    val request = XMLHttpRequest()
    request.open("GET", getUri(path), false)
    // A blocking request can only return text; this charset maps each byte to one char.
    request.overrideMimeType("text/plain; charset=x-user-defined")
    request.send()
    if (request.status != 200.toShort()) throw MissingResourceException(getUri(path))
    val text = request.responseText
    return ByteArray(text.length) { text[it].code.toByte() }
  }
}
