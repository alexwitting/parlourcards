package com.cardgames.core.ui.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalDensity
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.scaledMillis
import com.cardgames.core.ui.motion.table
import com.cardgames.core.ui.motion.tween

/**
 * Shared drag-and-drop for card games: touch-drag a card (or run), a ghost follows the finger,
 * and drop targets report hover while a legal payload is over them. Rule-agnostic — legality is
 * asked of the feature via each target's `canAccept` lambda.
 *
 * Wiring: create one state with [rememberDragDropState], wrap the play area in
 * [DragDropContainer], mark draggables with [dragSource] and landing zones with [dropTarget].
 * Read [DragDropState.payload] to hide/dim the picked-up cards (keep them composed — removing
 * a source node mid-gesture kills the drag) and [DragDropState.hoveredTargetKey] to highlight
 * the hovered target.
 *
 * A release that no target accepts flies the ghost back to where it was picked up (the payload
 * stays set until it's back, so the source stays hidden); released over a target that refused
 * it, the ghost also shakes there — an illegal drop.
 */
@Stable
class DragDropState<P : Any> internal constructor() {
    /** The payload being dragged, or null when idle. */
    var payload: P? by mutableStateOf(null)
        private set

    /** Key of the drop target currently under the finger and accepting the payload. */
    var hoveredTargetKey: Any? by mutableStateOf(null)
        private set

    /** True while a refused drop is flying back; the payload is still set but nothing can be dropped. */
    var isReturning: Boolean by mutableStateOf(false)
        private set

    internal var containerCoordinates: LayoutCoordinates? = null

    /** Where the drag began (ghost top-left), and whether the release was over a refusing target. */
    internal var returnTo: Offset = Offset.Zero
    internal var returnShakes: Boolean = false

    /** Ghost top-left in container coordinates; read inside graphicsLayer, so per-frame moves skip recomposition. */
    internal var ghostTopLeft: Offset by mutableStateOf(Offset.Zero)
    internal var ghostWidthPx: Int by mutableIntStateOf(0)

    private var pointerPosition = Offset.Zero
    private val targets = LinkedHashMap<Any, DropTargetEntry<P>>()

    /** The cards a payload carries — so an accepted drop can land them from where they were let go. */
    internal var cardsOf: (P) -> List<Card> = { emptyList() }

    /** The cards an accepted drop is landing, read by each card (see [LocalCardLanding]). */
    internal val landing = CardLanding()

    internal fun registerTarget(
        key: Any,
        entry: DropTargetEntry<P>,
    ) {
        targets[key] = entry
    }

    internal fun unregisterTarget(key: Any) {
        targets.remove(key)
        if (hoveredTargetKey == key) hoveredTargetKey = null
    }

    internal fun startDrag(
        payload: P,
        sourceCoordinates: LayoutCoordinates,
        pointerInSource: Offset,
    ) {
        val container = containerCoordinates ?: return
        if (isReturning) return
        this.payload = payload
        ghostWidthPx = sourceCoordinates.size.width
        ghostTopLeft = container.localPositionOf(sourceCoordinates, Offset.Zero)
        returnTo = ghostTopLeft
        pointerPosition = container.localPositionOf(sourceCoordinates, pointerInSource)
        updateHover()
    }

    internal fun drag(delta: Offset) {
        if (payload == null || isReturning) return
        ghostTopLeft += delta
        pointerPosition += delta
        updateHover()
    }

    internal fun drop() {
        val dropped = payload ?: return
        if (isReturning) return
        val target = hoveredTargetKey?.let(targets::get)
        if (target != null) {
            containerCoordinates?.let { landing.start(cardsOf(dropped), it.localToRoot(ghostTopLeft)) }
            reset()
            target.onDrop(dropped)
        } else {
            flyBack(shake = targetUnderPointer() != null)
        }
    }

    internal fun cancel() {
        if (payload != null && !isReturning) flyBack(shake = false)
    }

    private fun flyBack(shake: Boolean) {
        hoveredTargetKey = null
        returnShakes = shake
        isReturning = true
    }

    /** The fly-back (and any shake) has finished. */
    internal fun returned() {
        isReturning = false
        reset()
    }

    private fun reset() {
        payload = null
        hoveredTargetKey = null
    }

    private fun targetUnderPointer(): Any? {
        val container = containerCoordinates ?: return null
        return targets.entries
            .firstOrNull { (_, entry) ->
                val coordinates = entry.coordinates?.takeIf { it.isAttached }
                coordinates != null && container.localBoundingBoxOf(coordinates, clipBounds = false).contains(pointerPosition)
            }?.key
    }

    private fun updateHover() {
        val dragged = payload
        val container = containerCoordinates
        hoveredTargetKey =
            if (dragged == null || container == null) {
                null
            } else {
                targets.entries
                    .firstOrNull { (_, entry) ->
                        val coordinates = entry.coordinates?.takeIf { it.isAttached }
                        coordinates != null &&
                            container
                                .localBoundingBoxOf(coordinates, clipBounds = false)
                                .contains(pointerPosition) &&
                            entry.canAccept(dragged)
                    }?.key
            }
    }
}

/**
 * The cards an accepted drop just sent to their new place, and where the drag let them go (root
 * pixels, the ghost's top-left). Each card, once laid out at its destination, draws itself back at
 * that release point and springs the rest of the way — so a drop lands from the finger rather than
 * popping into place. The [lead] card (a run's top card) measures the offset for all of them.
 */
@Stable
class CardLanding internal constructor() {
    internal var id by mutableIntStateOf(0)
        private set
    internal var cards: Set<Card> = emptySet()
        private set
    internal var lead: Card? = null
        private set
    internal var releasedAt: Offset = Offset.Zero
        private set

    /** Release point minus the lead card's new top-left, once the lead has been laid out there. */
    internal var offset: Offset? by mutableStateOf(null)

    internal fun start(
        cards: List<Card>,
        releasedAt: Offset,
    ) {
        this.cards = cards.toSet()
        lead = cards.firstOrNull()
        this.releasedAt = releasedAt
        offset = null
        id++
    }
}

/** The drop landing in progress for the cards inside a [DragDropContainer]; null outside one. */
val LocalCardLanding = compositionLocalOf<CardLanding?> { null }

internal class DropTargetEntry<P : Any> {
    var coordinates: LayoutCoordinates? = null
    var canAccept: (P) -> Boolean = { false }
    var onDrop: (P) -> Unit = {}
}

@Composable
fun <P : Any> rememberDragDropState(): DragDropState<P> = remember { DragDropState() }

/**
 * The area drags happen inside. Renders [content], then — while a drag is live — [ghost] for
 * the current payload, following the finger. The ghost gets the drag source's width; height
 * wraps content (so a run ghost can be taller than one card). [onLift] runs as a drag starts and
 * [onReject] as an illegal drop starts shaking — the feature's haptics. [landingCards] names the
 * cards a payload carries, so an accepted drop lands them from the release point (see
 * [CardLanding]).
 */
@Composable
fun <P : Any> DragDropContainer(
    state: DragDropState<P>,
    modifier: Modifier = Modifier,
    onLift: (P) -> Unit = {},
    onReject: () -> Unit = {},
    landingCards: (P) -> List<Card> = { emptyList() },
    ghost: @Composable (P) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    state.cardsOf = landingCards
    val payload = state.payload
    LaunchedEffect(payload) { if (payload != null) onLift(payload) }
    val shake = remember { Animatable(0f) }
    // Reduce motion: the ghost slides back on the short reduced fade and doesn't shake.
    val still = LocalReduceMotion.current
    val flight = (if (still) CardGamesMotion.ReducedFade else CardGamesMotion.Flight).tween<Offset>()
    val shakeMillis = CardGamesMotion.Shake.scaledMillis()
    val shakePx = with(LocalDensity.current) { CardGamesMotion.table.shakeDistance.toPx() }
    LaunchedEffect(state.isReturning) {
        if (!state.isReturning) return@LaunchedEffect
        val from = Animatable(state.ghostTopLeft, Offset.VectorConverter)
        from.animateTo(state.returnTo, flight) { state.ghostTopLeft = value }
        if (state.returnShakes) {
            onReject()
            if (!still) {
                shake.animateTo(
                    0f,
                    keyframes {
                        durationMillis = shakeMillis
                        val beat = shakeMillis / (CardGamesMotion.SHAKE_CYCLES * 2 + 1)
                        var at = beat
                        repeat(CardGamesMotion.SHAKE_CYCLES) {
                            shakePx at at
                            at += beat
                            -shakePx at at
                            at += beat
                        }
                    },
                )
            }
        }
        state.returned()
    }
    Box(modifier = modifier.onGloballyPositioned { state.containerCoordinates = it }) {
        CompositionLocalProvider(LocalCardLanding provides state.landing) { content() }
        if (payload != null) {
            val ghostWidth = with(LocalDensity.current) { state.ghostWidthPx.toDp() }
            Box(
                modifier =
                    Modifier
                        .graphicsLayer {
                            translationX = state.ghostTopLeft.x + shake.value
                            translationY = state.ghostTopLeft.y
                        }.width(ghostWidth),
            ) { ghost(payload) }
        }
    }
}

/**
 * Makes this node draggable, carrying [payload]. A tap still reaches any `clickable` on the
 * same node — the drag only claims the pointer after touch slop. [payload] is re-read at drag
 * start, so recompositions with new payloads don't restart the gesture detector.
 */
fun <P : Any> Modifier.dragSource(
    state: DragDropState<P>,
    payload: P,
    enabled: Boolean = true,
): Modifier =
    composed {
        val currentPayload by rememberUpdatedState(payload)
        val coordinates = remember { Ref<LayoutCoordinates>() }
        onGloballyPositioned { coordinates.value = it }
            .pointerInput(state, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { pointerInSource ->
                        coordinates.value?.let { state.startDrag(currentPayload, it, pointerInSource) }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        state.drag(delta)
                    },
                    onDragEnd = { state.drop() },
                    onDragCancel = { state.cancel() },
                )
            }
    }

/**
 * Makes this node a landing zone identified by [key] (also the value surfaced through
 * [DragDropState.hoveredTargetKey]). [canAccept] is the feature's legality check; [onDrop]
 * fires only for an accepted payload released while hovering.
 */
fun <P : Any> Modifier.dropTarget(
    state: DragDropState<P>,
    key: Any,
    canAccept: (P) -> Boolean,
    onDrop: (P) -> Unit,
): Modifier =
    composed {
        val entry = remember(state, key) { DropTargetEntry<P>() }
        SideEffect {
            entry.canAccept = canAccept
            entry.onDrop = onDrop
        }
        DisposableEffect(state, key) {
            state.registerTarget(key, entry)
            onDispose { state.unregisterTarget(key) }
        }
        onGloballyPositioned { entry.coordinates = it }
    }
