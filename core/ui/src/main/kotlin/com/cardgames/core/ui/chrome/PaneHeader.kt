package com.cardgames.core.ui.chrome

/** What heads a detail: its [title], an [overline] above it ("SETTINGS"), and a back chevron when [onBack] is set (tablet). */
data class PaneHeader(
    val title: String,
    val overline: String? = null,
    val onBack: (() -> Unit)? = null,
)
