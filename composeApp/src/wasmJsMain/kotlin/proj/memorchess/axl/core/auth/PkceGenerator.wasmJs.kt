@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package proj.memorchess.axl.core.auth

import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

internal actual fun randomBytes(size: Int): ByteArray {
  val buffer = Uint8Array(size)
  cryptoGetRandomValues(buffer)
  val result = ByteArray(size)
  for (i in 0 until size) {
    result[i] = buffer[i]
  }
  return result
}

internal actual suspend fun sha256(bytes: ByteArray): ByteArray {
  val input = byteArrayToUint8Array(bytes)
  val digest: ArrayBuffer = cryptoSubtleDigest("SHA-256", input).await()
  val view = Int8Array(digest)
  val out = ByteArray(view.length)
  for (i in 0 until view.length) {
    out[i] = view[i]
  }
  return out
}

private fun cryptoGetRandomValues(buffer: Uint8Array) {
  js("globalThis.crypto.getRandomValues(buffer)")
}

private fun cryptoSubtleDigest(algorithm: String, data: Uint8Array): Promise<ArrayBuffer> =
  js("globalThis.crypto.subtle.digest(algorithm, data)")

/**
 * Converts a Kotlin [ByteArray] into a Uint8Array view.
 *
 * The Uint8Array constructor accepts a JS array of numbers; we hand it the bytes one by one using a
 * loop because there is no direct ByteArray->Uint8Array cast in wasmJs.
 */
private fun byteArrayToUint8Array(bytes: ByteArray): Uint8Array {
  val array = Uint8Array(bytes.size)
  for (i in bytes.indices) {
    setByteJs(array, i, bytes[i].toInt() and 0xff)
  }
  return array
}

private fun setByteJs(array: Uint8Array, index: Int, value: Int) {
  js("array[index] = value")
}
