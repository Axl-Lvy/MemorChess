package proj.memorchess.axl.server.routes

import io.ktor.server.plugins.BadRequestException
import io.ktor.server.routing.RoutingContext

/**
 * The caller's `limit` query parameter, [default] when absent and clamped to [max]: page size is a
 * hint, and refusing a large one only makes a client guess.
 *
 * @throws BadRequestException when the parameter is not a positive integer.
 */
internal fun RoutingContext.pageLimit(default: Int, max: Int): Int {
  val raw = call.request.queryParameters["limit"] ?: return default
  val limit = raw.toIntOrNull()
  if (limit == null || limit <= 0) {
    throw BadRequestException("limit must be a positive integer, was '$raw'")
  }
  return minOf(limit, max)
}
