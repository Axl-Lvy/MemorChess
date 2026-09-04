package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class TestApiError {

  @Test
  fun `round trips through the sync codec`() {
    val error = ApiError(code = ApiErrorCode.BAD_REQUEST, message = "since must be non negative")

    val encoded = SYNC_JSON.encodeToString(error)

    SYNC_JSON.decodeFromString<ApiError>(encoded) shouldBe error
  }

  @Test
  fun `encodes the code as a plain string so an unknown one still decodes`() {
    val encoded = """{"code":"invented_later","message":"from a newer server"}"""

    val decoded = SYNC_JSON.decodeFromString<ApiError>(encoded)

    decoded.code shouldBe "invented_later"
  }

  @Test
  fun `keeps the message out of the code`() {
    val error = ApiError(code = ApiErrorCode.INTERNAL, message = "something broke")

    SYNC_JSON.encodeToString(error) shouldContain "\"code\":\"internal\""
  }
}
