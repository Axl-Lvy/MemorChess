package proj.ankichess.axl

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform