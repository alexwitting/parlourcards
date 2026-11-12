package com.cardgames.feature.ginrummy

import androidx.lifecycle.SavedStateHandle
import com.cardgames.core.domain.ginrummy.ai.GinOpponentFactory
import com.cardgames.core.domain.ginrummy.ai.HeuristicGinOpponent
import com.cardgames.core.domain.repository.SavedGameRepository
import com.cardgames.core.domain.repository.SettingsRepository
import com.cardgames.core.domain.repository.StatsRepository
import com.cardgames.core.testing.FakeClockProvider
import com.cardgames.core.testing.FakeSavedGameRepository
import com.cardgames.core.testing.FakeSeedProvider
import com.cardgames.core.testing.FakeSettingsRepository
import com.cardgames.core.testing.FakeStatsRepository
import kotlinx.coroutines.Dispatchers

/**
 * A [GinViewModel] on fakes, seeded with 1, playing the quick [HeuristicGinOpponent] with no
 * pauses ([GinPacing.Instant]) and its searches on the calling thread. Nothing has landed yet —
 * the opening deal is still in flight; [settle] lands it.
 */
internal fun testGinViewModel(
    opponentFactory: GinOpponentFactory = GinOpponentFactory { HeuristicGinOpponent() },
    savedGames: SavedGameRepository = FakeSavedGameRepository(),
    stats: StatsRepository = FakeStatsRepository(),
    settings: SettingsRepository = FakeSettingsRepository(),
    savedState: SavedStateHandle = SavedStateHandle(),
) = GinViewModel(
    FakeSeedProvider(seed = 1L),
    FakeClockProvider(),
    opponentFactory,
    savedGames,
    stats,
    settings,
    GinPacing.Instant,
    Dispatchers.Unconfined,
    savedState,
)

/** Lands every motion in flight — the screen's part under commit-on-land — until the table is still. */
internal fun GinViewModel.settle() {
    repeat(MAX_LANDINGS) {
        val pending = state.value.pending ?: return
        onIntent(GinIntent.MoveLanded(pending.id))
    }
}

/** Far more landings than any chain of motions (a deal, then every opponent reply) needs. */
private const val MAX_LANDINGS = 100
