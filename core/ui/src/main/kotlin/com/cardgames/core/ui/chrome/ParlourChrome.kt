package com.cardgames.core.ui.chrome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The Parlour chrome kit: buttons, the bottom panel, labels and the grouped settings list. Panel
// pieces (buttons, tool buttons) sit on the cream panel; the rest sit directly on the felt.

/** Buttons in the bottom panel take the compact size, matching the tool tiles beside them. */
internal val LocalCompactButtons = staticCompositionLocalOf { false }
internal val TOOL_LABEL_GAP: Dp = 2.dp
