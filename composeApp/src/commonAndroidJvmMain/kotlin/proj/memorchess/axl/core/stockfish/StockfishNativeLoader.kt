package proj.memorchess.axl.core.stockfish

import com.diamondedge.logging.logging
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val LOGGER = logging()

/**
 * Utility object responsible for loading the native Stockfish library (jstockfish) at runtime.
 *
 * This loader handles cross-platform library loading by:
 * - Extracting the appropriate native library from JAR resources based on the operating system
 * - Creating temporary files with proper permissions
 * - Falling back to standard system library loading if extraction fails
 *
 * Supported platforms:
 * - Windows (jstockfish.dll)
 * - macOS (libjstockfish.dylib)
 * - Linux/Unix (libjstockfish.so)
 */
object StockfishNativeLoader {

  /**
   * Loads the native Stockfish library.
   *
   * Attempts to load the library from JAR resources first. If that fails, falls back to standard
   * system library loading using [System.loadLibrary].
   *
   * @throws UnsatisfiedLinkError if both loading methods fail
   */
  fun load() {
    try {
      loadLibraryFromJar()
    } catch (e: Exception) {
      LOGGER.error { "Failed to load native library from JAR: ${e.message}" }
      // Fallback to standard library loading
      try {
        System.loadLibrary("jstockfish")
      } catch (e: Exception) {
        LOGGER.error { "Failed to load native library: ${e.message}" }
        throw UnsatisfiedLinkError("Could not load jstockfish library: ${e.message}")
      }
    }
  }

  /**
   * Extracts and loads the native library from JAR resources.
   *
   * This method:
   * 1. Determines the correct library filename based on the operating system
   * 2. Creates a temporary directory for the extracted library
   * 3. Extracts the library from resources to the temporary file
   * 4. Sets executable permissions on Unix-like systems
   * 5. Loads the library using [System.load]
   *
   * @throws IllegalStateException if the library resource is not found in the JAR
   * @throws Exception for any I/O or loading errors
   */
  private fun loadLibraryFromJar() {
    val libraryName =
      when {
        System.getProperty("os.name").contains("Windows", ignoreCase = true) -> "jstockfish.dll"
        System.getProperty("os.name").contains("Mac", ignoreCase = true) -> "libjstockfish.dylib"
        else -> "libjstockfish.so"
      }

    val tempDir = createTempDirectory()
    val tempFile = File(tempDir, libraryName)

    // Extract library from resources
    javaClass.getResourceAsStream("/$libraryName")?.use { input ->
      Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } ?: throw IllegalStateException("Could not find $libraryName in resources")

    // Set file permissions if on Unix-like system
    if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
      tempFile.setExecutable(true)
    }

    System.loadLibrary(tempFile.absolutePath)
    LOGGER.info { "Successfully loaded $libraryName from: ${tempFile.absolutePath}" }
  }

  /**
   * Creates a temporary directory for extracting the native library.
   *
   * The directory is marked for deletion on JVM exit to ensure cleanup.
   *
   * @return a [File] representing the created temporary directory
   */
  private fun createTempDirectory(): File {
    val tempDir = Files.createTempDirectory("jstockfish").toFile()
    tempDir.deleteOnExit()
    return tempDir
  }
}
