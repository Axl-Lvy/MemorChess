package proj.memorchess.axl.core.data.online.database

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.PostgrestFilterDSL
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import io.github.jan.supabase.postgrest.query.PostgrestUpdate
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.github.jan.supabase.postgrest.rpc
import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds

private const val USER_NOT_CONNECTED_MESSAGE = "User must be logged in to update database"

class RemoteDatabaseQueryManager(
  private val client: SupabaseClient,
  private val authManager: AuthManager,
) : DatabaseQueryManager {

  private suspend fun PostgrestQueryBuilder.updateWithUserFilter(
    update: PostgrestUpdate.() -> Unit,
    block: @PostgrestFilterDSL (PostgrestFilterBuilder.() -> Unit) = {},
  ): PostgrestResult {
    throw NotImplementedError("This method is not implemented yet.")
  }

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<StoredNode> {
    throw NotImplementedError("This method is not implemented yet.")
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    throw NotImplementedError("This method is not implemented yet.")
  }

  override suspend fun deletePosition(fen: String) {
    throw NotImplementedError("This method is not implemented yet.")
  }

  override suspend fun deleteMove(origin: String, move: String) {
    throw NotImplementedError("This method is not implemented yet.")
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
    throw NotImplementedError("This method is not implemented yet.")
  }

  override suspend fun insertNodes(vararg positions: StoredNode) {
    throw NotImplementedError("This method is not implemented yet.")
  }

  override suspend fun getLastUpdate(): LocalDateTime? {
    throw NotImplementedError("This method is not implemented yet.")
  }

  private suspend fun getLastTableUpdate(table: Table): LocalDateTime? {
    throw NotImplementedError("This method is not implemented yet.")
  }

  override fun isActive(): Boolean {
    return authManager.user != null && isSynced
  }
}
