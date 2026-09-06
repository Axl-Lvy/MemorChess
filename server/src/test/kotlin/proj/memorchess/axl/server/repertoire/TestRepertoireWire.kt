package proj.memorchess.axl.server.repertoire

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.core.data.repertoire.RepertoireColor

internal class TestRepertoireWire {

  private val row =
    RepertoireRow(
      id = "london-system-white",
      version = 1,
      authorId = "author-1",
      title = "London System",
      description = "Solid.",
      side = "white",
      payloadSha256 = "a".repeat(64),
      payloadBytes = 42,
      moveCount = 73,
      status = "published",
      publishedAt = Instant.fromEpochSeconds(1_700_000_000),
    )

  @Test
  fun `toDescriptor maps the side, file path and every plain field`() {
    val descriptor = row.toDescriptor(downloadCount = 5)

    descriptor.id shouldBe "london-system-white"
    descriptor.name shouldBe "London System"
    descriptor.color shouldBe RepertoireColor.WHITE
    descriptor.description shouldBe "Solid."
    descriptor.moveCount shouldBe 73
    descriptor.file shouldBe "pgn/${"a".repeat(64)}.pgn"
  }

  @Test
  fun `toDescriptor maps side black`() {
    row.copy(side = "black").toDescriptor().color shouldBe RepertoireColor.BLACK
  }

  @Test
  fun `toDescriptor defaults downloadCount to zero when the caller has none to report`() {
    row.toDescriptor().downloadCount shouldBe 0
  }

  @Test
  fun `toDescriptor passes through the lowest non zero downloadCount`() {
    row.toDescriptor(downloadCount = 1).downloadCount shouldBe 1
  }

  @Test
  fun `toDescriptor passes through a representative large downloadCount unchanged`() {
    row.toDescriptor(downloadCount = 1_000_000).downloadCount shouldBe 1_000_000
  }

  @Test
  fun `toDescriptor passes through downloadCount at exactly Int MAX_VALUE`() {
    row.toDescriptor(downloadCount = Int.MAX_VALUE.toLong()).downloadCount shouldBe Int.MAX_VALUE
  }

  @Test
  fun `toDescriptor coerces a downloadCount one past Int MAX_VALUE down to it`() {
    row.toDescriptor(downloadCount = Int.MAX_VALUE.toLong() + 1).downloadCount shouldBe
      Int.MAX_VALUE
  }

  @Test
  fun `toDescriptor coerces Long MAX_VALUE down to Int MAX_VALUE rather than overflowing`() {
    row.toDescriptor(downloadCount = Long.MAX_VALUE).downloadCount shouldBe Int.MAX_VALUE
  }
}
