package com.cardgames.core.ui.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.theme.ThemePack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * [CardImages] over a validated pack's asset files. Decodes each image at the widest it's ever
 * drawn on this screen — [cardWidthPx] for full faces and backs, [smallFaceWidthPx] for small
 * faces — and caches every bitmap: subsampled first (cheap), then scaled to that exact width,
 * never all 52 faces at their full 750×1050. On a 3× phone that's about 9 MB of faces and 5 MB of
 * small faces in RGB_565. Call [preloadAll] off the main thread before first render; cache misses
 * decode lazily as a fallback.
 */
class AssetCardImages(
    context: Context,
    private val pack: ThemePack,
    private val cardWidthPx: Int = widthPx(context, LARGEST_CARD_DP),
    private val smallFaceWidthPx: Int = widthPx(context, SMALL_CARD_MAX_WIDTH.value),
) : CardImages {
    private val assets = context.assets
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    override fun face(
        card: Card,
        small: Boolean,
    ): ImageBitmap {
        val smallPath = if (small) pack.smallFacePaths?.get(card) else null
        return smallPath?.let { decode(it, smallFaceWidthPx) } ?: decode(pack.facePaths.getValue(card), cardWidthPx)
    }

    override fun back(): ImageBitmap = decode(pack.cardBackPath, cardWidthPx)

    override fun tableBackground(): ImageBitmap? = pack.tableBackgroundPath?.let { decode(it, TABLE_TARGET_WIDTH_PX) }

    suspend fun preloadAll() =
        withContext(Dispatchers.IO) {
            val jobs =
                pack.facePaths.values.map { path -> async { decode(path, cardWidthPx) } } +
                    pack.smallFacePaths
                        ?.values
                        .orEmpty()
                        .map { path -> async { decode(path, smallFaceWidthPx) } } +
                    async { back() } +
                    async { tableBackground() }
            jobs.awaitAll()
        }

    private fun decode(
        path: String,
        targetWidthPx: Int,
    ): ImageBitmap =
        cache.getOrPut(path) {
            val bounds =
                BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { options ->
                    assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
                }
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, targetWidthPx)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            val bitmap =
                assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
                    ?: error("Undecodable theme asset $path (pack ${pack.id} passed validation?)")
            bitmap.scaledDownTo(targetWidthPx).asImageBitmap()
        }

    /**
     * Subsampling only halves, so it can land up to twice the target (750px art for a 252px card
     * decodes at 375px); a filtered scale takes it the rest of the way. Never scales up.
     */
    private fun Bitmap.scaledDownTo(targetWidthPx: Int): Bitmap {
        if (width <= targetWidthPx) return this
        val targetHeight = (height.toFloat() * targetWidthPx / width).roundToInt()
        return Bitmap.createScaledBitmap(this, targetWidthPx, targetHeight, true).also { if (it !== this) recycle() }
    }

    /** Largest power-of-two subsampling that still lands at or above the target width. */
    private fun sampleSize(
        sourceWidth: Int,
        targetWidthPx: Int,
    ): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidthPx) sample *= 2
        return sample
    }

    private companion object {
        /** The widest card either game draws: Gin's stock and discard piles (`GinMetrics.Regular`). */
        const val LARGEST_CARD_DP = 84f
        const val TABLE_TARGET_WIDTH_PX = 1080

        fun widthPx(
            context: Context,
            dp: Float,
        ): Int = (dp * context.resources.displayMetrics.density).roundToInt()
    }
}
