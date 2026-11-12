package com.cardgames.core.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * Wraps a "start over" action (solitaire's New game, gin's New match) so it asks first whenever
 * going ahead would throw away a game in progress and record it as a loss — a stray tap on a top
 * bar button shouldn't silently cost the player their streak. Call the returned function from the
 * button; it runs [onConfirmed] immediately when [needsConfirmation] is false. [detail] says what
 * going ahead costs (Solitaire's Restart can be undone, so it says that instead). [onKept] runs
 * when the player picks "Keep playing" — off the table (match setup), that's back to the game.
 */
@Composable
fun rememberConfirmAbandon(
    needsConfirmation: Boolean,
    title: String,
    confirmLabel: String,
    detail: String = "Your current game will count as a loss.",
    onKept: () -> Unit = {},
    onConfirmed: () -> Unit,
): () -> Unit {
    var showing by remember { mutableStateOf(false) }
    if (showing) {
        // The window's own dim is switched off and the scrim is drawn instead, edge to edge (hence
        // no system-window insets) — the same stop as the pause menu.
        Dialog(
            onDismissRequest = { showing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            HideNavigationBarInWindow(dimAmount = 0f)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(CardGamesTheme.colors.scrim.copy(alpha = SCRIM_ALPHA))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showing = false },
                contentAlignment = Alignment.Center,
            ) {
                ConfirmCard(
                    title = title,
                    detail = detail,
                    confirmLabel = confirmLabel,
                    onConfirm = {
                        showing = false
                        onConfirmed()
                    },
                    onCancel = {
                        showing = false
                        onKept()
                    },
                )
            }
        }
    }
    return { if (needsConfirmation) showing = true else onConfirmed() }
}

/** A question on a cream card: the [title], a [detail] line, then "Keep playing" and the brass [confirmLabel]. */
@Composable
fun ConfirmCard(
    title: String,
    detail: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    DialogCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm)) {
        Text(title, color = colors.onPanel, style = CardGamesTheme.type.heading)
        Text(detail, color = colors.onPanelMuted, style = CardGamesTheme.type.body)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CardGamesTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.sm),
        ) {
            SecondaryButton(text = "Keep playing", onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton(text = confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

private const val SCRIM_ALPHA = 0.72f
