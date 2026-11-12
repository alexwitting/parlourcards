package com.cardgames.feature.ginrummy.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.ginrummy.GinMatchHighlights
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.HandOutcome
import com.cardgames.core.domain.ginrummy.HandResult
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.ui.chrome.Avatar
import com.cardgames.core.ui.chrome.ChromeShapes
import com.cardgames.core.ui.chrome.Confetti
import com.cardgames.core.ui.chrome.TableBackground
import com.cardgames.core.ui.chrome.TablePrimaryButton
import com.cardgames.core.ui.chrome.TableSecondaryButton
import com.cardgames.core.ui.chrome.rememberLatestNonNull
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.scaledMillis
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.text.initials
import com.cardgames.core.ui.text.opponentName
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.ginrummy.GinIntent
import com.cardgames.feature.ginrummy.GinUiState
import com.cardgames.feature.ginrummy.motion.GinMotionTokens
import com.cardgames.feature.ginrummy.opponentName
import kotlinx.coroutines.delay

/**
 * The end of a hand or match, full screen on the felt once the last card has landed: the hand
 * result (both hands, the layoffs, the sum, the race), the Gin celebration, or the match result.
 * A blocked hand isn't here — it's said in the bottom panel. A gin waits a beat
 * ([GinMotionTokens.GIN_REVEAL_PAUSE]) first: with no layoffs to pause on, the loser's hand would
 * otherwise turn face up only under the fading-in result.
 */
@Composable
internal fun GinResultOverlay(
    state: GinUiState,
    onIntent: (GinIntent) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settled = state.pending == null
    val ended = state.game.takeIf { settled && it.endsWithResult() }
    // The gin hand whose beat on the table has run; any other result shows as soon as it lands.
    val pauseMillis = scaledMillis(GinMotionTokens.GIN_REVEAL_PAUSE)
    var pausedGin by remember { mutableStateOf<GinState?>(null) }
    LaunchedEffect(ended) {
        if (ended?.endedInGin() == true) {
            delay(pauseMillis)
            pausedGin = ended
        }
    }
    val showing = ended?.takeIf { !it.endedInGin() || it == pausedGin }
    // Keeps the finished hand on screen while the overlay fades out over the next deal.
    val shown = rememberLatestNonNull(showing)
    AnimatedVisibility(
        visible = showing != null,
        enter = fadeIn(CardGamesMotion.Base.tween()),
        exit = fadeOut(CardGamesMotion.Base.tween()),
        modifier = modifier,
    ) {
        val game = shown ?: return@AnimatedVisibility
        val phase = game.phase
        val result = (phase as? GinPhase.HandOver)?.result ?: (phase as? GinPhase.MatchOver)?.lastHand
        val playerGin = result?.outcome == HandOutcome.GIN && result.winner == GinPlayer.PLAYER
        ResultPage(backdropFor(phase, result, playerGin)) {
            when {
                result != null && playerGin -> GinCelebration(game, result, state, onIntent, onHome)
                phase is GinPhase.MatchOver -> MatchResult(game, phase, state, onIntent, onHome)
                result != null -> HandResultPage(game, result, state.opponent, onIntent, onHome)
            }
        }
    }
}

/** The hand (or the match's last hand) went out on a gin. */
private fun GinState.endedInGin(): Boolean {
    val result = (phase as? GinPhase.HandOver)?.result ?: (phase as? GinPhase.MatchOver)?.lastHand
    return result?.outcome == HandOutcome.GIN
}

/** Only a hand someone won gets a result screen; a blocked hand stays in the panel. */
private fun GinState.endsWithResult(): Boolean =
    when (val phase = phase) {
        is GinPhase.HandOver -> phase.result.outcome != HandOutcome.BLOCKED
        is GinPhase.MatchOver -> true
        else -> false
    }

/** What's behind a result: confetti for the player's win, rays too for gin, burgundy for the opponent's (any hand they win). */
private enum class Backdrop { FELT, CONFETTI, GIN, OPPONENT }

private fun backdropFor(
    phase: GinPhase,
    result: HandResult?,
    playerGin: Boolean,
): Backdrop =
    when {
        playerGin -> Backdrop.GIN
        phase is GinPhase.MatchOver -> if (phase.winner == GinPlayer.PLAYER) Backdrop.CONFETTI else Backdrop.FELT
        // By who won the hand, not who knocked: an undercut belongs to the defender.
        result?.winner == GinPlayer.PLAYER -> Backdrop.CONFETTI
        result?.winner == GinPlayer.OPPONENT -> Backdrop.OPPONENT
        else -> Backdrop.FELT
    }

/** The felt (with its [backdrop]), edge to edge, swallowing every touch that isn't one of its own buttons. */
@Composable
private fun ResultPage(
    backdrop: Backdrop,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        TableBackground(modifier = Modifier.matchParentSize())
        when (backdrop) {
            Backdrop.FELT -> Unit
            Backdrop.CONFETTI -> Confetti(modifier = Modifier.matchParentSize())
            Backdrop.GIN -> {
                FeltRays(modifier = Modifier.matchParentSize())
                Confetti(modifier = Modifier.matchParentSize(), pieces = GIN_CONFETTI, sideDepth = GIN_CONFETTI_DEPTH)
            }
            Backdrop.OPPONENT -> OpponentTint(modifier = Modifier.matchParentSize())
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        start = ginResultMetrics.pagePadding,
                        end = ginResultMetrics.pagePadding,
                        top = CardGamesTheme.spacing.sm,
                        bottom = CardGamesTheme.spacing.md,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Tight enough that every page fits a phone (360 × 780dp, S24) without scrolling.
            verticalArrangement = Arrangement.spacedBy(ginResultMetrics.blockGap),
            content = content,
        )
    }
}

/** A won hand: who knocked, both hands on cream cards with their deadwood and layoffs, the sum, and the race. */
@Composable
private fun ColumnScope.HandResultPage(
    game: GinState,
    result: HandResult,
    opponent: GinDifficulty,
    onIntent: (GinIntent) -> Unit,
    onHome: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    val opponentName = opponent.opponentName
    val knocker = knockerOf(result) ?: GinPlayer.PLAYER
    val yours = result.winner == GinPlayer.PLAYER
    ResultBody(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ginResultMetrics.blockGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm)) {
            if (knocker ==
                GinPlayer.OPPONENT
            ) {
                Avatar(opponent.initials, fill = colors.panel, ink = colors.cardBack, size = ginResultMetrics.smallAvatar)
            }
            OverlineChip("Hand ${game.handNumber} · ${handOverline(result, opponentName)}")
        }
        ResultTitle(handTitle(result, opponentName))
        PointsPill("+${result.pointsScored} points", yours = yours)
        val layoffs = layoffLine(game, knocker, opponentName)
        HandSummary(
            "Your hand",
            "You",
            game,
            GinPlayer.PLAYER,
            deadwoodOf(result, GinPlayer.PLAYER, knocker),
            won = yours,
            note = layoffs.takeIf { knocker == GinPlayer.PLAYER },
        )
        HandSummary(
            "$opponentName's hand",
            opponent.initials,
            game,
            GinPlayer.OPPONENT,
            deadwoodOf(result, GinPlayer.OPPONENT, knocker),
            won = !yours,
            note = layoffs.takeIf { knocker == GinPlayer.OPPONENT },
        )
        SumChips(sumTerms(result))
        RaceCard(game, opponentName, gained = result.winner?.let { it to result.pointsScored })
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeTile(onHome)
        TablePrimaryButton(text = "Next hand", onClick = { onIntent(GinIntent.NextHand) }, modifier = Modifier.weight(1f))
    }
}

/** "And that's the match!" under a gin that ends it, with the final race. */
@Composable
internal fun MatchBanner(
    game: GinState,
    matchOver: GinPhase.MatchOver,
    opponentName: String,
) {
    val colors = CardGamesTheme.colors
    val race = matchOver.raceScores ?: game.matchScores
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.chipOnTableSoft, ChromeShapes.Button)
                .padding(horizontal = 14.dp, vertical = CardGamesTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        Box(modifier = Modifier.size(BANNER_ICON).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(ParlourIcons.Trophy, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("And that's the match!", color = colors.onTable, style = CardGamesTheme.type.subhead)
            Text(
                "You ${race.getValue(GinPlayer.PLAYER)} – ${race.getValue(GinPlayer.OPPONENT)} $opponentName · " +
                    "boxes ${game.boxes.getValue(GinPlayer.PLAYER)} – ${game.boxes.getValue(GinPlayer.OPPONENT)}",
                color = colors.onTableMuted,
                style = CardGamesTheme.type.toolLabel,
            )
        }
    }
}

/** The match decided: who took it, the final race against the goal, the player's highlights, and what next. */
@Composable
private fun ColumnScope.MatchResult(
    game: GinState,
    phase: GinPhase.MatchOver,
    state: GinUiState,
    onIntent: (GinIntent) -> Unit,
    onHome: () -> Unit,
) {
    val colors = CardGamesTheme.colors
    val won = phase.winner == GinPlayer.PLAYER
    val race = phase.raceScores ?: game.matchScores
    val target = game.rules.target
    val short = target - race.getValue(GinPlayer.PLAYER)
    ResultBody(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ginResultMetrics.blockGap),
    ) {
        val halo = colors.onTable.copy(alpha = HERO_HALO_ALPHA)
        Avatar(
            if (won) "You" else state.opponent.initials,
            fill = if (won) colors.accent else colors.opponent,
            ink = if (won) colors.onAccent else colors.onTable,
            size = ginResultMetrics.heroAvatar,
            style = CardGamesTheme.type.resultTitle,
            modifier =
                Modifier
                    .padding(top = HERO_HALO)
                    .drawBehind { drawCircle(halo, radius = size.minDimension / 2 + HERO_HALO.toPx()) },
        )
        OverlineChip("Match over · ${state.highlights.handsPlayed} hands")
        ResultTitle(if (won) "You take the match" else "${state.opponentName} takes this one")
        Text(
            matchLine(won, target, short, phase.lastHand, state.opponentName),
            color = colors.onTable,
            style = CardGamesTheme.type.rowLabel,
            textAlign = TextAlign.Center,
        )
        FinalScoreCard(race.getValue(GinPlayer.PLAYER), race.getValue(GinPlayer.OPPONENT), state.opponentName, game)
        Text("Your highlights", color = colors.onTable, style = CardGamesTheme.type.subhead, modifier = Modifier.fillMaxWidth())
        Highlights(state.highlights)
        state.overallRecord?.let { OverallRecordRow(it, state.opponentName) }
    }
    TablePrimaryButton(
        text = "Rematch ${state.opponentName}",
        onClick = { onIntent(GinIntent.NewMatchAgainst(state.opponent)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        easierThan(state.opponent)?.takeIf { !won }?.let { easier ->
            TableSecondaryButton(
                text = "Try an easier rival",
                onClick = { onIntent(GinIntent.NewMatchAgainst(easier)) },
                modifier = Modifier.weight(1f),
            )
        }
        TableSecondaryButton(text = "Home", onClick = onHome, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Highlights(highlights: GinMatchHighlights) {
    Row(horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm)) {
        HighlightTile("${highlights.gins}", if (highlights.gins == 1) "Gin" else "Gins", Modifier.weight(1f))
        HighlightTile("+${highlights.bestHand}", "Best hand", Modifier.weight(1f))
        HighlightTile("${highlights.undercuts}", if (highlights.undercuts == 1) "Undercut" else "Undercuts", Modifier.weight(1f))
    }
}

/** The next opponent down, or null for the easiest. */
private fun easierThan(difficulty: GinDifficulty): GinDifficulty? = GinDifficulty.entries.getOrNull(difficulty.ordinal - 1)

private val HERO_HALO = 6.dp
private const val HERO_HALO_ALPHA = 0.25f
private val BANNER_ICON = 40.dp
private const val GIN_CONFETTI = 240
private val GIN_CONFETTI_DEPTH = 600.dp
