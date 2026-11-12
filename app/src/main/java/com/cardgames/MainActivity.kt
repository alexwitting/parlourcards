package com.cardgames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardgames.core.ui.feedback.LocalGameFeedback
import com.cardgames.core.ui.feedback.SoundHapticGameFeedback
import com.cardgames.core.ui.theme.AppTheme
import com.cardgames.navigation.CardGamesNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            !mainViewModel.isReady.value
        }

        enableEdgeToEdge()
        hideSystemNavigationBar()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val cardTableTheme by viewModel.theme.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val haptics = LocalHapticFeedback.current
            val feedback =
                remember(viewModel, haptics) {
                    SoundHapticGameFeedback(
                        context = applicationContext,
                        haptics = haptics,
                        isSoundEnabled = { viewModel.settings.value.soundEnabled },
                        isHapticsEnabled = { viewModel.settings.value.hapticsEnabled },
                    )
                }
            DisposableEffect(feedback) {
                onDispose { feedback.release() }
            }
            AppTheme(
                cardTableTheme = cardTableTheme,
                animationSpeed = settings.animationSpeed,
                reduceMotion = settings.reduceMotion,
            ) {
                CompositionLocalProvider(LocalGameFeedback provides feedback) {
                    CardGamesNavGraph(
                        // Test tags are visible to UI Automator, for the Baseline Profile and benchmark journeys.
                        modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
                    )
                }
            }
        }
    }

    /** Immersive: hide the nav bar (home/back) so the table uses the full screen. Swipe reveals it. */
    private fun hideSystemNavigationBar() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** The nav bar re-appears whenever focus is regained (e.g. after a system dialog); re-hide it. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigationBar()
    }
}
