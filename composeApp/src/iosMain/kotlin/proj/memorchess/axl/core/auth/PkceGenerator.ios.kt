package proj.memorchess.axl.core.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun randomBytes(size: Int): ByteArray = memScoped {
  val buffer = allocArray<UByteVar>(size)
  val status = SecRandomCopyBytes(kSecRandomDefault, size.toULong(), buffer)
  check(status == 0) { "SecRandomCopyBytes failed with status $status" }
  buffer.readBytes(size)
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun sha256(bytes: ByteArray): ByteArray {
  val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
  bytes.usePinned { inputPin ->
    digest.usePinned { outPin ->
      CC_SHA256(inputPin.addressOf(0), bytes.size.toUInt(), outPin.addressOf(0))
    }
  }
  return digest.toByteArray()
}
