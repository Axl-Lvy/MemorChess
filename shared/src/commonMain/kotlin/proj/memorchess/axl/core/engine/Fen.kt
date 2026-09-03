package proj.memorchess.axl.core.engine

import kotlin.jvm.JvmInline

/** Full 6-part FEN string representing a complete chess position with move counters. */
@JvmInline value class Fen(val value: String)
