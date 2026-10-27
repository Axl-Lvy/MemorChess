package proj.memorchess.axl.server.auth

/** Id of an authenticated user: the `sub` of their bearer token. */
@JvmInline internal value class UserId(val value: String)
