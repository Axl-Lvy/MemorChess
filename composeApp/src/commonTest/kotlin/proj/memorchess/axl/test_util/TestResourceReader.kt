package proj.memorchess.axl.test_util

import androidx.compose.runtime.Composable

/** Provides [content] with a resource reader that completes each read without suspending. */
@Composable expect fun ProvideTestResourceReader(content: @Composable () -> Unit)
