package proj.memorchess.axl.shared.routes

import io.ktor.resources.Resource
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

  /** Route for fetching data for a specific position. GET /data/node/{fen} */
  @Serializable
  @Resource("node/{fen}")
  class Node(val parent: DataRoutes = DataRoutes(), val fen: String)
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
