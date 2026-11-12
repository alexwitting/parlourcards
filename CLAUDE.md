# CLAUDE.md

This file provides guidance to Claude Code when working in this repository.

## Project

**CardGames** — an offline Android app of classic card games: **Solitaire** (Klondike, draw 1 or 3,
optional winnable-deals solver) and **Gin Rummy** against a Monte Carlo AI opponent (Easy / Medium /
Hard). No ads, no analytics, no network. The look comes from swappable **theme packs** (table art,
card faces and backs, chrome colors) plus a shared design system.

- `docs/THEME_SPEC.md` — the asset contract for theme packs (the user generates all theme images
  with an external tool against that spec).
- `docs/DESIGN_SYSTEM.md` — the design-system layer in `:core:ui` (type, spacing, motion, shared
  chrome and card-table components) and the screenshot-test workflow. Screens take every visual
  value from it rather than setting their own.

`minSdk 26`, `targetSdk 35`. `java.time` classes (`LocalDate`, `Instant`, `Duration`) work
natively — no desugaring needed.

## Working agreements

- **Ask before running Gradle** (builds, tests, lint, screenshot tasks). Once the user agrees, run
  the relevant checks and fix failures before reporting; when not running them, list the commands.
- **Never commit.** The user handles all git commits, branches and pushes.
- Gradle needs the Android SDK: `ANDROID_HOME` set, or `sdk.dir` in `local.properties`.
- Release builds are minified (R8). Type-safe navigation looks enum route arguments up by name,
  so they need a keep rule in `app/proguard-rules.pro` (`GameId` has one) — without it the release
  app crashes at launch while debug runs fine.

## Build & Test Commands

```bash
# Build
./gradlew assembleDebug

# Run unit tests for a specific module / a single test class / everything
./gradlew :core:domain:test
./gradlew :core:domain:test --tests "com.cardgames.core.domain.SomeTest"
./gradlew test

# Android Lint (main sources only — test sources are covered by ktlint + detekt); runs in CI
./gradlew lint

# ktlint — check formatting / auto-fix
./gradlew ktlintCheck
./gradlew ktlintFormat

# detekt — static analysis (config: config/detekt/detekt.yml)
./gradlew detekt
# regenerate a module's baseline after intentionally accepting new findings:
./gradlew detektBaseline

# Screenshot tests (Roborazzi) — verify against committed baselines / re-record after an
# intended change (review the *_compare.png diffs in build/outputs/roborazzi first)
./gradlew verifyRoborazziDebug
./gradlew recordRoborazziDebug

# Coverage (Kover) — aggregated report at build/reports/kover/html/index.html; per-module floors
./gradlew koverHtmlReport
./gradlew koverVerify

# Gin AI tier benchmark — opt-in, slow (real thinking budgets); --rerun is required because the
# env var isn't a task input. Optional: GIN_BENCHMARK_DEALS, GIN_BENCHMARK_PAIRINGS=HARD:MEDIUM,...
GIN_BENCHMARK=1 ./gradlew :core:domain:test --tests "*GinTierBenchmark" --rerun

# Baseline Profile and macrobenchmarks — on a connected phone. They drive the profiling builds,
# installed as com.cardgames.benchmark (set in app/build.gradle.kts; the journeys refuse any other id),
# which they clear and uninstall — never point them at com.cardgames, it would lose a player's data.
# Regenerate the profile (commit app/src/release/generated/baselineProfiles/) after big screen changes:
./gradlew :app:generateReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.class=com.cardgames.baselineprofile.BaselineProfileGenerator
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest   # cold start + play, with and without
# On-device journeys (kill and resume, settings and stats across restarts) — same safe install:
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.cardgames.baselineprofile.AppJourneyTest

# Clean
./gradlew clean
```

CI (`.github/workflows/cardgames.yml`) runs `ktlintCheck detekt lint`, then `test` + Kover reports +
`koverVerify` (each module's coverage floor), then `assembleRelease` (R8, which debug builds skip) with a
check that `GameId` kept its name, then `verifyRoborazziDebug` (uploading the diff images if
it fails), on every push/PR to `master`; a manual `workflow_dispatch` toggle also builds and uploads
the debug APK. Android Lint's `MissingKeepAnnotation` is an error (`app/lint.xml`): an enum used as a
navigation argument needs a keep rule, or the minified release crashes at launch.

## Module Structure

```
:app                  — Hilt root, MainActivity (AppTheme + theme/settings wiring), CardGamesNavGraph
:feature:main         — Home (game list) and Settings screens
:feature:solitaire    — Klondike screen + ViewModel
:feature:ginrummy     — Gin Rummy screen + ViewModel (+ GinAiModule: per-match AI opponent factory)
:feature:stats        — Stats screen
:core:domain          — Pure Kotlin JVM — game engines, AI, models, repository interfaces. ZERO Android deps.
:core:data            — Repository impls: Room (saved games, results), DataStore (settings), theme-pack loader
:core:ui              — Design system (theme/, motion/), chrome/, cards/, table/ kit, feedback/ (sound + haptics)
:core:testing         — Shared fakes (Fake*Repository, FakeClockProvider…) and fixtures (`cards("A♣ 2♣")`,
                        `ginPosition(hand)`) — plain JVM, testImplementation only
:baselineprofile      — Baseline Profile generator, macrobenchmarks and end-to-end journeys (com.android.test,
                        runs on a phone against com.cardgames.benchmark)
:core:testing-ui      — Shared Compose test helpers: `snapshot { }` (screenshots), `TestDevices` (phone/tablet
                        qualifiers), `RecordingFeedback` — testImplementation only
build-logic/          — Convention Gradle plugins
tools/                — gen_sounds.js (the sound effects); upgrade_check.sh (on-device upgrade check);
                        monkey_run.sh (crash hunt on a separate install); theme packs
                        are drawn by ThemePackRenderTest (THEME_SPEC)
```

**Dependency rule:** features depend on `:core:domain` and `:core:ui` only. Features never depend
on each other. `:app` is the only module allowed to depend on everything.

## Architecture — MVI

```
UI (Compose)  →Intent→  ViewModel  →  game engines (pure, :core:domain) + repository interfaces
                ↑State/Effect↑                                   ↓
                                          Repository implementations (:core:data)
                                                                 ↓
                                                   Room / DataStore / theme assets
```

- There is no UseCase layer: ViewModels call the pure engines (`KlondikeEngine`, `GinEngine`) and
  repository interfaces directly. Add a UseCase only when logic is genuinely shared between
  ViewModels.
- Each screen state is one `data class`. ViewModels expose `StateFlow<ScreenState>` and
  `SharedFlow<Effect>` (one-shot: navigate, snackbar, sound/haptic cues) via `MutableSharedFlow` +
  `asSharedFlow()` — never a raw `Channel`. No LiveData anywhere.
- Every screen is a thin `<Screen>` (`hiltViewModel()` wrapper) + an `internal <Screen>Content(state,
  onIntent, …)` that's stateless with zero Hilt dependency — that's what previews, smoke tests and
  screenshot tests render.
- **Games share** `UndoRedoHistory<S>` (undo/redo of whole positions, persisted capped) and
  `GamePersistence<Snapshot>` (save slot + stats per `GameId`) from `:core:domain/game`.
- **Error handling:** mutation methods wrap the call in
  `runCatching { ... }.onSuccess { }.onFailure { Log.e(TAG, "...", it); emit(Effect.ShowSnackbar(...)) }`.
  A `combine(...).collect { }` in `init` that drives the screen's primary state should have a
  `.catch { }` upstream of `collect` — an uncaught exception there silently ends the flow forever
  (the screen freezes with no further updates and no error shown), not just for that one tick.
- **Time/date access:** business logic never calls `LocalDate.now()` / `LocalDateTime.now()` /
  `System.currentTimeMillis()` directly — inject `ClockProvider` (`:core:domain/time/ClockProvider.kt`)
  instead, and use `FakeClockProvider` (`:core:testing`) in tests. The sanctioned exceptions are
  pure UI display code and wall-clock compute budgets (the AI's thinking time, the solver's deadline).
- **Heavy work off Main:** AI decisions, solver searches and save encoding/decoding
  (`GamePersistence`'s `codec`) run on the injected `CoroutineDispatcher` (`DispatcherModule`,
  `Dispatchers.Default`), so animations never stall; tests pass `Dispatchers.Unconfined`.
- **Smooth table rules** are collected in
  `docs/DESIGN_SYSTEM.md` → "Keeping the table still" — per-card state in `key(card)`, panels that
  never change height, fades that don't clip. Read it before touching a game board.

## Convention Plugins (build-logic)

| Plugin ID | Applied to | Does |
|---|---|---|
| `cardgames.kotlin.jvm` | `:core:domain`, `:core:testing` | Kotlin JVM, Java 21, `useJUnitPlatform()` |
| `cardgames.android.library` | all other `:core:*` + `:feature:*` | Android library, compileSdk 36, minSdk 26 |
| `cardgames.android.application` | `:app` | Android application, same SDK |
| `cardgames.hilt` | any module using Hilt | applies Hilt + KSP plugins, adds hilt-android + compiler deps |
| `cardgames.compose` | any module with Compose | applies compose plugin, enables buildFeatures.compose, @Preview tooling |
| `cardgames.detekt` | every module | applies detekt + ktlint, points detekt at `config/detekt/detekt.yml` + a per-module baseline |
| `cardgames.kover` | every measured module | coverage, with Hilt/Room/Compose codegen exclusions |
| `cardgames.robolectric` | every feature module, and `:core:ui` | Robolectric + Compose UI test deps, JUnit4-vintage bridge, Roborazzi screenshot tests (baselines in `src/test/screenshots`, per-module temp dir for the native graphics library) |

## Key Domain Models (`:core:domain`)

- `cards/` — `Card`, `Suit`, `Rank`, `Deck`.
- `game/` — `GameId` (one entry per game), `SeedProvider`, `UndoRedoHistory`, `GamePersistence`.
- `solitaire/` — `KlondikeState` + `KlondikeEngine` (rules/moves), `KlondikeSolver` +
  `KlondikeDealer` + `WinnableDealCache` (winnable deals), `KlondikeHints`, `KlondikeAutoMove`,
  `KlondikeSnapshot` (save format).
- `ginrummy/` — `GinState` + `GinEngine` (phases, knocking, layoffs, scoring), `Melds`
  (best-partition solver), `GinSnapshot` (save format). `GinState.knownHeld` / `laidOff` are public
  table knowledge (discard-pile pickups; layoffs at hand end), kept for the AI and the display.
- `ginrummy/ai/` — `GinOpponent` strategy interface; `MonteCarloGinOpponent` (shipped) tuned per
  `GinDifficulty` via `MonteCarloConfig`; `HeuristicGinOpponent` (rollout policy + baseline);
  `GinOpponentFactory` (a fresh opponent per match, so a difficulty change applies next match);
  `HiddenWorldSampler`, `RolloutRunner`, `DiscardDanger` (HARD's defensive discards).
- `repository/` — `SettingsRepository` (+ `Settings`, `AnimationSpeed`), `SavedGameRepository`,
  `StatsRepository` (+ `GameResult`, `GameStats`).
- `theme/` — `ThemePack` + `ThemeRepository` (validated packs; see `docs/THEME_SPEC.md`).
- `time/` — `ClockProvider`, the current-time seam described above.

## DI Module Organization

- `:core:data/di/RepositoryModule` — one `@Binds @Singleton` per `:core:domain` interface:
  `ClockProvider`, `SeedProvider`, `SavedGameRepository`, `StatsRepository`, `SettingsRepository`,
  `ThemeRepository`, `WinnableDealCache`. `PersistenceModule` provides Room and DataStore;
  `DispatcherModule` the background `CoroutineDispatcher`.
- A feature module owns `di/` modules only for providers no other feature needs —
  e.g. `:feature:ginrummy`'s `GinAiModule` (the `GinOpponentFactory`). Anything two features need
  moves to `:core:data/di`.

## Adding a New Game

The recipe below is exactly how `:feature:solitaire` and `:feature:ginrummy` are built — a third
game should replicate the same shape, not invent a new one.

1. **New `:feature:<game>` module.** `build.gradle.kts` plugins: `cardgames.android.library`,
   `cardgames.compose`, `cardgames.hilt`, `cardgames.detekt`, `cardgames.kover`,
   `cardgames.robolectric`. Dependencies: `project(":core:domain")` + `project(":core:ui")`
   **only** — never another feature module (the dependency rule above).
2. **Engine/domain logic lives in `:core:domain`**, under its own package
   (`com.cardgames.core.domain.<game>`) — pure Kotlin, zero Android deps, same as
   `.ginrummy`/`.solitaire`, with a `<Game>Snapshot` save format. If the game needs an AI opponent,
   follow `GinOpponent`'s strategy-interface pattern, created per match via a factory.
3. **Register the game:** add a `GameId` entry (`:core:domain/game/GameId.kt`); extend
   `GameId.displayName` (`:core:ui/text/GameText.kt`, shared by Home/Stats) and `HomeScreen.kt`'s
   `GameId.tagline`; add the route to `CardGamesNavGraph.kt` (`:app`).
4. **ViewModel:** use `UndoRedoHistory` and `GamePersistence(GameId.<GAME>, …, <Game>Snapshot::toJson,
   <Game>Snapshot::fromJson, dispatcher)` rather than hand-rolling history or save plumbing. Give
   the state a `loaded` flag (false for the constructor's stand-in) so the screen never shows a
   placeholder game.
5. **Build the screen** as `<Game>Screen` + `internal <Game>Content(state, onIntent, ...)`, laid
   out like the existing games: the screen, ViewModel, UI state and `<Game>Feedback.kt`
   (sound/haptic per effect) at the package root; `board/` (the table's pieces, one file each, plus
   `<Game>Table.kt` — the arrangement of those pieces, which the screen draws inside its scaffold —
   and the top-bar chrome and drag handling); `motion/` (`<Game>MotionTokens`, `<Game>Choreography`);
   and, where the game has them, `result/` (result screens, `*Overlays.kt`) and `setup/`. Tests sit
   in the package of what they test; screenshot and smoke tests at the root. Reuse
   `GameScaffold` (`:core:ui`) for the top bar, bottom panel and pause menu, and the card-table kit
   (`:core:ui` `table/` — `rememberMovableCards`, `CardGlide`, `rememberTableHandoff`,
   `rememberTableReveal`) for the board; `rememberConfirmAbandon` for "start over" questions, with
   `HideNavigationBarInWindow` in any other dialog or sheet, and wrap `<Game>Content` in `CapFontScale` (table text grows to
   1.3× at most). Take every size, color, text style and duration from the
   design system (`docs/DESIGN_SYSTEM.md`) — no ad-hoc `.copy(fontSize = …)`, dp literals for shared
   measures, or raw millisecond constants.
   **Tablet:** the same screen draws both form factors. Put the table's sizes in a `<Game>Metrics`
   with a phone and a tablet set (like `SolitaireMetrics`, `GinMetrics`), chosen by
   `CardGamesTheme.formFactor`; give `GameScaffold` a `scoreCard` and `GameControls` (the tablet
   docks the tools bottom left, the game's panel bottom right); add `_tablet` screenshot tests and
   check the phone baselines don't move. The tablet layout of a new game is designed alongside the
   phone one (`docs/TABLET_PLAN.md` holds the existing games' decisions).
6. **DI:** a `di/` module in the new feature only for game-specific providers (like `GinAiModule`).
7. **Wire it in:** `app/build.gradle.kts` (`implementation(project(":feature:<game>"))`) and root
   `build.gradle.kts`'s Kover aggregation `dependencies` block (`kover(project(":feature:<game>"))`)
   — easy to forget; nothing fails loudly if you do, the module's coverage just silently never
   shows up in the merged report.
8. **Coverage floor:** start the new module's own `kover { reports { verify { rule { minBound(0)
   } } } }` block low rather than guessing a target — ratchet it up once there's a real measured
   baseline (see `:core:domain`'s `build.gradle.kts` for the "regression floor, not a target"
   rationale).
9. **Tests + previews early:** a `<Game>ContentSmokeTest` (`createComposeRule()` +
   `@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [34])`), a `<Game>ScreenshotTest`
   (`:core:testing-ui`'s `snapshot { }`, then `recordRoborazziDebug`), and a
   `<Game>ScreenPreviews.kt`. **Name preview files `*Previews.kt`** — `KoverConventionPlugin`'s
   Compose exclusion filter matches that pattern (and `*ScreenKt*`) specifically; a differently
   named file's top-level consts won't be excluded and will quietly drag measured coverage down.

## Tech Stack

| Concern | Library |
|---|---|
| UI | Jetpack Compose + Material 3 (fonts: Spectral + Instrument Sans, bundled, SIL OFL; Lucide-style icons) |
| Navigation | Navigation Compose (type-safe `@Serializable` routes) |
| DI | Hilt (compile-time verified) |
| Persistence | Room (saved games, results) + DataStore Preferences (settings); kotlinx.serialization snapshots |
| Unit testing | JUnit 5 + MockK + Turbine |
| UI testing | Robolectric + Compose UI test (smoke tests), Roborazzi (screenshot baselines) |

Versions live in `gradle/libs.versions.toml`. Always add deps there, not inline. Unused libraries
are kept commented out there (e.g. Haze) — uncomment rather than re-adding from scratch.

## Current State

Both games are complete and playable with save/resume, undo/redo, stats, sound and haptics, Card
speed and Reduce motion settings, and four theme packs (`parlour`, `violet_pop`, `lagoon`,
`midnight`). The app ("Parlour Cards"; debug builds install beside it as "Parlour Cards Dev", id
suffix `.dev`) follows the Parlour design on every screen but Home, whose redesign is on hold; the
decisions behind it are in `docs/DESIGN_SYSTEM.md` → "Design decisions". There is no first-game
tutorial — "How to play" (pause menu and Home) covers the rules. Tablets (shorter side 600dp or
more) have their own layout on every screen, built as a second set of specs rather than separate
screens (`docs/TABLET_PLAN.md`); phones and tablets are portrait only.

## Testing Conventions

- JUnit 5 (`@Test` from `org.junit.jupiter.api`) in all JVM modules; Compose/Robolectric tests are
  JUnit4 (`org.junit.Test`) via the vintage engine.
- MockK for mocking; `flowOf(...)` to stub Flow-returning repository methods;
  `Fake*Repository`/`FakeClockProvider` (`:core:testing`) for sociable ViewModel tests instead of
  mocking every dependency.
- Turbine for Flow assertions: `flow.test { assertEquals(..., awaitItem()) }`
- `runTest` from `kotlinx.coroutines.test` for suspend functions
- A ViewModel method that launches on `Dispatchers.IO` explicitly isn't touched by
  `MainDispatcherExtension` (which only swaps `Dispatchers.Main`) — poll with a bounded
  real-time `awaitCondition { ... }` helper instead of asserting immediately.
- Real delays never resolve under a test dispatcher, so tests remove them: Gin's opponent pacing is
  injected (`GinPacing`; `testGinViewModel()` passes `GinPacing.Instant`, and `settle()` lands every
  motion in flight), and Solitaire's new-deal shuffle is set per instance
  (`SolitaireViewModel.shuffleMillis = 0`); a test's `play` helper lands chained motions (a new
  deal's gather, then the deal).
- Write test hands as text — `cards("A♣ 2♣ 3♣ K♠")`, `card("10♥")` — and build Gin positions with
  `ginPosition(hand, …)` (`:core:testing`) rather than by hand.
- JVM unit tests in Android modules return default values from the stub `android.jar`
  (`isReturnDefaultValues`), so an error path's `Log.e` needs no `mockkStatic`.
- A Robolectric test that taps or reads layout renders at a real size —
  `@Config(qualifiers = TestDevices.PHONE)` (`:core:testing-ui`); Robolectric's default screen is
  too small and content measures as "not displayed".
- Screenshot tests freeze the clock (`mainClock.autoAdvance = false`) and advance a fixed time, so
  animations land on the same frame every run. To capture one test at every screen size, run it with
  `ParameterizedRobolectricTestRunner` over `ScreenSize.entries` and pass `snapshot(size = …)` (example
  in `TestDevices.kt`). Robolectric's text measurement differs from a real
  device, so check layout-sensitive text on a phone too.
- **Upgrades keep players' data.** Every database version's schema is exported to `core/data/schemas`
  (Room's Gradle plugin) and `DatabaseMigrationTest` migrates each one on real SQLite; the app opens the
  database only through `CardGamesDatabase.open`, whose destructive fallback is for downgrades only — a
  missing migration fails the tests instead of wiping saves and stats. `UpgradeFromReleaseTest` opens each
  release's real stored data (`core/data/src/test/resources/upgrade/<version>/`) with today's code. A new
  database version needs its migration (added to `MIGRATIONS`) and `DatabaseMigrationTest.CURRENT` bumped;
  **each release**, write its fixture and add its checks:
  `./gradlew :core:data:testDebugUnitTest --tests "*UpgradeFixtureWriter" -PwriteUpgradeFixture=<version>`.
- **Scenarios** (Dev build only, `app/src/debug/java/com/cardgames/debug/Scenarios.kt`): saved positions just before a
  moment worth checking on the device (Complete, Solved, gin, a knock, match won/lost, a blocked hand),
  run from the "Parlour Scenarios" launcher icon or
  `adb shell am start -n com.cardgames.dev/com.cardgames.debug.ScenarioActivity --es scenario <id>`.
  A new game's notable moments get scenarios too; `ScenariosTest` keeps each a whole, valid position.
  The list is in `docs/TEST_PLAN.md`.
- Kover per-module `minBound` gates are a **regression floor, not a target** — set just below
  currently-measured coverage so an accidental drop fails CI without forcing coverage-chasing
  busywork. Don't ratchet a gate upward without a reason.
