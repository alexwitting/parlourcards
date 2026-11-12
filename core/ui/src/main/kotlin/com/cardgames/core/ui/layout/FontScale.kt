package com.cardgames.core.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * The most a game screen lets the phone's font size setting grow its text. A table is laid out on
 * fixed card geometry — pile labels between piles, chips beside a speech bubble, tool captions
 * under 48dp tiles — and from about 1.5× its labels run into each other and get cut off. Pages
 * that scroll (Settings, Stats, match setup, How to play) aren't capped.
 */
const val TABLE_MAX_FONT_SCALE = 1.3f

/**
 * [content] with the font scale held to [max]. Below it the phone's own scaling passes through
 * untouched (including Android 14's non-linear scaling of large text); above it, text is drawn at
 * [max].
 */
@Composable
fun CapFontScale(
    max: Float = TABLE_MAX_FONT_SCALE,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, max), content = content)
    }
}
