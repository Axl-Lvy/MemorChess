package proj.memorchess.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import java.time.OffsetDateTime
import java.util.UUID
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import proj.memorchess.server.db.AppUserTable
import proj.memorchess.shared.USER_ID_HEADER

private val UserIdAttributeKey = io.ktor.util.AttributeKey<UUID>("userId")

/**
 * Ktor plugin that reads the [USER_ID_HEADER] header, upserts the user into the `app_user` table,
 * and stores the UUID in [ApplicationCall.attributes].
 *
 * Returns HTTP 400 if the header is missing on non-health endpoints.
 */
private val UserPluginInstance =
  createRouteScopedPlugin("UserPlugin") {
    onCall { call ->
      if (call.request.local.uri.startsWith("/api/health")) return@onCall

      val headerValue = call.request.headers[USER_ID_HEADER]
      if (headerValue == null) {
        call.respondText("Missing $USER_ID_HEADER header", status = HttpStatusCode.BadRequest)
        return@onCall
      }

      val userId =
        try {
          UUID.fromString(headerValue)
        } catch (_: IllegalArgumentException) {
          call.respondText(
            "Invalid UUID in $USER_ID_HEADER header",
            status = HttpStatusCode.BadRequest,
          )
          return@onCall
        }

      transaction {
        AppUserTable.insertIgnore {
          it[id] = userId
          it[createdAt] = OffsetDateTime.now()
        }
      }

      call.attributes.put(UserIdAttributeKey, userId)
    }
  }

/** Installs the [UserPluginInstance] into the application. */
fun Application.configureUserPlugin() {
  install(UserPluginInstance)
}

/** Retrieves the authenticated user's UUID from the call attributes. */
val ApplicationCall.userId: UUID
  get() = attributes[UserIdAttributeKey]
