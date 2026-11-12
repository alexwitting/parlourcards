package com.cardgames.feature.solitaire.board

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.PrimaryButton
import com.cardgames.core.ui.chrome.softSurface
import com.cardgames.core.ui.feedback.GameHaptic
import com.cardgames.core.ui.feedback.LocalGameFeedback
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.spring
import com.cardgames.core.ui.text.formatDuration
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.solitaire.motion.SolitaireMotionTokens

/** Time and moves on a soft card under the top bar, split by a hairline. */
@Composable
internal fun ScoreCard(
    elapsedSeconds: Int,
    moves: Int,
) {
    val colors = CardGamesTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .softSurface(colors.surfaceOnTable)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Figure(formatDuration(elapsedSeconds), "Time", SolitaireTags.TIME, Modifier.weight(1f))
        Box(modifier = Modifier.width(1.dp).height(22.dp).background(colors.lineOnTable))
        Figure("$moves", "Moves", SolitaireTags.MOVES, Modifier.weight(1f))
    }
}

@Composable
private fun Figure(
    value: String,
    label: String,
    valueTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Text(value, color = colors.accent, style = CardGamesTheme.type.score, modifier = Modifier.alignByBaseline().testTag(valueTag))
        Text(label.uppercase(), color = colors.onTableMuted, style = CardGamesTheme.type.toolLabel, modifier = Modifier.alignByBaseline())
    }
}

@Composable
internal fun StatusRow(line: StatusLine) {
    val colors = CardGamesTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
    ) {
        if (line.isHint) Box(modifier = Modifier.size(8.dp).background(colors.accent, CircleShape))
        Text(
            line.text,
            color = if (line.isHint) colors.onPanel else colors.onPanelMuted,
            style = CardGamesTheme.type.pill,
            modifier = Modifier.weight(1f),
        )
        line.trailing?.let { Text(it, color = colors.onPanelMuted, style = CardGamesTheme.type.rowCaption) }
    }
}

@Composable
internal fun NoMovesMessage() {
    val colors = CardGamesTheme.colors
    Text("No moves left", color = colors.onPanel, style = CardGamesTheme.type.actionLabel)
}

/**
 * Complete: on a phone always in the panel, disabled until every card is face up. When it comes on it
 * turns brass (the button's own cross-fade), pulses once 1 → 1.04 → 1 on the pop spring, and ticks —
 * or, [pulseOnAppear], as soon as it's shown (the tablet's, which only appears once it can be used).
 */
@Composable
internal fun CompleteButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Complete",
    pulseOnAppear: Boolean = false,
) {
    val feedback = LocalGameFeedback.current
    val scale = remember { Animatable(1f) }
    val pop = CardGamesMotion.Pop.spring<Float>()
    val still = LocalReduceMotion.current
    var wasEnabled by remember { mutableStateOf(enabled && !pulseOnAppear) }
    LaunchedEffect(enabled) {
        if (enabled && !wasEnabled) {
            feedback.haptic(GameHaptic.Tick)
            // Reduce motion: the cross-fade to brass and the tick, no pulse.
            if (!still) {
                scale.animateTo(SolitaireMotionTokens.COMPLETE_PULSE, pop)
                scale.animateTo(1f, pop)
            }
        }
        wasEnabled = enabled
    }
    PrimaryButton(
        text = label,
        enabled = enabled,
        onClick = onClick,
        modifier =
            modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}

/** The tablet's hint, on the felt above the draw pile: the move, and the hints left. */
@Composable
internal fun HintPill(line: StatusLine) {
    val colors = CardGamesTheme.colors
    Row(
        modifier = Modifier.background(colors.feedPill, ChromeShapes.Pill).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
    ) {
        Box(modifier = Modifier.size(8.dp).background(colors.accent, CircleShape))
        Text(line.text, color = colors.onTable, style = CardGamesTheme.type.pill)
        line.trailing?.let { Text(it, color = colors.onTableMuted, style = CardGamesTheme.type.rowCaption) }
    }
}
