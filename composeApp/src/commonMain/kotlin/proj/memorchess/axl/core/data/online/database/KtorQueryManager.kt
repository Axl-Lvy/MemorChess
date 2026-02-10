package proj.memorchess.axl.core.data.online.database

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.online.auth.KtorAuthManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.shared.data.MoveFetched
import proj.memorchess.axl.shared.data.NodeFetched
import proj.memorchess.axl.shared.routes.DataRoutes

private const val USER_NOT_CONNECTED_MESSAGE = "User must be logged in to update database"

class KtorQueryManager(
  private val httpClient: HttpClient,
  private val authManager: KtorAuthManager,
) : DatabaseQueryManager {

  private fun requireAuth() {
    check(authManager.isUserLoggedIn()) { USER_NOT_CONNECTED_MESSAGE }
  }

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> {
    requireAuth()
    val moves = httpClient.get(DataRoutes.Moves(withDeletedOnes = withDeletedOnes)).body<List<MoveFetched>>()
    return movesToDataNodes(moves, withDeletedOnes)
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): DataNode? {
    requireAuth()
    val response = httpClient.get(DataRoutes.Node(fen = positionIdentifier.fenRepresentation))
    if (response.status == HttpStatusCode.NotFound) return null
    val node = response.body<NodeFetched>()
    return nodeToDataNode(node)
  }

  override suspend fun deletePosition(position: PositionIdentifier, updatedAt: Instant) {
    requireAuth()
    httpClient.delete(DataRoutes.Node(fen = position.fenRepresentation, updatedAt = updatedAt))
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String, updatedAt: Instant) {
    requireAuth()
    httpClient.delete(DataRoutes.Move(fen = origin.fenRepresentation, move = move, updatedAt = updatedAt))
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    requireAuth()
    httpClient.delete(DataRoutes.All(hardFrom = hardFrom, updatedAt = DateUtil.now()))
  }

  override suspend fun insertNodes(vararg positions: DataNode) {
    requireAuth()
    val moves = dataNodesToMoves(positions.toList())
    httpClient.post(DataRoutes.Moves()) {
      contentType(ContentType.Application.Json)
      setBody(moves)
    }
  }

  override suspend fun getLastUpdate(): Instant? {
    requireAuth()
    val response = httpClient.get(DataRoutes.LastUpdate())
    if (response.status == HttpStatusCode.NoContent) return null
    return response.body<Instant>()
  }

  override fun isActive(): Boolean {
    return authManager.isUserLoggedIn()
  }
}
