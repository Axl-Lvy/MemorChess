package proj.memorchess.axl.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlin.test.Test
import proj.memorchess.axl.shared.data.MoveFetched

class TestPersonalData {
  @Test
  fun testGetPosition() = testApplicationWithTestModule {
    client.get("/data/moves").apply {
      println("Response: ${this}")
      assert(status.value == 200)
      val moves = body<List<MoveFetched>>()
      assert(moves.size == 6)
    }
  }
}
