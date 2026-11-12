package com.cardgames.core.data.theme

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeThemeAssetSource : ThemeAssetSource {
    val packIds = mutableListOf<String>()
    val texts = mutableMapOf<String, String>()
    val images = mutableMapOf<String, Pair<Int, Int>>()

    override fun listPackIds(): List<String> = packIds

    override fun list(
        packId: String,
        subPath: String,
    ): List<String> {
        val prefix =
            listOf("themes", packId, subPath)
                .filter { it.isNotEmpty() }
                .joinToString("/") + "/"
        return (texts.keys + images.keys)
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .filter { "/" !in it }
    }

    override fun readText(path: String): String? = texts[path]

    override fun imageSize(path: String): Pair<Int, Int>? = images[path]

    /** A complete schema-2 pack: manifest, card back and 52 faces. [colorOverrides] replace single color entries. */
    fun addPack(
        id: String,
        facesFrom: String? = null,
        manifestId: String = id,
        schemaVersion: Int = 2,
        colorOverrides: Map<String, String> = emptyMap(),
        meld: String = """["#8FB8A0", "#C98A8A", "#C9A45C"]""",
    ) {
        packIds += id
        val colors = (BASE_COLORS + colorOverrides).entries.joinToString(",\n") { (k, v) -> "    \"$k\": \"$v\"" }
        texts["themes/$id/manifest.json"] =
            """
            {
              "schemaVersion": $schemaVersion,
              "id": "$manifestId",
              "displayName": "Pack $id",
              "colors": {
            $colors,
                "meld": $meld
              }${if (facesFrom != null) ",\n  \"facesFrom\": \"$facesFrom\"" else ""}
            }
            """.trimIndent()
        images["themes/$id/card_back.png"] = 750 to 1050
        if (facesFrom == null) addFaces(id, "faces")
    }

    fun addFaces(
        id: String,
        dir: String,
    ) {
        for (suit in Suit.entries) {
            for (rank in 1..13) {
                images["themes/$id/$dir/${suit.name.lowercase()}_%02d.png".format(rank)] = 750 to 1050
            }
        }
    }

    companion object {
        val BASE_COLORS =
            linkedMapOf(
                "tableLight" to "#2A5E48",
                "table" to "#214F3C",
                "tableDark" to "#183D2E",
                "onTable" to "#F3ECDC",
                "onTableMuted" to "#B3F3ECDC",
                "chipOnTable" to "#38081610",
                "lineOnTable" to "#2EF3ECDC",
                "scrim" to "#8C081610",
                "panel" to "#F3ECDC",
                "panelLine" to "#CFC4A8",
                "onPanel" to "#1C3A2D",
                "onPanelMuted" to "#6E7466",
                "cardFace" to "#FBF7EE",
                "suitRed" to "#A8322A",
                "suitBlack" to "#23262B",
                "cardBack" to "#7A2431",
                "cardBackLattice" to "#38FBF7EE",
                "cardBackBorder" to "#FBF7EE",
                "accent" to "#C9A45C",
                "onAccent" to "#1C3A2D",
                "accentDeep" to "#8C6E33",
                "action" to "#1C3A2D",
                "onAction" to "#F3ECDC",
                "hint" to "#8FB8A0",
                "good" to "#3E7A5C",
                "goodBg" to "#E1EDE4",
                "bad" to "#8E2F2A",
                "badBg" to "#F2DCD8",
                "opponent" to "#8E3A44",
                "opponentScore" to "#E0B4B4",
            )
    }
}

class ThemePackLoaderTest {
    private val source = FakeThemeAssetSource()
    private val loader = ThemePackLoader(source)

    @Test
    fun `a complete pack loads with parsed colors and all 52 face paths`() {
        source.addPack("parlour")

        val pack = loader.loadValidPacks().single()

        assertEquals("parlour", pack.id)
        assertEquals("Pack parlour", pack.displayName)
        assertEquals(0xFFF3ECDCL, pack.colors.onTable)
        assertEquals(listOf(0xFF8FB8A0L, 0xFFC98A8AL, 0xFFC9A45CL), pack.colors.meld)
        assertEquals(52, pack.facePaths.size)
        assertEquals("themes/parlour/faces/spades_01.png", pack.facePaths.getValue(Card(Suit.SPADES, Rank.ACE)))
    }

    @Test
    fun `an eight-digit color keeps its alpha`() {
        source.addPack("parlour")

        assertEquals(
            0xB3F3ECDCL,
            loader
                .loadValidPacks()
                .single()
                .colors.onTableMuted,
        )
    }

    @Test
    fun `table art and small faces are optional and absent by default`() {
        source.addPack("parlour")

        val pack = loader.loadValidPacks().single()

        assertNull(pack.tableBackgroundPath)
        assertNull(pack.smallFacePaths)
    }

    @Test
    fun `present, correctly sized table art resolves to its path`() {
        source.addPack("parlour")
        source.images["themes/parlour/table_bg.webp"] = 1440 to 3200

        val pack = loader.loadValidPacks().single()

        assertEquals("themes/parlour/table_bg.webp", pack.tableBackgroundPath)
    }

    @Test
    fun `a wrongly sized optional image is dropped, not the whole pack`() {
        source.addPack("parlour")
        source.images["themes/parlour/table_bg.webp"] = 1080 to 1920

        val pack = loader.loadValidPacks().single()

        assertNull(pack.tableBackgroundPath)
    }

    @Test
    fun `a complete pip-less small face set is used, an incomplete one ignored`() {
        source.addPack("complete")
        source.addFaces("complete", "faces_small")
        source.addPack("partial")
        source.addFaces("partial", "faces_small")
        source.images.remove("themes/partial/faces_small/hearts_05.png")

        val packs = loader.loadValidPacks().associateBy { it.id }

        assertEquals(
            "themes/complete/faces_small/hearts_05.png",
            packs.getValue("complete").smallFacePaths?.getValue(Card(Suit.HEARTS, Rank.FIVE)),
        )
        assertNull(packs.getValue("partial").smallFacePaths)
    }

    @Test
    fun `facesFrom reuses the donor's face files`() {
        source.addPack("parlour")
        source.addPack("royal_navy", facesFrom = "parlour")

        val packs = loader.loadValidPacks()

        assertEquals(listOf("parlour", "royal_navy"), packs.map { it.id })
        assertEquals(
            "themes/parlour/faces/hearts_13.png",
            packs.last().facePaths.getValue(Card(Suit.HEARTS, Rank.KING)),
        )
    }

    @Test
    fun `facesFrom chains are rejected`() {
        source.addPack("base")
        source.addPack("middle", facesFrom = "base")
        source.addPack("leaf", facesFrom = "middle")

        assertEquals(listOf("base", "middle"), loader.loadValidPacks().map { it.id })
    }

    @Test
    fun `facesFrom pointing at a missing donor is rejected`() {
        source.addPack("orphan", facesFrom = "nowhere")

        assertTrue(loader.loadValidPacks().isEmpty())
    }

    @Test
    fun `an incomplete face set is rejected`() {
        source.addPack("holey")
        source.images.remove("themes/holey/faces/clubs_07.png")

        assertTrue(loader.loadValidPacks().isEmpty())
    }

    @Test
    fun `a wrongly sized card back is rejected`() {
        source.addPack("stretched")
        source.images["themes/stretched/card_back.png"] = 700 to 1000

        assertTrue(loader.loadValidPacks().isEmpty())
    }

    @Test
    fun `a manifest id that disagrees with its folder is rejected`() {
        source.addPack("folder_name", manifestId = "different_id")

        assertTrue(loader.loadValidPacks().isEmpty())
    }

    @Test
    fun `a schema-1 manifest is rejected`() {
        source.addPack("old", schemaVersion = 1)

        assertTrue(loader.loadValidPacks().isEmpty())
    }

    @Test
    fun `an invalid color string is rejected`() {
        source.addPack("weird_colors", colorOverrides = mapOf("accent" to "gold"))

        assertTrue(loader.loadValidPacks().isEmpty())
    }

    @Test
    fun `a meld list that isn't three colors is rejected`() {
        source.addPack("two_melds", meld = """["#8FB8A0", "#C98A8A"]""")

        assertTrue(loader.loadValidPacks().isEmpty())
    }

    @Test
    fun `webp files satisfy the same slots as png`() {
        source.addPack("webby")
        source.images.remove("themes/webby/card_back.png")
        source.images["themes/webby/card_back.webp"] = 750 to 1050

        assertEquals("themes/webby/card_back.webp", loader.loadValidPacks().single().cardBackPath)
    }

    @Test
    fun `one broken pack does not take valid ones down`() {
        source.addPack("good")
        source.addPack("broken")
        source.images.remove("themes/broken/card_back.png")

        assertEquals(listOf("good"), loader.loadValidPacks().map { it.id })
    }
}
