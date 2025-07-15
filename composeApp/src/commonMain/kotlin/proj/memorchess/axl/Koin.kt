package proj.memorchess.axl

import io.github.jan.supabase.SupabaseClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import proj.memorchess.axl.core.data.online.auth.SupabaseAuthManager
import proj.memorchess.axl.core.data.online.database.RemoteDatabaseManager
import proj.memorchess.axl.core.data.online.createSupabaseClient

/**
 * Initializes koin modules
 *
 * @return An array of all koin modules
 */
fun initKoinModules(): Array<Module> {
  val dataModule = module {
    single<SupabaseClient> { createSupabaseClient() }
    singleOf(::SupabaseAuthManager)
    singleOf(::RemoteDatabaseManager)
  }
  return arrayOf(dataModule)
}
