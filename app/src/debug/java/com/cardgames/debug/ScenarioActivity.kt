package com.cardgames.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.cardgames.MainActivity
import com.cardgames.core.domain.repository.SavedGameRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug builds only ("Parlour Scenarios" in the launcher): sets a game up just before a moment
 * worth checking — Complete, a win, a gin, a match result — by writing it as that game's saved game,
 * then reopens the app on Home, where Continue opens it. Replaces the Dev app's saved game for that
 * game. From a computer:
 *
 * ```
 * adb shell am start -n com.cardgames.dev/com.cardgames.debug.ScenarioActivity --es scenario solitaire-complete
 * ```
 */
@AndroidEntryPoint
class ScenarioActivity : ComponentActivity() {
    @Inject
    lateinit var savedGames: SavedGameRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_SCENARIO)?.let { id ->
            open(scenarios.firstOrNull { it.id == id } ?: error("No scenario \"$id\"; one of ${scenarios.map { it.id }}"))
            return
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(modifier = Modifier.safeDrawingPadding()) {
                        item {
                            Text(
                                "Each replaces the Dev app's saved game for that game, then opens Home: tap Continue.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        items(scenarios) { scenario ->
                            Column(modifier = Modifier.fillMaxWidth().clickable { open(scenario) }.padding(16.dp)) {
                                Text(scenario.title, style = MaterialTheme.typography.titleMedium)
                                Text(scenario.description, style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    private fun open(scenario: Scenario) {
        lifecycleScope.launch {
            savedGames.save(scenario.game, scenario.snapshotJson())
            // A fresh task, so the games' ViewModels load the new save rather than keep what they hold.
            startActivity(
                Intent(this@ScenarioActivity, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                ),
            )
            finish()
        }
    }

    private companion object {
        const val EXTRA_SCENARIO = "scenario"
    }
}
