package proj.memorchess.axl.core.data.online

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import proj.memorchess.axl.core.config.generated.Secrets

private const val url = "https://hqvnegakqcgltmrzvoxi.supabase.co"

/**
 * Creates the supabase client
 *
 * Note that it is ok to store the key in the source code as it is the anon key.
 */
fun createSupabaseClient(): SupabaseClient {
  return createSupabaseClient(supabaseUrl = url, supabaseKey = Secrets.supabaseApiKey) {
    install(Auth)
    install(Postgrest) { defaultSchema = "memor_chess" }
  }
}
