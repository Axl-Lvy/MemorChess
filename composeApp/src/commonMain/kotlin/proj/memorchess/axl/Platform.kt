package proj.memorchess.axl

interface Platform {
  val name: String
}

expect fun getPlatform(): Platform
