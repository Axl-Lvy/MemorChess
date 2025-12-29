package proj.memorchess.axl.shared.routes

import io.ktor.resources.Resource
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Type-safe route definitions for the MemorChess API. These resources are shared between client and
 * server.
 */
@Serializable
@Resource("/data")
class DataRoutes {
  /** Route for fetching all moves for the authenticated user. GET /data/moves */
  @Serializable @Resource("moves") class Moves(val parent: DataRoutes = DataRoutes())

  /** Route for fetching or deleting data for a specific position. GET /data/node/{fen} */
  @Serializable
  @Resource("node/{fen}")
  class Node(val parent: DataRoutes = DataRoutes(), val fen: String)

  /** Route for deleting a specific move. DELETE /data/move/{fen}/{move} */
  @Serializable
  @Resource("move/{fen}/{move}")
  class Move(val parent: DataRoutes = DataRoutes(), val fen: String, val move: String)

  /** Route for deleting all user data. DELETE /data/all */
  @Serializable
  @Resource("all")
  class All(val parent: DataRoutes = DataRoutes(), val hardFrom: Instant? = null)

  /** Route for getting last update timestamp. GET /data/last-update */
  @Serializable @Resource("last-update") class LastUpdate(val parent: DataRoutes = DataRoutes())
}

@Serializable
@Resource("/user")
class UserRoutes {
  /** Route for creating a new user account. POST /user/signup */
  @Serializable @Resource("signup") class Signup(val parent: UserRoutes = UserRoutes())

  /**
   * Route for checking if the current user has a specific permission. GET
   * /user/permission/{permission}
   */
  @Serializable
  @Resource("permission/{permission}")
  class Permission(val parent: UserRoutes = UserRoutes(), val permission: String)
}
