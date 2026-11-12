package com.cardgames.feature.solitaire.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.ui.cards.LocalSmallCardFaces
import com.cardgames.core.ui.table.MovableCard
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The table's arrangement for one [game] position: the foundations across the top, the tableau
 * filling the middle, the draw pile at the bottom — on a board no wider than the design's cap.
 * [aboveDrawRow] sits just above the draw pile (the tablet's hint), without moving anything else. The
 * screen owns everything around it (motion, drag, the panel); a different arrangement for another
 * window size would sit beside this one, taking the same parts. [foundationModifier] goes on the
 * foundation row (the screen reads where it is, for the Solved screen's kings).
 */
@Composable
internal fun SolitaireTable(
    game: KlondikeState,
    look: BoardLook,
    input: BoardInput,
    cards: (Card) -> MovableCard,
    modifier: Modifier = Modifier,
    foundationModifier: Modifier = Modifier,
    aboveDrawRow: (@Composable (columnWidth: Dp) -> Unit)? = null,
) {
    // Solitaire's cards all wear the small face — the bigger foundation and stock cards read as the
    // same cards as the columns'.
    val m = solitaireMetrics
    CompositionLocalProvider(LocalSmallCardFaces provides true) {
        BoxWithConstraints(modifier = modifier.widthIn(max = CardGamesTheme.spacing.maxBoardWidth)) {
            val columnWidth = m.columnWidthFor(DpSize(maxWidth, maxHeight))
            Column(modifier = Modifier.fillMaxSize().padding(top = m.tableTop)) {
                FoundationRow(game, look, input, cards, columnWidth, foundationModifier)
                Spacer(modifier = Modifier.height(m.foundationGap))
                Tableau(game, look, input, cards, modifier = Modifier.weight(1f))
                if (aboveDrawRow != null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = m.drawRowBottom), contentAlignment = Alignment.Center) {
                        aboveDrawRow(columnWidth)
                    }
                }
                DrawRow(game, look, input, cards, columnWidth)
                Spacer(modifier = Modifier.height(m.drawRowBottom + m.statusRoom))
            }
        }
    }
}
