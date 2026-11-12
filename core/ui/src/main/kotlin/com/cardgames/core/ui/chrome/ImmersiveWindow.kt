package com.cardgames.core.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Keeps the navigation bar hidden in the window this is composed into — a dialog or sheet, which
 * gets a window of its own. The app hides the bar app-wide; a new window would bring it back, and
 * the screen behind would re-lay out around it, then jump back when the window closed. [dimAmount]
 * sets the window's own dim (0 when the content draws its own scrim).
 */
@Composable
fun HideNavigationBarInWindow(dimAmount: Float? = null) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        dimAmount?.let(window::setDimAmount)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
