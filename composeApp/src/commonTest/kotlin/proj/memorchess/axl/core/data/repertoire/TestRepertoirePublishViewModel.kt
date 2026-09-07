package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import proj.memorchess.axl.core.auth.TokenResult
import proj.memorchess.axl.core.config.PUBLISHED_REPERTOIRES_SETTING
import proj.memorchess.axl.core.pgn.RepertoireExportResult
import proj.memorchess.axl.test_util.TestWithKoin

class TestRepertoirePublishViewModel : TestWithKoin() {

  override suspend fun setUp() {
    PUBLISHED_REPERTOIRES_SETTING.reset()
  }

  private val descriptor =
    RepertoireDescriptor(
      id = "my-italian-game",
      name = "Italian Game",
      color = RepertoireColor.WHITE,
      description = "d",
      moveCount = 1,
      file = "pgn/abc.pgn",
    )

  private fun viewModel(
    scope: TestScope,
    export: suspend (String) -> RepertoireExportResult = {
      RepertoireExportResult.Pgn("1. e4", 1, 1)
    },
    accessToken: suspend () -> TokenResult = { TokenResult.Ok("token") },
    publish: suspend (String, String, String, String, String, String) -> PublishOutcome =
      { _, _, _, _, _, _ ->
        PublishOutcome.Published(descriptor)
      },
    remove: suspend (String, String) -> RemoveOutcome = { _, _ -> RemoveOutcome.Removed },
    store: PublishedRepertoireStore = PublishedRepertoireStore(),
  ) =
    RepertoirePublishViewModel(
      localId = "italian-game",
      exportRepertoire = export,
      accessToken = accessToken,
      publish = publish,
      remove = remove,
      publishedStore = store,
      scope = scope,
    )

  @Test
  fun startsNotPublishedWhenTheStoreHasNoSlug() = test {
    val vm = viewModel(this)

    vm.state.value shouldBe PublishState.NotPublished
  }

  @Test
  fun startsPublishedWhenTheStoreAlreadyHasASlug() = test {
    val store =
      PublishedRepertoireStore().apply { recordPublished("italian-game", "my-italian-game") }
    val vm = viewModel(this, store = store)

    vm.state.value shouldBe PublishState.Published("my-italian-game")
  }

  @Test
  fun publishSucceedsAndRecordsTheSlug() = test {
    val store = PublishedRepertoireStore()
    val vm = viewModel(this, store = store)

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Published("my-italian-game")
    store.publishedSlug("italian-game") shouldBe "my-italian-game"
  }

  @Test
  fun publishSurfacesRemovedOnServerAndClearsTheStoredSlug() = test {
    val store =
      PublishedRepertoireStore().apply { recordPublished("italian-game", "my-italian-game") }
    val vm =
      viewModel(
        this,
        store = store,
        publish = { _, _, _, _, _, _ -> PublishOutcome.RemovedOnServer },
      )

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.RemovedOnServer)
    store.publishedSlug("italian-game") shouldBe null
  }

  @Test
  fun publishSurfacesSignedOutWithoutCallingTheNetwork() = test {
    var networkCalled = false
    val vm =
      viewModel(
        this,
        accessToken = { TokenResult.SignedOut },
        publish = { _, _, _, _, _, _ ->
          networkCalled = true
          PublishOutcome.Published(descriptor)
        },
      )

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.SignedOut)
    networkCalled shouldBe false
  }

  @Test
  fun publishFailsLocallyWithoutCallingTheNetworkWhenTheExportIsOverTheMoveCap() = test {
    var networkCalled = false
    val vm =
      viewModel(
        this,
        export = {
          RepertoireExportResult.Pgn("1. e4", RepertoirePublishLimits.MAX_REPERTOIRE_MOVES + 1, 1)
        },
        publish = { _, _, _, _, _, _ ->
          networkCalled = true
          PublishOutcome.Published(descriptor)
        },
      )

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    (vm.state.value as PublishState.Failed).error should
      beInstanceOf<PublishError.PayloadTooLarge>()
    networkCalled shouldBe false
  }

  @Test
  fun removeSucceedsAndClearsTheStoredSlug() = test {
    val store =
      PublishedRepertoireStore().apply { recordPublished("italian-game", "my-italian-game") }
    val vm = viewModel(this, store = store)

    vm.remove()
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.NotPublished
    store.publishedSlug("italian-game") shouldBe null
  }

  @Test
  fun removeIsANoOpWhenNotCurrentlyPublished() = test {
    val vm = viewModel(this)

    vm.remove()
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.NotPublished
  }

  @Test
  fun publishFailsWhenTheRepertoireHasNothingTaggedToExport() = test {
    val vm = viewModel(this, export = { RepertoireExportResult.Empty })

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.NothingToPublish)
  }

  @Test
  fun publishFailsLocallyWhenThePayloadIsOverTheByteCap() = test {
    var networkCalled = false
    val oversized = "x".repeat(RepertoirePublishLimits.MAX_REPERTOIRE_PAYLOAD_BYTES + 1)
    val vm =
      viewModel(
        this,
        export = { RepertoireExportResult.Pgn(oversized, 1, 1) },
        publish = { _, _, _, _, _, _ ->
          networkCalled = true
          PublishOutcome.Published(descriptor)
        },
      )

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    (vm.state.value as PublishState.Failed).error should
      beInstanceOf<PublishError.PayloadTooLarge>()
    networkCalled shouldBe false
  }

  @Test
  fun publishFailsLocallyWhenALineGoesPastTheDepthCap() = test {
    var networkCalled = false
    val vm =
      viewModel(
        this,
        export = {
          RepertoireExportResult.Pgn("1. e4", 1, RepertoirePublishLimits.MAX_PLY_DEPTH + 1)
        },
        publish = { _, _, _, _, _, _ ->
          networkCalled = true
          PublishOutcome.Published(descriptor)
        },
      )

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    (vm.state.value as PublishState.Failed).error should
      beInstanceOf<PublishError.PayloadTooLarge>()
    networkCalled shouldBe false
  }

  @Test
  fun publishSurfacesInvalidPayloadFromTheServer() = test {
    val vm =
      viewModel(
        this,
        publish = { _, _, _, _, _, _ -> PublishOutcome.InvalidPayload("bad pgn") },
      )

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.InvalidPayload("bad pgn"))
  }

  @Test
  fun publishSurfacesPayloadTooLargeFromTheServer() = test {
    val vm =
      viewModel(
        this,
        publish = { _, _, _, _, _, _ -> PublishOutcome.PayloadTooLarge("too big") },
      )

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.PayloadTooLarge("too big"))
  }

  @Test
  fun publishSurfacesForbidden() = test {
    val vm = viewModel(this, publish = { _, _, _, _, _, _ -> PublishOutcome.Forbidden })

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.Forbidden)
  }

  @Test
  fun publishSurfacesQuotaExceeded() = test {
    val vm =
      viewModel(
        this,
        publish = { _, _, _, _, _, _ -> PublishOutcome.QuotaExceeded("too many") },
      )

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.QuotaExceeded("too many"))
  }

  @Test
  fun publishSurfacesRateLimited() = test {
    val vm = viewModel(this, publish = { _, _, _, _, _, _ -> PublishOutcome.RateLimited })

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.RateLimited)
  }

  @Test
  fun publishSurfacesAGenericServerFailure() = test {
    val vm = viewModel(this, publish = { _, _, _, _, _, _ -> PublishOutcome.Failed("boom") })

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.ServerError)
  }

  @Test
  fun publishSurfacesRateLimitedWhenTheTokenLookupIsTransientlyFailing() = test {
    var networkCalled = false
    val vm =
      viewModel(
        this,
        accessToken = { TokenResult.Failed.Transient },
        publish = { _, _, _, _, _, _ ->
          networkCalled = true
          PublishOutcome.Published(descriptor)
        },
      )

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.RateLimited)
    networkCalled shouldBe false
  }

  @Test
  fun publishSurfacesSignedOutWhenTheTokenLookupTerminallyFails() = test {
    var networkCalled = false
    val vm =
      viewModel(
        this,
        accessToken = { TokenResult.Failed.Terminal },
        publish = { _, _, _, _, _, _ ->
          networkCalled = true
          PublishOutcome.Published(descriptor)
        },
      )

    vm.publish(slug = "my-italian-game", title = "Italian Game", description = "d", side = "white")
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.SignedOut)
    networkCalled shouldBe false
  }

  @Test
  fun removeSurfacesForbidden() = test {
    val store =
      PublishedRepertoireStore().apply { recordPublished("italian-game", "my-italian-game") }
    val vm = viewModel(this, store = store, remove = { _, _ -> RemoveOutcome.Forbidden })

    vm.remove()
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.Forbidden)
  }

  @Test
  fun removeSurfacesSignedOut() = test {
    val store =
      PublishedRepertoireStore().apply { recordPublished("italian-game", "my-italian-game") }
    val vm = viewModel(this, store = store, accessToken = { TokenResult.SignedOut })

    vm.remove()
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.SignedOut)
  }

  @Test
  fun removeSurfacesRateLimited() = test {
    val store =
      PublishedRepertoireStore().apply { recordPublished("italian-game", "my-italian-game") }
    val vm = viewModel(this, store = store, remove = { _, _ -> RemoveOutcome.RateLimited })

    vm.remove()
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.RateLimited)
  }

  @Test
  fun removeSurfacesAGenericServerFailure() = test {
    val store =
      PublishedRepertoireStore().apply { recordPublished("italian-game", "my-italian-game") }
    val vm = viewModel(this, store = store, remove = { _, _ -> RemoveOutcome.Failed("boom") })

    vm.remove()
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.Failed(PublishError.ServerError)
  }

  @Test
  fun removeTreatsNotFoundAsAlreadyRemoved() = test {
    val store =
      PublishedRepertoireStore().apply { recordPublished("italian-game", "my-italian-game") }
    val vm = viewModel(this, store = store, remove = { _, _ -> RemoveOutcome.NotFound })

    vm.remove()
    advanceUntilIdle()

    vm.state.value shouldBe PublishState.NotPublished
    store.publishedSlug("italian-game") shouldBe null
  }
}
