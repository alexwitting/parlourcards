package com.cardgames.core.domain.theme

import com.cardgames.core.testing.testThemePack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ThemeResolveTest {
    private val default = testThemePack(DEFAULT_THEME_ID)
    private val lagoon = testThemePack("lagoon")
    private val midnight = testThemePack("midnight")

    @Test
    fun `the stored pack wins when it's installed`() {
        assertEquals(lagoon, listOf(default, lagoon, midnight).resolve("lagoon"))
    }

    @Test
    fun `a missing or unset choice falls back to the default pack`() {
        assertEquals(default, listOf(midnight, default).resolve("uninstalled"))
        assertEquals(default, listOf(midnight, default).resolve(null))
    }

    @Test
    fun `without the default, any installed pack is used`() {
        assertEquals(midnight, listOf(midnight, lagoon).resolve("uninstalled"))
    }

    @Test
    fun `no packs means no theme`() {
        assertNull(emptyList<ThemePack>().resolve(DEFAULT_THEME_ID))
    }
}
