package com.cardgames.core.ui.table

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.ui.cards.LocalCardLanding
import com.cardgames.core.ui.cards.PlayingCard
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.MotionSpec
import com.cardgames.core.ui.motion.SpringMotion
import com.cardgames.core.ui.motion.scaledStiffness
import com.cardgames.core.ui.motion.spring
import com.cardgames.core.ui.motion.tween
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.sin

/**
 * One card on a game table, rendered by whichever call site currently holds it — the same
 * composable node wherever it moves (see [rememberMovableCards]), so it glides between piles,
 * hands and fans instead of popping. [glide] and [rotationZ] are that call site's — the
 * destination's — choice of how to arrive and how to tilt (0 outside a fan). Rendering a card
 * face down never leaks its identity: [PlayingCard] shows a generic back and description.
 */
typealias MovableCard = @Composable (faceUp: Boolean, glide: CardGlide, rotationZ: Float, modifier: Modifier) -> Unit

/** How a [MovableCard] travels to the call site rendering it. */
@Immutable
sealed interface CardGlide {
    /**
     * Along a motion token — duration and curve (see [CardGamesMotion]) — starting [delayMillis]
     * late, and turning over (when the card's face changes) [flipDelayMillis] in. Both delays are
     * normal-speed milliseconds, scaled like the token. With an [arc] the card rises that far at
     * mid-flight (`− arc · sin(πt)`) instead of sliding straight; with [land] it turns to its
     * resting angle on that spring rather than along the tween.
     */
    data class Tween(
        val spec: MotionSpec,
        val delayMillis: Int = 0,
        val flipDelayMillis: Int = 0,
        val arc: Dp = 0.dp,
        val land: SpringMotion? = null,
    ) : CardGlide

    /** A spring at [stiffness] (Compose's `Spring.Stiffness*`), stretched to the animation speed. */
    data class Spring(
        val stiffness: Float,
    ) : CardGlide

    companion object {
        /** Arrives in place with no glide — e.g. a card a drag already carried to its destination. */
        val Instant: CardGlide = Tween(MotionSpec(0))
    }
}

/**
 * One [MovableCard] per card, cached per deal ([dealKey] — a new deal is a fresh table, nothing
 * glides across it; replaying the same deal keeps the cache, so cards fly back to their start).
 * Each card fades in over [CardGamesMotion.CardAppear] the first time it appears, glides on
 * every move per its destination's [CardGlide], rotates with the same timing so position and tilt
 * never drift apart, and turns over along [flip]. [fadeIn] false skips the first-appearance fade —
 * for re-keying a table to snap every card into place.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun rememberMovableCards(
    dealKey: Any,
    lookaheadScope: LookaheadScope,
    flip: MotionSpec = CardGamesMotion.Flip,
    fadeIn: Boolean = true,
): (Card) -> MovableCard {
    val cache = remember(dealKey, lookaheadScope) { mutableMapOf<Card, MovableCard>() }
    // The cards on the table when the screen first draws are simply there — coming back to a game
    // shouldn't fade the whole table in again; only cards that turn up later (a new deal) fade in.
    val opening = remember { booleanArrayOf(true) }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        opening[0] = false
    }
    return { card ->
        cache.getOrPut(card) {
            val fades = fadeIn && !opening[0]
            movableContentOf { faceUp, glide, targetRotation, modifier ->
                val reduceMotion = LocalReduceMotion.current
                val appear = remember { Animatable(if (fades) 0f else 1f) }
                val appearSpec = CardGamesMotion.CardAppear.tween<Float>()
                LaunchedEffect(Unit) { appear.animateTo(1f, appearSpec) }
                val boundsSpec = glide.spec<Rect>(Rect.VisibilityThreshold)
                val boundsTransform = remember(boundsSpec) { BoundsTransform { _, _ -> boundsSpec } }
                // Initialized to the current target so a card's first appearance never animates
                // in from some earlier default angle.
                val rotation = remember { Animatable(targetRotation) }
                val landing = (glide as? CardGlide.Tween)?.land?.takeUnless { reduceMotion }
                val rotationSpec = landing?.spring<Float>() ?: glide.spec<Float>(null)
                LaunchedEffect(targetRotation, rotationSpec) { rotation.animateTo(targetRotation, rotationSpec) }
                // An arcing flight: progress along the same tween, lifting the card by sin(πt).
                val arc = (glide as? CardGlide.Tween)?.arc ?: 0.dp
                val arcProgress = remember { Animatable(0f) }
                val arcSpec = glide.spec<Float>(null)
                LaunchedEffect(glide) {
                    if (arc > 0.dp && !reduceMotion) {
                        arcProgress.snapTo(0f)
                        arcProgress.animateTo(1f, arcSpec)
                    }
                }
                val drop = rememberLanding(card)
                val pivot = LocalCardPivot.current
                PlayingCard(
                    card = card,
                    faceUp = faceUp,
                    flip = flip,
                    flipDelayMillis = (glide as? CardGlide.Tween)?.flipDelayMillis ?: 0,
                    modifier =
                        Modifier
                            .animateBounds(lookaheadScope, boundsTransform = boundsTransform)
                            .onGloballyPositioned { drop.root = it.positionInRoot() }
                            .graphicsLayer {
                                alpha = if (drop.waiting) 0f else appear.value
                                transformOrigin = pivot
                                rotationZ = rotation.value
                                translationX = drop.offset.value.x
                                val lift = if (arcProgress.value in 0f..<1f) arc.toPx() * sin(PI * arcProgress.value).toFloat() else 0f
                                translationY = drop.offset.value.y - lift
                            }.then(modifier),
                )
            }
        }
    }
}

/**
 * Where a card turns about when the call site tilts it — the centre by default. A held hand turns
 * its cards about a point below them (`TransformOrigin(0.5f, 1.2f)`), so a tilted row reads as a
 * fan in the hand rather than cards laid side by side.
 */
val LocalCardPivot = compositionLocalOf { TransformOrigin.Center }

/** One card's part in a drop landing (see [CardLanding]): where it's laid out, and its spring back from the release point. */
private class LandingState {
    var root by mutableStateOf<Offset?>(null)
    var waiting by mutableStateOf(false)
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
}

/**
 * Watches the surrounding drop landing for [card]: when a drop carries it, the card hides until
 * it's laid out at its destination, then draws itself back at the release point and springs home.
 * The lead card measures the offset; the rest of a run reuse it, so the run keeps its shape.
 */
@Composable
private fun rememberLanding(card: Card): LandingState {
    val state = remember { LandingState() }
    val landing = LocalCardLanding.current ?: return state
    val reduceMotion = LocalReduceMotion.current
    val settle =
        spring(dampingRatio = LAND_DAMPING, stiffness = scaledStiffness(LAND_STIFFNESS), visibilityThreshold = Offset.VisibilityThreshold)
    LaunchedEffect(landing.id) {
        if (card !in landing.cards || reduceMotion) return@LaunchedEffect
        state.waiting = true
        if (card == landing.lead) {
            val before = state.root
            val placed = withTimeoutOrNull(LANDING_TIMEOUT_MILLIS) { snapshotFlow { state.root }.first { it != null && it != before } }
            landing.offset = (placed ?: state.root)?.let { landing.releasedAt - it } ?: Offset.Zero
        }
        val offset = withTimeoutOrNull(LANDING_TIMEOUT_MILLIS) { snapshotFlow { landing.offset }.filterNotNull().first() } ?: Offset.Zero
        state.offset.snapTo(offset)
        state.waiting = false
        state.offset.animateTo(Offset.Zero, settle)
    }
    return state
}

/** This glide as an animation spec at the player's animation speed. */
@Composable
private fun <T> CardGlide.spec(visibilityThreshold: T?): FiniteAnimationSpec<T> =
    when {
        // Reduce motion: every move is the same short, staggerless slide.
        LocalReduceMotion.current -> CardGamesMotion.ReducedFade.tween()
        else -> moveSpec(visibilityThreshold)
    }

@Composable
private fun <T> CardGlide.moveSpec(visibilityThreshold: T?): FiniteAnimationSpec<T> =
    when (this) {
        // Both spec types compare by value, so callers can key remember/LaunchedEffect on the result.
        is CardGlide.Tween -> spec.tween(delay = MotionSpec(delayMillis).takeIf { delayMillis > 0 })
        is CardGlide.Spring -> spring(stiffness = scaledStiffness(stiffness), visibilityThreshold = visibilityThreshold)
    }

/**
 * Cards that just render where they're placed — no glide, no fade-in — for a non-interactive copy
 * of a table (see [rememberTableHandoff]).
 */
@Composable
fun rememberStaticCards(flip: MotionSpec = CardGamesMotion.Flip): (Card) -> MovableCard =
    remember(flip) {
        { card ->
            { faceUp, _, rotationZ, modifier ->
                PlayingCard(
                    card = card,
                    faceUp = faceUp,
                    flip = flip,
                    modifier = Modifier.graphicsLayer { this.rotationZ = rotationZ }.then(modifier),
                )
            }
        }
    }

/** A dropped card's spring home: soft, barely overshooting — it should feel set down, not thrown. */
private const val LAND_DAMPING = 0.78f
private const val LAND_STIFFNESS = 260f

/** How long a landing card waits to be laid out at its destination before giving up on the effect. */
private const val LANDING_TIMEOUT_MILLIS = 400L
