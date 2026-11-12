package com.cardgames.core.testing

import com.cardgames.core.domain.theme.ThemePack
import com.cardgames.core.domain.theme.ThemePackColors
import com.cardgames.core.domain.theme.ThemeRepository

/** In-memory [ThemeRepository]; assign [packs] to simulate installed packs. */
class FakeThemeRepository(
    var packs: List<ThemePack> = emptyList(),
) : ThemeRepository {
    override suspend fun availablePacks(): List<ThemePack> = packs
}

/** A structurally-complete pack for tests that only care about identity, not real assets. */
fun testThemePack(id: String): ThemePack =
    ThemePack(
        id = id,
        displayName = id,
        colors =
            ThemePackColors(
                tableLight = 0xFF000000L,
                table = 0xFFFFFFFFL,
                tableDark = 0xFF000000L,
                onTable = 0xFFFFFFFFL,
                onTableMuted = 0xFF000000L,
                chipOnTable = 0xFFFFFFFFL,
                lineOnTable = 0xFF000000L,
                scrim = 0xFFFFFFFFL,
                panel = 0xFF000000L,
                panelLine = 0xFFFFFFFFL,
                onPanel = 0xFF000000L,
                onPanelMuted = 0xFFFFFFFFL,
                cardFace = 0xFF000000L,
                suitRed = 0xFF000000L,
                suitBlack = 0xFFFFFFFFL,
                cardBack = 0xFF000000L,
                cardBackLattice = 0xFFFFFFFFL,
                cardBackBorder = 0xFF000000L,
                accent = 0xFFFFFFFFL,
                onAccent = 0xFF000000L,
                accentDeep = 0xFFFFFFFFL,
                action = 0xFF000000L,
                onAction = 0xFFFFFFFFL,
                hint = 0xFF000000L,
                meld = listOf(0xFF00FF00L, 0xFFFF00FFL, 0xFFFFFF00L),
                good = 0xFF000000L,
                goodBg = 0xFFFFFFFFL,
                bad = 0xFF000000L,
                badBg = 0xFFFFFFFFL,
                opponent = 0xFF000000L,
                opponentScore = 0xFFFFFFFFL,
            ),
        cardBackPath = "themes/$id/card_back.png",
        facePaths = emptyMap(),
    )
