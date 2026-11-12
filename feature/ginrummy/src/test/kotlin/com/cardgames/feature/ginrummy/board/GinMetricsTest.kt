package com.cardgames.feature.ginrummy.board

import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.layout.FormFactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** The table's sizes for each form factor and table size. */
class GinMetricsTest {
    @Test
    fun `a table at least as tall as the regular one needs gets the design's sizes`() {
        assertSame(GinMetrics.Regular, GinMetrics.forTable(FormFactor.PHONE, 360.dp, GinMetrics.RegularMinHeight))
        assertSame(GinMetrics.Regular, GinMetrics.forTable(FormFactor.PHONE, 360.dp, 700.dp))
    }

    @Test
    fun `a shorter table gets the compact sizes`() {
        assertSame(GinMetrics.Compact, GinMetrics.forTable(FormFactor.PHONE, 360.dp, GinMetrics.RegularMinHeight - 1.dp))
        assertSame(GinMetrics.Compact, GinMetrics.forTable(FormFactor.PHONE, 360.dp, 480.dp))
    }

    @Test
    fun `the regular table is the design's`() {
        val m = GinMetrics.Regular
        assertEquals(84.dp, m.pileWidth)
        assertEquals(44.dp, m.opponentCard)
        assertEquals(44.dp, m.meldCard)
        assertEquals(72.dp, m.handCard)
        assertEquals(66.dp, m.handCardMany)
        assertEquals(28.dp, m.pileGap)
        assertEquals(16.dp, m.handMargin)
    }

    @Test
    fun `compact trims the cards but keeps the table's spacing`() {
        val regular = GinMetrics.Regular
        val compact = GinMetrics.Compact
        assertEquals(66.dp, compact.handCard)
        assertEquals(58.dp, compact.handCardMany)
        assertEquals(regular.pileGap, compact.pileGap)
        assertEquals(regular.handMargin, compact.handMargin)
        assertEquals(regular.manyFan, compact.manyFan)
    }

    @Test
    fun `a fan turns its cards evenly about the middle, two cards a fixed amount, one not at all`() {
        val fan = GinMetrics.Regular.manyFan

        assertEquals(0f, fan.turnOf(0, 1))
        assertEquals(listOf(-3f, 3f), List(2) { fan.turnOf(it, 2) })
        assertEquals(listOf(-4f, 0f, 4f), List(3) { fan.turnOf(it, 3) })
        // Eleven cards: the per-place turn shrinks so the ends stop at the edge turn.
        assertEquals(-9.1f, fan.turnOf(0, 11), 0.001f)
        assertEquals(9.1f, fan.turnOf(10, 11), 0.001f)
    }

    @Test
    fun `a tablet gets the tablet table, fitted to its width`() {
        assertEquals(GinMetrics.Tablet, GinMetrics.forTable(FormFactor.TABLET, 800.dp, 1280.dp))
        val narrow = GinMetrics.forTable(FormFactor.TABLET, 753.dp, 1205.dp)
        assertEquals(128f * 753 / 800, narrow.handCard.value, 0.01f)
        // Never bigger than the design's, never below three-quarters of it.
        assertEquals(GinMetrics.Tablet.handCard, GinMetrics.forTable(FormFactor.TABLET, 1000.dp, 1400.dp).handCard)
        assertEquals(96f, GinMetrics.forTable(FormFactor.TABLET, 500.dp, 900.dp).handCard.value, 0.01f)
    }
}
