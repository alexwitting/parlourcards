package com.cardgames.core.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.ui.text.initials
import com.cardgames.core.ui.theme.CardGamesTheme

/** A Gin rival's avatar, [size] across, in their own colours ([rivalAvatarColors]). */
@Composable
fun RivalAvatar(
    rival: GinDifficulty,
    size: Dp,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
) {
    val (fill, ink) = rivalAvatarColors(rival)
    Avatar(rival.initials, fill = fill, ink = ink, size = size, modifier = modifier, style = style)
}

/** Each rival's avatar fill and ink: Milo sage, Rosa burgundy, Vex deep brass. */
@Composable
fun rivalAvatarColors(rival: GinDifficulty): Pair<Color, Color> {
    val colors = CardGamesTheme.colors
    return when (rival) {
        GinDifficulty.EASY -> colors.hint to colors.onAccent
        GinDifficulty.MEDIUM -> colors.opponent to colors.onTable
        GinDifficulty.HARD -> colors.accentDeep to colors.panel
    }
}
