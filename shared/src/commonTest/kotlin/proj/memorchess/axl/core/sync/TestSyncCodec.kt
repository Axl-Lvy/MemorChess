package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.time.Instant

class TestSyncCodec {

  private val node =
    NodeSyncRow(
      positionKey = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq",
      dueDate = Instant.parse("2026-09-03T10:15:30Z"),
      lastReview = Instant.fromEpochMilliseconds(0),
      firstReview = null,
      stability = 1.5,
      difficulty = 5.0,
      reps = 3,
      lapses = 1,
      phase = "REVIEW",
      step = 0,
      isDeleted = false,
      updatedAt = Instant.parse("2026-09-03T10:15:30Z"),
      originDevice = "device-a",
      deviceSeq = 0,
    )

  private val edge =
    EdgeSyncRow(
      origin = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq",
      destination = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq",
      move = "e4",
      isGood = true,
      isDeleted = false,
      updatedAt = Instant.fromEpochMilliseconds(1),
      originDevice = "device-b",
      deviceSeq = 0,
    )

  private val setting =
    SettingSyncRow(
      key = "installedRepertoires",
      value = "london-system-white",
      isDeleted = false,
      updatedAt = Instant.parse("2100-01-01T00:00:00Z"),
      originDevice = "device-a",
      deviceSeq = 0,
    )

  @Test
  fun nodeRoundTrips() {
    val text = SYNC_JSON.encodeToString(node)
    SYNC_JSON.decodeFromString<NodeSyncRow>(text) shouldBe node
  }

  @Test
  fun edgeRoundTrips() {
    val text = SYNC_JSON.encodeToString(edge)
    SYNC_JSON.decodeFromString<EdgeSyncRow>(text) shouldBe edge
  }

  @Test
  fun settingRoundTrips() {
    val text = SYNC_JSON.encodeToString(setting)
    SYNC_JSON.decodeFromString<SettingSyncRow>(text) shouldBe setting
  }

  @Test
  fun tombstoneRoundTrips() {
    val tombstone = edge.copy(isDeleted = true)
    val text = SYNC_JSON.encodeToString(tombstone)
    SYNC_JSON.decodeFromString<EdgeSyncRow>(text) shouldBe tombstone
  }

  @Test
  fun instantsEncodeAsIso8601Strings() {
    SYNC_JSON.encodeToString(node) shouldContain "\"2026-09-03T10:15:30Z\""
  }

  @Test
  fun epochZeroInstantRoundTrips() {
    val row = edge.copy(updatedAt = Instant.fromEpochMilliseconds(0))
    SYNC_JSON.decodeFromString<EdgeSyncRow>(SYNC_JSON.encodeToString(row)) shouldBe row
  }

  @Test
  fun nullOptionalInstantRoundTrips() {
    SYNC_JSON.decodeFromString<NodeSyncRow>(SYNC_JSON.encodeToString(node)).firstReview shouldBe
      null
  }

  @Test
  fun fullPullResponseRoundTrips() {
    val response =
      SyncPullResponse(
        serverTime = Instant.parse("2026-09-03T10:16:00Z"),
        nextCursor = 42L,
        nodes = listOf(node),
        edges = listOf(edge),
        settings = listOf(setting),
      )
    SYNC_JSON.decodeFromString<SyncPullResponse>(SYNC_JSON.encodeToString(response)) shouldBe
      response
  }

  @Test
  fun emptyPullResponseRoundTripsAndTerminatesPaging() {
    val response =
      SyncPullResponse(
        serverTime = Instant.fromEpochMilliseconds(0),
        nextCursor = null,
        nodes = emptyList(),
        edges = emptyList(),
        settings = emptyList(),
      )
    val decoded = SYNC_JSON.decodeFromString<SyncPullResponse>(SYNC_JSON.encodeToString(response))
    decoded shouldBe response
    decoded.nextCursor shouldBe null
  }

  @Test
  fun pushRequestAndResponseRoundTrip() {
    val request = SyncPushRequest(listOf(node), listOf(edge), listOf(setting))
    SYNC_JSON.decodeFromString<SyncPushRequest>(SYNC_JSON.encodeToString(request)) shouldBe request

    val response =
      SyncPushResponse(
        serverTime = Instant.fromEpochMilliseconds(1),
        revision = 0L,
        rejected =
          listOf(
            RejectedRow(
              kind = "edge",
              id = "origin|destination",
              code = "illegal_move",
              reason = "illegal move",
            )
          ),
      )
    SYNC_JSON.decodeFromString<SyncPushResponse>(SYNC_JSON.encodeToString(response)) shouldBe
      response
  }

  @Test
  fun unknownFieldsAreTolerated() {
    val text =
      """{"origin":"a","destination":"b","move":"e4","isGood":true,"isDeleted":false,""" +
        """"updatedAt":"2026-09-03T10:15:30Z","originDevice":"device-b","deviceSeq":3,""" +
        """"futureField":7}"""
    SYNC_JSON.decodeFromString<EdgeSyncRow>(text).move shouldBe "e4"
  }
}
