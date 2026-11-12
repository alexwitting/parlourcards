package com.cardgames.core.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The most recent non-null [value] seen — for content that must keep showing what it last showed
 * while it animates away (a popup fading out after its trigger has already cleared). A plain
 * holder, not state: it never triggers recomposition itself.
 */
@Composable
fun <T : Any> rememberLatestNonNull(value: T?): T? {
    val holder = remember { ArrayList<T>(1) }
    if (value != null) {
        holder.clear()
        holder.add(value)
    }
    return holder.firstOrNull()
}
