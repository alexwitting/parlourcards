package com.cardgames.core.data.theme

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/** The asset adapter under [AssetThemeRepository]; pack validation itself is [ThemePackLoaderTest]'s. */
class AssetThemeRepositoryTest {
    private val assets = mockk<AssetManager>()
    private val context = mockk<Context> { every { assets } returns this@AssetThemeRepositoryTest.assets }

    @Test
    fun `an APK with no themes folder has no packs, rather than failing`() {
        every { assets.list(any()) } returns null

        assertTrue(runBlocking { AssetThemeRepository(context).availablePacks() }.isEmpty())
    }

    @Test
    fun `an unreadable themes folder is treated as empty`() {
        every { assets.list(any()) } throws IOException("broken APK")

        assertTrue(runBlocking { AssetThemeRepository(context).availablePacks() }.isEmpty())
    }

    @Test
    fun `a pack folder without a valid manifest is skipped`() {
        every { assets.list("themes") } returns arrayOf("broken")
        every { assets.list(match { it != "themes" }) } returns emptyArray()
        every { assets.open(any()) } throws IOException("missing")

        assertEquals(emptyList<Any>(), runBlocking { AssetThemeRepository(context).availablePacks() })
    }
}
