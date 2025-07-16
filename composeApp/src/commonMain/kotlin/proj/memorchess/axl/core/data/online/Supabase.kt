package proj.memorchess.axl.core.data.online

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

// This is a Supabase anon (public) key. It is safe to expose in client-side code.
// See: https://supabase.com/docs/guides/auth#api-keys
private const val apiKey =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhxdm5lZ2FrcWNnbHRtcnp2b3hpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIyNDQzOTUsImV4cCI6MjA2NzgyMDM5NX0.IZreLXFrOzxdwyg2t3ckovtD_Izt4Hn-bAKbGcFjj2A" // NOSONAR

private const val url = "https://hqvnegakqcgltmrzvoxi.supabase.co"

/**
 * Creates the supabase client
 *
 * Note that it is ok to store the key in the source code as it is the anon key.
 */
fun createSupabaseClient() =
  createSupabaseClient(supabaseUrl = url, supabaseKey = apiKey) {
    install(Auth)
    install(Postgrest)
  }
