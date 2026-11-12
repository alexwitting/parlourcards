package com.cardgames.core.domain.ginrummy.ai

import com.cardgames.core.domain.ginrummy.GinEngine
import com.cardgames.core.domain.ginrummy.GinPhase
import com.cardgames.core.domain.ginrummy.GinPlayer
import com.cardgames.core.domain.ginrummy.GinState
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Head-to-head benchmark of the shipped [GinDifficulty] tiers — a measurement, not a pass/fail
 * test, so it never asserts on strength. Opt-in only (it takes minutes to hours, because every
 * Monte Carlo decision spends its real wall-clock budget), and skipped in CI and in a plain
 * `./gradlew test`:
 *
 * ```
 * GIN_BENCHMARK=1 ./gradlew :core:domain:test --tests "*GinTierBenchmark" --rerun
 * ```
 *
 * `--rerun` matters: environment variables aren't task inputs, so Gradle would otherwise report
 * the test task UP-TO-DATE and skip it. Optional knobs (environment variables):
 * - `GIN_BENCHMARK_DEALS` — deals per pairing (default 60; each is played twice, see below).
 * - `GIN_BENCHMARK_PAIRINGS` — comma-separated, e.g. `HARD:MEDIUM,MEDIUM:EASY` (default: all three).
 * - `GIN_BENCHMARK_THREADS` — games played in parallel (default: half the cores). Each decision
 *   is single-threaded and wall-clock budgeted, so keep this at or below the physical core count
 *   or the tiers get less search than they would on a phone's dedicated thread.
 *
 * Duplicate format: each deal (seed) is played twice with the seats swapped, so both tiers get
 * the same cards in the same seat once each — the luck of the deal cancels and the per-deal
 * difference isolates decision quality. Single hands, not whole matches: a hand's points are the
 * unit a match is made of, and many more of them fit in the same time.
 *
 * Results print to stdout and are written to `build/gin-benchmark/report.txt` (relative to the
 * module). Numbers come from this machine's speed: a desktop JVM runs far more rollouts inside
 * each tier's budget than a phone does, so treat the gap here as an upper bound on the on-device
 * gap.
 */
@Tag("benchmark")
@EnabledIfEnvironmentVariable(named = "GIN_BENCHMARK", matches = "1")
class GinTierBenchmark {
    @Test
    fun `report head-to-head results for each difficulty pairing`() {
        val deals = env("GIN_BENCHMARK_DEALS")?.toInt() ?: DEFAULT_DEALS
        val threads = env("GIN_BENCHMARK_THREADS")?.toInt() ?: (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        val pairings = env("GIN_BENCHMARK_PAIRINGS")?.let(::parsePairings) ?: DEFAULT_PAIRINGS

        val report = StringBuilder()
        report.appendLine("Gin tier benchmark — $deals deals per pairing, each played twice (seats swapped), $threads threads")
        report.appendLine()
        val executor = Executors.newFixedThreadPool(threads)
        try {
            for ((a, b) in pairings) {
                val started = System.nanoTime()
                val results = runPairing(a, b, deals, executor)
                val minutes = (System.nanoTime() - started) / NANOS_PER_MINUTE
                report.appendLine(summarize(a, b, results, minutes))
                println(report.lines().takeLast(SUMMARY_LINES).joinToString("\n"))
            }
        } finally {
            executor.shutdownNow()
        }

        println(report)
        File("build/gin-benchmark").apply { mkdirs() }.resolve("report.txt").writeText(report.toString())
    }

    /** One deal's two games: [a]'s net points in each seat (positive = [a] scored). */
    private data class DealResult(
        val aAsPlayer: Int,
        val aAsOpponent: Int,
    ) {
        val net: Int get() = aAsPlayer + aAsOpponent
    }

    private fun runPairing(
        a: GinDifficulty,
        b: GinDifficulty,
        deals: Int,
        executor: ExecutorService,
    ): List<DealResult> {
        val games =
            (1..deals).flatMap { deal ->
                val seed = BASE_SEED + deal
                listOf(
                    Callable { netForA(playHand(seed, player = a, opponent = b), aSeat = GinPlayer.PLAYER) },
                    Callable { netForA(playHand(seed, player = b, opponent = a), aSeat = GinPlayer.OPPONENT) },
                )
            }
        val nets = executor.invokeAll(games).map { it.get() }
        return nets.chunked(2).map { (asPlayer, asOpponent) -> DealResult(asPlayer, asOpponent) }
    }

    /** Plays the first hand of [seed]'s match to its end, each seat driven by its own tier. */
    private fun playHand(
        seed: Long,
        player: GinDifficulty,
        opponent: GinDifficulty,
    ): GinState {
        // Each game gets its own instances (they hold their own Random), seeded for repeatable
        // sampling — though the wall-clock budget still makes exact results vary run to run.
        val playerAi = MonteCarloGinOpponent(player, Random(seed * 2))
        val opponentAi = MonteCarloGinOpponent(opponent, Random(seed * 2 + 1))
        var state = GinEngine.newMatch(seed)
        var ticks = 0
        while (!GinEngine.isHandOver(state)) {
            state = GinOpponentDriver.driveOneDecision(state, GinPlayer.PLAYER, playerAi)
            state = GinOpponentDriver.driveOneDecision(state, GinPlayer.OPPONENT, opponentAi)
            ticks++
            check(ticks <= MAX_TICKS_PER_HAND) { "seed $seed: hand didn't finish within $MAX_TICKS_PER_HAND ticks" }
        }
        return state
    }

    private fun netForA(
        state: GinState,
        aSeat: GinPlayer,
    ): Int {
        val result =
            when (val phase = state.phase) {
                is GinPhase.HandOver -> phase.result
                // A first hand big enough to end the match outright carries no HandResult; the
                // match started 0–0, so the score difference is that hand's points.
                is GinPhase.MatchOver -> return state.matchScores.getValue(aSeat) - state.matchScores.getValue(aSeat.other)
                else -> error("hand not over")
            }
        return when (result.winner) {
            null -> 0
            aSeat -> result.pointsScored
            else -> -result.pointsScored
        }
    }

    private fun summarize(
        a: GinDifficulty,
        b: GinDifficulty,
        results: List<DealResult>,
        minutes: Double,
    ): String {
        val games = results.flatMap { listOf(it.aAsPlayer, it.aAsOpponent) }
        val aWins = games.count { it > 0 }
        val bWins = games.count { it < 0 }
        val blocked = games.count { it == 0 }
        // The per-deal net (both seats) is the duplicate-format statistic: its mean is A's edge
        // per deal, and its spread gives the confidence interval.
        val nets = results.map { it.net.toDouble() }
        val mean = nets.average()
        val sd = sqrt(nets.sumOf { (it - mean) * (it - mean) } / (nets.size - 1).coerceAtLeast(1))
        val halfWidth = Z_95 * sd / sqrt(nets.size.toDouble())
        val verdict =
            when {
                mean - halfWidth > 0 -> "$a is measurably stronger"
                mean + halfWidth < 0 -> "$b is measurably stronger"
                else -> "no measurable difference at this sample size"
            }
        return buildString {
            appendLine("$a vs $b  (${"%.1f".format(minutes)} min)")
            appendLine("  hands won: $a $aWins · $b $bWins · blocked $blocked  (of ${games.size})")
            appendLine("  $a points per deal (both seats): ${"%+.2f".format(mean)}  95% CI ±${"%.2f".format(halfWidth)}")
            appendLine("  → $verdict")
        }
    }

    private fun parsePairings(raw: String): List<Pair<GinDifficulty, GinDifficulty>> =
        raw.split(",").map { pairing ->
            val (a, b) = pairing.trim().split(":").map { GinDifficulty.valueOf(it.trim().uppercase()) }
            a to b
        }

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    private companion object {
        const val DEFAULT_DEALS = 60
        val DEFAULT_PAIRINGS =
            listOf(
                GinDifficulty.HARD to GinDifficulty.MEDIUM,
                GinDifficulty.MEDIUM to GinDifficulty.EASY,
                GinDifficulty.HARD to GinDifficulty.EASY,
            )

        /** Far from the seeds other tests use, so the benchmark's deals are its own. */
        const val BASE_SEED = 900_000L
        const val MAX_TICKS_PER_HAND = 400
        const val Z_95 = 1.96
        const val NANOS_PER_MINUTE = 60_000_000_000.0
        const val SUMMARY_LINES = 5
    }
}
