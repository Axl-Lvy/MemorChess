package proj.memorchess.axl.core.data.online

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

val supabase =
  createSupabaseClient(
    supabaseUrl = "https://hqvnegakqcgltmrzvoxi.supabase.co",
    supabaseKey =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhxdm5lZ2FrcWNnbHRtcnp2b3hpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIyNDQzOTUsImV4cCI6MjA2NzgyMDM5NX0.IZreLXFrOzxdwyg2t3ckovtD_Izt4Hn-bAKbGcFjj2A",
  ) {
    install(Auth)
    install(Postgrest)
  }

suspend fun signIn(providedEmail: String, providedPassword: String) {
  supabase.auth.signInWith(Email) {
    email = providedEmail
    password = providedPassword
  }
}
