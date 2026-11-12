package com.cardgames.core.ui.layout

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WindowLayoutTest {
    @Test
    fun `a phone held upright is compact wide and a phone however it's held`() {
        val upright = WindowLayout(360.dp, 780.dp)
        val sideways = WindowLayout(780.dp, 360.dp)

        assertEquals(WindowSizeClass.COMPACT, upright.widthClass)
        assertEquals(WindowSizeClass.MEDIUM, upright.heightClass)
        assertEquals(WindowSizeClass.COMPACT, sideways.heightClass)
        assertEquals(FormFactor.PHONE, upright.formFactor)
        assertEquals(FormFactor.PHONE, sideways.formFactor)
    }

    @Test
    fun `a tablet is medium or expanded, and no phone`() {
        val portrait = WindowLayout(800.dp, 1280.dp)
        val landscape = WindowLayout(1280.dp, 800.dp)

        assertEquals(WindowSizeClass.MEDIUM, portrait.widthClass)
        assertEquals(WindowSizeClass.EXPANDED, portrait.heightClass)
        assertEquals(WindowSizeClass.EXPANDED, landscape.widthClass)
        assertEquals(WindowSizeClass.MEDIUM, landscape.heightClass)
        assertEquals(FormFactor.TABLET, portrait.formFactor)
        assertEquals(FormFactor.TABLET, landscape.formFactor)
    }

    @Test
    fun `the tablet layout starts at a 600dp shorter side`() {
        assertEquals(FormFactor.PHONE, WindowLayout(599.dp, 1000.dp).formFactor)
        assertEquals(FormFactor.TABLET, WindowLayout(600.dp, 960.dp).formFactor)
    }

    @Test
    fun `the bands start exactly at Material's breakpoints`() {
        assertEquals(WindowSizeClass.COMPACT, WindowLayout(599.dp, 1000.dp).widthClass)
        assertEquals(WindowSizeClass.MEDIUM, WindowLayout(600.dp, 1000.dp).widthClass)
        assertEquals(WindowSizeClass.EXPANDED, WindowLayout(840.dp, 1000.dp).widthClass)
        assertEquals(WindowSizeClass.COMPACT, WindowLayout(1000.dp, 479.dp).heightClass)
        assertEquals(WindowSizeClass.MEDIUM, WindowLayout(1000.dp, 480.dp).heightClass)
        assertEquals(WindowSizeClass.EXPANDED, WindowLayout(1000.dp, 900.dp).heightClass)
    }
}
