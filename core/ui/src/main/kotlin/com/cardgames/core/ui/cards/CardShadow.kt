package com.cardgames.core.ui.cards

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A card's soft shadow, [elevation] deep, on the card's rounded corners ([cornerPercent] of its
 * width). Drawn from a blurred bitmap made once per card size and depth and then reused, rather
 * than an elevation shadow: those are worked out by the render thread for every card on every
 * frame, and a table of 52 spent about 3ms of each 8ms frame on them. Draw it before the card's
 * face, unclipped, so it falls on the cards beneath like the elevation shadow did.
 */
internal fun Modifier.cardShadow(
    elevation: Dp,
    cornerPercent: Int,
): Modifier =
    drawBehind {
        val depth = elevation.toPx()
        if (depth <= 0f || size.minDimension <= 0f) return@drawBehind
        // Made for the size rounded to SIZE_STEP and stretched to the exact one: a card in flight
        // changes size every frame, and an image per exact size meant a blur and an upload per card
        // per frame (809 in one Solitaire new deal). A stretch of a few percent doesn't show on a blur.
        val width = bucketed(size.width)
        val height = bucketed(size.height)
        val shadow = CardShadows.get(width, height, depth, cornerPercent)
        val scaleX = size.width / width
        val scaleY = size.height / height
        drawImage(
            shadow.image,
            dstOffset = IntOffset((-shadow.pad * scaleX).roundToInt(), (-shadow.pad * scaleY + depth * SHADOW_DROP).roundToInt()),
            dstSize = IntSize(((width + 2 * shadow.pad) * scaleX).roundToInt(), ((height + 2 * shadow.pad) * scaleY).roundToInt()),
        )
    }

/** [pixels] to the nearest [SIZE_STEP], at least one step. */
private fun bucketed(pixels: Float): Int = maxOf(SIZE_STEP, (pixels / SIZE_STEP).roundToInt() * SIZE_STEP)

/** One shadow image and how far it spreads past the card on every side. */
private class CardShadowImage(
    val image: ImageBitmap,
    val pad: Float,
)

/** The shadows made so far, by (rounded) card size, depth and corner — a table uses a few dozen. */
private object CardShadows {
    private val cache =
        object : LinkedHashMap<Long, CardShadowImage>(CACHE_SIZE, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CardShadowImage>?) = size > CACHE_SIZE
        }

    @Synchronized
    fun get(
        width: Int,
        height: Int,
        depth: Float,
        cornerPercent: Int,
    ): CardShadowImage {
        val key =
            (width.toLong() shl BITS_W) or (height.toLong() shl BITS_H) or (depth.roundToInt().toLong() shl BITS_D) or
                cornerPercent.toLong()
        return cache.getOrPut(key) { make(width, height, depth, cornerPercent) }
    }

    private fun make(
        width: Int,
        height: Int,
        depth: Float,
        cornerPercent: Int,
    ): CardShadowImage {
        val blur = depth * SHADOW_BLUR
        val pad = ceil(blur * 2).toInt()
        val bitmap = Bitmap.createBitmap(width + pad * 2, height + pad * 2, Bitmap.Config.ARGB_8888)
        val radius = width * cornerPercent / PERCENT
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb((SHADOW_ALPHA * MAX_ALPHA).roundToInt(), 0, 0, 0)
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
        android.graphics
            .Canvas(bitmap)
            .drawRoundRect(pad.toFloat(), pad.toFloat(), (pad + width).toFloat(), (pad + height).toFloat(), radius, radius, paint)
        return CardShadowImage(bitmap.asImageBitmap(), pad.toFloat())
    }
}

/** The blur's radius, and how far the shadow sits below the card, as fractions of its depth. */
private const val SHADOW_BLUR = 1.2f
private const val SHADOW_DROP = 0.25f
private const val SHADOW_ALPHA = 0.3f
private const val MAX_ALPHA = 255
private const val PERCENT = 100f

/** Card sizes are cached to the nearest this many pixels; see [cardShadow]. */
private const val SIZE_STEP = 8
private const val CACHE_SIZE = 64
private const val LOAD_FACTOR = 0.75f
private const val BITS_W = 44
private const val BITS_H = 24
private const val BITS_D = 8
