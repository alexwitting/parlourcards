package com.cardgames.core.data.theme

import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.domain.theme.ThemeChromeColors
import com.cardgames.core.domain.theme.ThemePack
import com.cardgames.core.domain.theme.ThemePackColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How the loader sees the `assets/themes/` tree — an interface so validation logic stays
 * pure/unit-testable; the Android adapter wraps AssetManager + BitmapFactory.
 */
internal interface ThemeAssetSource {
    /** Candidate pack ids: the child folders of `themes/`. */
    fun listPackIds(): List<String>

    /** File names directly inside `themes/<packId>/<subPath>`; empty if the folder is absent. */
    fun list(
        packId: String,
        subPath: String = "",
    ): List<String>

    /** Contents of an asset-relative path, or null if absent/unreadable. */
    fun readText(path: String): String?

    /** Pixel (width, height) of an image without decoding it fully; null if absent/not an image. */
    fun imageSize(path: String): Pair<Int, Int>?
}

/** Schema-2 manifest colors — the Parlour roles, as `#RRGGBB` or `#AARRGGBB` strings. */
@Serializable
internal data class ThemeManifestColors(
    val tableLight: String,
    val table: String,
    val tableDark: String,
    val onTable: String,
    val onTableMuted: String,
    val chipOnTable: String,
    val lineOnTable: String,
    val scrim: String,
    val panel: String,
    val panelLine: String,
    val onPanel: String,
    val onPanelMuted: String,
    val cardFace: String,
    val suitRed: String,
    val suitBlack: String,
    val cardBack: String,
    val cardBackLattice: String,
    val cardBackBorder: String,
    val accent: String,
    val onAccent: String,
    val accentDeep: String,
    val action: String,
    val onAction: String,
    val hint: String,
    val meld: List<String>,
    val good: String,
    val goodBg: String,
    val bad: String,
    val badBg: String,
    val opponent: String,
    val opponentScore: String,
    val chipOnTableSoft: String? = null,
    val surfaceOnTable: String? = null,
    val feedPill: String? = null,
    val feedEmphasis: String? = null,
    val accentOnTable: String? = null,
    val deepChip: String? = null,
    val toolTile: String? = null,
    val panelSoft: String? = null,
    val onPanelDisabled: String? = null,
    val meterOver: String? = null,
    val actionBase: String? = null,
)

@Serializable
internal data class ThemeManifest(
    val schemaVersion: Int,
    val id: String,
    val displayName: String,
    val colors: ThemeManifestColors,
    val facesFrom: String? = null,
)

/**
 * Enumerates `themes/` and returns only the packs that pass the structural checks in
 * docs/THEME_SPEC.md: a schema-2 manifest whose colors all parse, a card back at the spec size,
 * and a complete face set (own `faces/` XOR a valid un-chained `facesFrom` donor). The preview,
 * table art, home splash and pip-less small faces are optional — a missing or wrongly sized one
 * just isn't used. Invalid packs are dropped silently — the caller falls back to the default pack.
 */
internal class ThemePackLoader(
    private val source: ThemeAssetSource,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadValidPacks(): List<ThemePack> {
        val manifests =
            source
                .listPackIds()
                .sorted()
                .mapNotNull { packId -> readManifest(packId)?.takeIf { it.id == packId } }
        val ownFaceOwners =
            manifests
                .filter { it.facesFrom == null && hasCompleteFaces(it.id, FACES_DIR) }
                .map { it.id }
                .toSet()
        return manifests.mapNotNull { manifest -> validate(manifest, ownFaceOwners) }
    }

    private fun readManifest(packId: String): ThemeManifest? =
        source.readText("themes/$packId/manifest.json")?.let { raw ->
            runCatching { json.decodeFromString(ThemeManifest.serializer(), raw) }
                .getOrNull()
                ?.takeIf { it.schemaVersion == SUPPORTED_SCHEMA_VERSION }
        }

    private fun validate(
        manifest: ThemeManifest,
        ownFaceOwners: Set<String>,
    ): ThemePack? {
        val packId = manifest.id
        val colors = manifest.colors.toDomain() ?: return null
        val cardBack = findImage(packId, "card_back", CARD_SIZE) ?: return null
        val faceOwner = resolveFacesOwner(manifest, ownFaceOwners) ?: return null
        val facePaths = facePaths(faceOwner, FACES_DIR) ?: return null
        return ThemePack(
            id = packId,
            displayName = manifest.displayName,
            colors = colors,
            cardBackPath = cardBack,
            facePaths = facePaths,
            // Optional: absent or wrong-sized just means that asset isn't used, unlike the card
            // back and faces above, which invalidate the whole pack on a mismatch.
            smallFacePaths = facePaths(faceOwner, SMALL_FACES_DIR),
            tableBackgroundPath = findImage(packId, "table_bg", TABLE_SIZE),
        )
    }

    /** Exactly one of own faces / facesFrom; donors must own faces, no chains. */
    private fun resolveFacesOwner(
        manifest: ThemeManifest,
        ownFaceOwners: Set<String>,
    ): String? =
        when {
            manifest.facesFrom == null -> manifest.id.takeIf { it in ownFaceOwners }
            else -> manifest.facesFrom.takeIf { it in ownFaceOwners && manifest.id !in ownFaceOwners }
        }

    /** Complete = all 52 files named per the spec exist in [dir] (png or webp). */
    private fun hasCompleteFaces(
        packId: String,
        dir: String,
    ): Boolean {
        val files = source.list(packId, dir).toSet()
        return allFaceNames().all { base -> EXTENSIONS.any { "$base.$it" in files } }
    }

    /** All 52 face paths in [dir], each at the spec size — or null if any is missing or wrongly sized. */
    private fun facePaths(
        ownerId: String,
        dir: String,
    ): Map<Card, String>? {
        val files = source.list(ownerId, dir).toSet()
        val paths = mutableMapOf<Card, String>()
        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
                val base = faceBaseName(suit, rank)
                val file = EXTENSIONS.map { "$base.$it" }.firstOrNull { it in files } ?: return null
                val path = "themes/$ownerId/$dir/$file"
                if (source.imageSize(path) != CARD_SIZE) return null
                paths[Card(suit, rank)] = path
            }
        }
        return paths
    }

    /**
     * The path of `themes/<packId>/<name>.(webp|png)` if it exists at exactly [expectedSize], or
     * null otherwise — whether a null here invalidates the whole pack (required assets) or just
     * that one optional asset is the caller's call ([validate]), not this function's.
     */
    private fun findImage(
        packId: String,
        name: String,
        expectedSize: Pair<Int, Int>,
    ): String? =
        EXTENSIONS
            .map { "themes/$packId/$name.$it" }
            .firstOrNull { source.imageSize(it) == expectedSize }

    private fun allFaceNames(): List<String> = Suit.entries.flatMap { suit -> Rank.entries.map { rank -> faceBaseName(suit, rank) } }

    private fun faceBaseName(
        suit: Suit,
        rank: Rank,
    ): String = "${suit.name.lowercase()}_%02d".format(rank.ordinal + 1)

    /** Null when any color is malformed — the pack is then rejected. */
    private fun ThemeManifestColors.toDomain(): ThemePackColors? = runCatching { toDomainOrThrow() }.getOrNull()

    /** Throws [IllegalArgumentException] on the first malformed color, or a meld list that isn't exactly 3. */
    private fun ThemeManifestColors.toDomainOrThrow(): ThemePackColors {
        fun c(hex: String): Long = requireNotNull(parseHexColor(hex)) { "Malformed color $hex" }
        require(meld.size == MELD_COLOR_COUNT) { "meld needs exactly $MELD_COLOR_COUNT colors" }
        return ThemePackColors(
            tableLight = c(tableLight),
            table = c(table),
            tableDark = c(tableDark),
            onTable = c(onTable),
            onTableMuted = c(onTableMuted),
            chipOnTable = c(chipOnTable),
            lineOnTable = c(lineOnTable),
            scrim = c(scrim),
            panel = c(panel),
            panelLine = c(panelLine),
            onPanel = c(onPanel),
            onPanelMuted = c(onPanelMuted),
            cardFace = c(cardFace),
            suitRed = c(suitRed),
            suitBlack = c(suitBlack),
            cardBack = c(cardBack),
            cardBackLattice = c(cardBackLattice),
            cardBackBorder = c(cardBackBorder),
            accent = c(accent),
            onAccent = c(onAccent),
            accentDeep = c(accentDeep),
            action = c(action),
            onAction = c(onAction),
            hint = c(hint),
            meld = meld.map(::c),
            good = c(good),
            goodBg = c(goodBg),
            bad = c(bad),
            badBg = c(badBg),
            opponent = c(opponent),
            opponentScore = c(opponentScore),
            chrome =
                ThemeChromeColors(
                    chipOnTableSoft = chipOnTableSoft?.let(::c),
                    surfaceOnTable = surfaceOnTable?.let(::c),
                    feedPill = feedPill?.let(::c),
                    feedEmphasis = feedEmphasis?.let(::c),
                    accentOnTable = accentOnTable?.let(::c),
                    deepChip = deepChip?.let(::c),
                    toolTile = toolTile?.let(::c),
                    panelSoft = panelSoft?.let(::c),
                    onPanelDisabled = onPanelDisabled?.let(::c),
                    meterOver = meterOver?.let(::c),
                    actionBase = actionBase?.let(::c),
                ),
        )
    }

    /** `#RRGGBB` → opaque ARGB; `#AARRGGBB` → ARGB with that alpha. Anything else is invalid. */
    private fun parseHexColor(hex: String): Long? =
        when {
            HEX_RGB.matches(hex) -> OPAQUE_ALPHA or hex.drop(1).toLong(HEX_RADIX)
            HEX_ARGB.matches(hex) -> hex.drop(1).toLong(HEX_RADIX)
            else -> null
        }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 2
        const val FACES_DIR = "faces"
        const val SMALL_FACES_DIR = "faces_small"
        const val MELD_COLOR_COUNT = 3
        val CARD_SIZE = 750 to 1050
        val TABLE_SIZE = 1440 to 3200
        val EXTENSIONS = listOf("webp", "png")
        val HEX_RGB = Regex("^#[0-9a-fA-F]{6}$")
        val HEX_ARGB = Regex("^#[0-9a-fA-F]{8}$")
        const val OPAQUE_ALPHA = 0xFF000000L
        const val HEX_RADIX = 16
    }
}
