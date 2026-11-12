package com.cardgames.feature.solitaire.board

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Solitaire's column width on the phone, pinned before any other board size is added. */
class ColumnWidthTest {
    @Test
    fun `seven columns share the board between its margins and gaps`() {
        // (360 − 2 × 17 margins − 6 × 5 gaps) / 7
        assertEquals(296f / 7, SolitaireMetrics.Phone.columnWidthFor(DpSize(360.dp, 700.dp)).value, 0.001f)
    }

    @Test
    fun `the widest board, at the cap, gives the widest columns`() {
        assertEquals(576f / 7, SolitaireMetrics.Phone.columnWidthFor(DpSize(640.dp, 700.dp)).value, 0.001f)
    }

    @Test
    fun `the height doesn't change the width yet`() {
        val phone = SolitaireMetrics.Phone
        assertEquals(phone.columnWidthFor(DpSize(360.dp, 700.dp)), phone.columnWidthFor(DpSize(360.dp, 400.dp)))
    }
}
