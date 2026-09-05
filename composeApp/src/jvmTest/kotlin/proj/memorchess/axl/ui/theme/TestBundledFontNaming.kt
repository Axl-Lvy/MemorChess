package proj.memorchess.axl.ui.theme

import io.kotest.matchers.collections.shouldBeEmpty
import java.io.File
import java.io.RandomAccessFile
import kotlin.test.Test

/**
 * Verifies that every bundled `.ttf` filename stem matches the PostScript name (nameID 6) baked
 * into the font's own `name` table, so a font imported by its filename resolves to the family the
 * OS and font tooling actually report.
 */
class TestBundledFontNaming {

  private val fontDir = File("src/commonMain/composeResources/font")

  @Test
  fun everyBundledFontFilenameMatchesItsOwnPostScriptName() {
    val mismatches =
      fontDir.listFiles { file -> file.extension == "ttf" }!!.map { file ->
        file.name to readPostScriptName(file)
      }

    mismatches.filter { (fileName, postScriptName) -> fileName.substringBeforeLast(".") != postScriptName }
      .shouldBeEmpty()
  }

  /** Reads nameID 6 (PostScript name) from a `.ttf`'s `name` table. */
  private fun readPostScriptName(file: File): String {
    RandomAccessFile(file, "r").use { raf ->
      val numTables = raf.readUInt16At(4)
      var nameTableOffset = -1L
      for (i in 0 until numTables) {
        val recordOffset = 12L + i * 16L
        val tag = raf.readTagAt(recordOffset)
        if (tag == "name") {
          nameTableOffset = raf.readUInt32At(recordOffset + 8)
          break
        }
      }
      check(nameTableOffset >= 0) { "No 'name' table in ${file.name}" }

      val count = raf.readUInt16At(nameTableOffset + 2)
      val stringAreaOffset = nameTableOffset + raf.readUInt16At(nameTableOffset + 4)
      for (i in 0 until count) {
        val recordOffset = nameTableOffset + 6 + i * 12L
        val platformId = raf.readUInt16At(recordOffset)
        val nameId = raf.readUInt16At(recordOffset + 6)
        if (nameId == 6) {
          val length = raf.readUInt16At(recordOffset + 8)
          val offset = raf.readUInt16At(recordOffset + 10)
          // Platform 3 (Windows) and 0 (Unicode) records are UTF-16BE; platform 1 (Macintosh) is
          // single-byte Mac Roman, ASCII compatible for the plain names these fonts use.
          val isUtf16 = platformId == 3 || platformId == 0
          return raf.readNameAt(stringAreaOffset + offset, length, isUtf16)
        }
      }
      error("No nameID 6 record in ${file.name}")
    }
  }

  private fun RandomAccessFile.readUInt16At(offset: Long): Int {
    seek(offset)
    return (read() shl 8) or read()
  }

  private fun RandomAccessFile.readUInt32At(offset: Long): Long {
    seek(offset)
    return ((read().toLong() shl 24) or (read().toLong() shl 16) or (read().toLong() shl 8) or read().toLong())
  }

  private fun RandomAccessFile.readTagAt(offset: Long): String {
    seek(offset)
    val bytes = ByteArray(4)
    readFully(bytes)
    return String(bytes, Charsets.US_ASCII)
  }

  private fun RandomAccessFile.readNameAt(offset: Long, length: Int, isUtf16: Boolean): String {
    seek(offset)
    val bytes = ByteArray(length)
    readFully(bytes)
    return if (isUtf16) String(bytes, Charsets.UTF_16BE) else String(bytes, Charsets.US_ASCII)
  }
}
