package proj.memorchess.axl.server

import io.ktor.server.application.Application
import io.ktor.server.application.log

fun Application.seedUsers() {
  val testUsers =
    listOf(
      "email1@example.com" to "password1234@",
      "email2@example.com" to "password1234@",
      "email3@example.com" to "password1234@",
      "email4@example.com" to "password1234@",
      "email5@example.com" to "password1234@",
      "testuser11@axl.com" to "4q2h65QPuXeY",
    )

  for ((email, password) in testUsers) {
    val user = createUser(email, password)
    if (user != null) {
      log.info("Seeded user: $email")
    }
  }
}
