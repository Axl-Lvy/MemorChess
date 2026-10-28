package proj.memorchess.axl.core.engine.evaluation

import fr.axl_lvy.stockfish_multiplatform.EngineDownload
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TestEngineDownloadLabel {

  @Test
  fun zeroBytesIsZeroPercent() {
    engineDownloadLabel(EngineDownload.InProgress(receivedBytes = 0, totalBytes = 1_000)) shouldBe
      "0%"
  }

  @Test
  fun firstByteRoundsDownToZeroPercent() {
    engineDownloadLabel(EngineDownload.InProgress(receivedBytes = 1, totalBytes = 1_000)) shouldBe
      "0%"
  }

  @Test
  fun justBelowOnePercent() {
    engineDownloadLabel(EngineDownload.InProgress(receivedBytes = 9, totalBytes = 1_000)) shouldBe
      "0%"
  }

  @Test
  fun exactlyOnePercent() {
    engineDownloadLabel(EngineDownload.InProgress(receivedBytes = 10, totalBytes = 1_000)) shouldBe
      "1%"
  }

  @Test
  fun justBelowTheTotalStaysBelowOneHundred() {
    engineDownloadLabel(EngineDownload.InProgress(receivedBytes = 999, totalBytes = 1_000)) shouldBe
      "99%"
  }

  @Test
  fun theTotalIsOneHundredPercent() {
    engineDownloadLabel(
      EngineDownload.InProgress(receivedBytes = 1_000, totalBytes = 1_000)
    ) shouldBe "100%"
  }

  @Test
  fun moreThanTheTotalIsCappedAtOneHundredPercent() {
    engineDownloadLabel(
      EngineDownload.InProgress(receivedBytes = 1_001, totalBytes = 1_000)
    ) shouldBe "100%"
  }

  @Test
  fun halfOfTheFullEngine() {
    engineDownloadLabel(
      EngineDownload.InProgress(receivedBytes = 56_503_670, totalBytes = 113_007_340)
    ) shouldBe "50%"
  }

  @Test
  fun notStartedHasNoLabel() {
    engineDownloadLabel(EngineDownload.NotStarted).shouldBeNull()
  }

  @Test
  fun doneHasNoLabel() {
    engineDownloadLabel(EngineDownload.Done).shouldBeNull()
  }

  @Test
  fun failedHasNoLabel() {
    engineDownloadLabel(EngineDownload.Failed("HTTP 503")).shouldBeNull()
  }
}
