package com.cardgames.feature.ginrummy.result

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandResult
import com.cardgames.core.ui.cards.CARD_ASPECT_RATIO
import com.cardgames.core.ui.cards.PlayingCard
import com.cardgames.core.ui.chrome.TableChip
import com.cardgames.core.ui.chrome.TablePrimaryButton
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.spring
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.text.opponentName
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.ginrummy.GinIntent
import com.cardgames.feature.ginrummy.GinUiState
import com.cardgames.feature.ginrummy.board.fade
import com.cardgames.feature.ginrummy.motion.GinMotionTokens
import com.cardgames.feature.ginrummy.opponentName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Gin moment over felt rays and confetti: "GIN!" popping in after a beat, the hand fanned
 * out, the bonus maths on a cream card — and, when it ends the match, the final score and a rematch.
 */
@Composable
internal fun ColumnScope.GinCelebration(
    game: GinState,
    result: HandResult,
    state: GinUiState,
    onIntent: (GinIntent) -> Unit,
    onHome: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    val title = remember { Animatable(0f) }
    val figures = remember { Animatable(0f) }
    val pop = CardGamesMotion.Pop.spring<Float>()
    val fade = CardGamesMotion.Quick.tween<Float>()
    val still = LocalReduceMotion.current
    LaunchedEffect(Unit) {
        delay(GinMotionTokens.RESULT_TITLE_BEAT)
        // Reduce motion: the shout is simply there; only the figures fade in.
        launch { if (still) title.snapTo(1f) else title.animateTo(1f, pop) }
        figures.animateTo(1f, fade)
    }
    val bonus = handBonus(result)
    val matchOver = game.phase as? GinPhase.MatchOver
    val base = with(LocalDensity.current) { SHOUT_BASE.toPx() }
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ginResultMetrics.blockGap),
    ) {
        OverlineChip("Hand ${game.handNumber} · Zero deadwood")
        Text(
            "GIN!",
            color = colors.accent,
            style = CardGamesTheme.type.shout.copy(shadow = Shadow(colors.actionBase, Offset(0f, base))),
            modifier =
                Modifier.graphicsLayer {
                    alpha = title.value.coerceIn(0f, 1f)
                    val scale = TITLE_START_SCALE + (1f - TITLE_START_SCALE) * title.value
                    scaleX = scale
                    scaleY = scale
                },
        )
        GinHandFan(handGroups(game, GinPlayer.PLAYER), modifier = Modifier.padding(top = CardGamesTheme.spacing.md))
        ResultCard(modifier = Modifier.graphicsLayer { alpha = figures.value }) {
            if (bonus > 0) SumRow(if (bonus > GIN_BONUS_PLAIN) "Big Gin bonus" else "Gin bonus", "+$bonus")
            SumRow("${state.opponentName}'s deadwood", "+${result.defenderDeadwood}")
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.panelLine))
            SumRow("This hand", "+${result.pointsScored}", emphasis = true)
        }
        if (matchOver != null) MatchBanner(game, matchOver, state.opponentName)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeTile(onHome)
        if (matchOver != null) {
            TablePrimaryButton(
                text = "Rematch ${state.opponentName}",
                onClick = { onIntent(GinIntent.NewMatchAgainst(state.opponent)) },
                modifier = Modifier.weight(1f),
            )
        } else {
            TablePrimaryButton(text = "Next hand", onClick = { onIntent(GinIntent.NextHand) }, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * The gin hand fanned wide — 56dp cards, 30dp apart with 12dp more between melds, tilted 4° per
 * step and curving down at the edges, each ringed in its meld's colour — over RUN / SET chips.
 */
@Composable
private fun GinHandFan(
    groups: HandGroups,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    val m = ginResultMetrics
    val flat = groups.melds.flatMapIndexed { index, meld -> meld.map { it to index } }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Layout(
            modifier = Modifier.fillMaxWidth().height(m.ginFanHeight),
            content = {
                flat.forEachIndexed { i, (card, group) ->
                    val tilt = (i - (flat.size - 1) / 2f) * GIN_FAN_TILT
                    PlayingCard(
                        card = card,
                        faceUp = true,
                        ring = colors.meld[group % colors.meld.size],
                        modifier = Modifier.graphicsLayer { rotationZ = tilt },
                    )
                }
            },
        ) { measurables, constraints ->
            val width = m.ginFanCard.roundToPx()
            val placeables = measurables.map { it.measure(Constraints.fixed(width, (width / CARD_ASPECT_RATIO).toInt())) }
            val extra = m.ginFanGroupGap.roundToPx()
            val breaks = flat.zipWithNext().count { (a, b) -> a.second != b.second }
            // Squeezed to fit a narrow screen; 30dp apart where there's room.
            val room = (constraints.maxWidth - width - extra * breaks) / (flat.size - 1).coerceAtLeast(1)
            val step = minOf(m.ginFanStep.roundToPx(), room)
            val xs =
                flat.indices.runningFold(0) { x, i ->
                    x + step +
                        if (i + 1 < flat.size && flat[i + 1].second != flat[i].second) extra else 0
                }
            val span = (xs.getOrNull(flat.lastIndex) ?: 0) + width
            val left = (constraints.maxWidth - span) / 2
            val middle = (flat.size - 1) / 2f
            layout(constraints.maxWidth, m.ginFanHeight.roundToPx()) {
                placeables.forEachIndexed { i, placeable ->
                    val d = i - middle
                    placeable.place(left + xs[i], (m.ginFanTop.toPx() + GIN_FAN_DROP.toPx() * d * d).toInt())
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm)) {
            groups.kinds.forEachIndexed { index, run ->
                val tint = colors.meld[index % colors.meld.size]
                TableChip(
                    if (run) "RUN" else "SET",
                    fill = tint.copy(alpha = MELD_CHIP_ALPHA),
                    ink = lerp(tint, colors.onTable, MELD_CHIP_INK),
                )
            }
        }
    }
}

private const val TITLE_START_SCALE = 0.6f
private const val GIN_BONUS_PLAIN = 25
private val SHOUT_BASE = 6.dp
private const val GIN_FAN_TILT = 4f
private val GIN_FAN_DROP = 1.1.dp
private const val MELD_CHIP_ALPHA = 0.3f
private const val MELD_CHIP_INK = 0.6f
