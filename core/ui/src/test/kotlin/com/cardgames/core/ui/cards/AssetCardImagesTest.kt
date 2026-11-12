package com.cardgames.core.ui.cards

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.testing.card
import com.cardgames.core.testing.testThemePack
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Card art decodes at the width it's drawn at — not at the next power-of-two step above it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AssetCardImagesTest {
    private val ace = card("A♠")

    /** A pack whose every asset is a blank [width] × [height] PNG. */
    private fun images(
        width: Int = 750,
        height: Int = 1050,
        withSmallFaces: Boolean = true,
    ): AssetCardImages {
        val png =
            ByteArrayOutputStream()
                .also { out ->
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
                }.toByteArray()
        val assetManager = mockk<AssetManager> { every { open(any()) } answers { ByteArrayInputStream(png) } }
        val context = mockk<Context> { every { assets } returns assetManager }
        val pack =
            testThemePack("test").copy(
                facePaths = mapOf(ace to "faces/spades_01.webp"),
                smallFacePaths = if (withSmallFaces) mapOf(ace to "faces_small/spades_01.webp") else null,
            )
        return AssetCardImages(context, pack, cardWidthPx = 252, smallFaceWidthPx = 180)
    }

    @Test
    fun facesAndBacksDecodeAtTheLargestCardWidthKeepingTheirShape() {
        val images = images()

        val face = images.face(ace, small = false)
        assertEquals(252, face.width)
        assertEquals(353, face.height)
        assertEquals(252, images.back().width)
    }

    @Test
    fun smallFacesDecodeAtTheSmallFaceWidth() {
        assertEquals(180, images().face(ace, small = true).width)
    }

    @Test
    fun aPackWithoutSmallFacesUsesItsFullFacesAtFullWidth() {
        assertEquals(252, images(withSmallFaces = false).face(ace, small = true).width)
    }

    @Test
    fun artNarrowerThanTheCardIsNeverScaledUp() {
        assertEquals(200, images(width = 200, height = 280).face(ace, small = false).width)
    }
}
