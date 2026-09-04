package proj.memorchess.axl.core.data

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TestDirtyKey {

  @kotlin.test.Test
  fun `two node keys over the same position are equal`() {
    // Arrange & Act.
    val a: DirtyKey = DirtyKey.NodeKey(PositionKey("k1"))
    val b: DirtyKey = DirtyKey.NodeKey(PositionKey("k1"))

    // Assert.
    a shouldBe b
  }

  @kotlin.test.Test
  fun `an edge key is not equal to a node key over the same string`() {
    // Arrange & Act.
    val edge: DirtyKey = DirtyKey.EdgeKey(PositionKey("k1"), PositionKey("k1"))
    val node: DirtyKey = DirtyKey.NodeKey(PositionKey("k1"))

    // Assert.
    edge shouldNotBe node
  }

  @kotlin.test.Test
  fun `edge keys with swapped origin and destination are not equal`() {
    // Arrange & Act.
    val a: DirtyKey = DirtyKey.EdgeKey(PositionKey("k1"), PositionKey("k2"))
    val b: DirtyKey = DirtyKey.EdgeKey(PositionKey("k2"), PositionKey("k1"))

    // Assert.
    a shouldNotBe b
  }

  @kotlin.test.Test
  fun `setting keys compare by their string key`() {
    // Arrange & Act.
    val a: DirtyKey = DirtyKey.SettingKey("appTheme")
    val b: DirtyKey = DirtyKey.SettingKey("appTheme")

    // Assert.
    a shouldBe b
  }

  @kotlin.test.Test
  fun `setting keys with different strings are not equal`() {
    // Arrange & Act.
    val a: DirtyKey = DirtyKey.SettingKey("appTheme")
    val b: DirtyKey = DirtyKey.SettingKey("chessBoardColor")

    // Assert.
    a shouldNotBe b
  }
}
