package com.cardgames.core.ui.cards

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.domain.cards.Rank
import com.cardgames.core.domain.cards.Suit
import com.cardgames.core.ui.theme.ParlourTheme
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Not a test: the opt-in render task that writes the shipped theme packs from [ParlourTheme] and
 * the card art in [CardFaceArt]/[CardBackArt] — each pack's manifest, card back, faces and pip-less
 * small faces (docs/THEME_SPEC.md). Skipped unless an output folder is given:
 *
 * ```
 * ./gradlew :core:ui:testDebugUnitTest --tests "*ThemePackRenderTest" \
 *     -PrenderThemePacksTo=$PWD/app/src/main/assets/themes
 * ```
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemePackRenderTest {
    private val density = Density(1f)
    private val textMeasurer =
        TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), density, LayoutDirection.Ltr)

    @Test
    fun renderThemePacks() {
        val outRoot = System.getProperty(OUT_PROPERTY)?.takeIf { it.isNotBlank() }
        assumeTrue("Pass -PrenderThemePacksTo=<dir> to render the theme packs", outRoot != null)

        for (pack in ParlourTheme.entries) {
            val dir = File(outRoot, pack.id)
            dir.deleteRecursively()
            render(File(dir, "card_back.png")) { drawCardBack(pack.colors) }
            for (suit in Suit.entries) {
                for (rank in Rank.entries) {
                    val card = Card(suit, rank)
                    val name = "${suit.name.lowercase()}_%02d.png".format(rank.ordinal + 1)
                    render(File(dir, "faces/$name")) { drawCardFace(card, small = false, pack.colors, textMeasurer) }
                    render(File(dir, "faces_small/$name")) { drawCardFace(card, small = true, pack.colors, textMeasurer) }
                }
            }
            File(dir, "manifest.json").writeText(manifest(pack))
        }
    }

    /** Draws a card-sized image with [draw] and writes it to [to] as an opaque PNG. */
    private fun render(
        to: File,
        draw: DrawScope.() -> Unit,
    ) {
        val bitmap = ImageBitmap(CARD_WIDTH_PX, CARD_HEIGHT_PX)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(CARD_WIDTH_PX.toFloat(), CARD_HEIGHT_PX.toFloat()), draw)
        val pixels = IntArray(CARD_WIDTH_PX * CARD_HEIGHT_PX)
        bitmap.asAndroidBitmap().getPixels(pixels, 0, CARD_WIDTH_PX, 0, 0, CARD_WIDTH_PX, CARD_HEIGHT_PX)
        val image = BufferedImage(CARD_WIDTH_PX, CARD_HEIGHT_PX, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, CARD_WIDTH_PX, CARD_HEIGHT_PX, pixels, 0, CARD_WIDTH_PX)
        to.parentFile.mkdirs()
        ImageIO.write(image, "png", to)
    }

    private fun manifest(pack: ParlourTheme): String {
        val c = pack.colors
        val roles = c.manifestRoles()
        val colorLines = roles.joinToString(",\n") { (name, color) -> "    \"$name\": \"${color.hex()}\"" }
        val meld = c.meld.joinToString(", ") { "\"${it.hex()}\"" }
        return """
            |{
            |  "schemaVersion": 2,
            |  "id": "${pack.id}",
            |  "displayName": "${pack.displayName}",
            |  "colors": {
            |$colorLines,
            |    "meld": [$meld]
            |  }
            |}
            |
            """.trimMargin()
    }

    /** `#RRGGBB` when opaque, else `#AARRGGBB`. */
    private fun Color.hex(): String {
        val argb = toArgb().toLong() and 0xFFFFFFFFL
        return if (argb ushr 24 == 0xFFL) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)
    }

    private companion object {
        const val OUT_PROPERTY = "cardgames.renderThemePacksTo"
        const val CARD_WIDTH_PX = 750
        const val CARD_HEIGHT_PX = 1050
    }
}

/** Every single-colour manifest role of these colors, by its manifest key. */
private fun CardTableColors.manifestRoles(): List<Pair<String, Color>> =
    listOf(
        "tableLight" to tableLight,
        "table" to table,
        "tableDark" to tableDark,
        "onTable" to onTable,
        "onTableMuted" to onTableMuted,
        "chipOnTable" to chipOnTable,
        "lineOnTable" to lineOnTable,
        "scrim" to scrim,
        "panel" to panel,
        "panelLine" to panelLine,
        "onPanel" to onPanel,
        "onPanelMuted" to onPanelMuted,
        "cardFace" to cardFace,
        "suitRed" to suitRed,
        "suitBlack" to suitBlack,
        "cardBack" to cardBack,
        "cardBackLattice" to cardBackLattice,
        "cardBackBorder" to cardBackBorder,
        "accent" to accent,
        "onAccent" to onAccent,
        "accentDeep" to accentDeep,
        "action" to action,
        "onAction" to onAction,
        "hint" to hint,
        "good" to good,
        "goodBg" to goodBg,
        "bad" to bad,
        "badBg" to badBg,
        "opponent" to opponent,
        "opponentScore" to opponentScore,
        "chipOnTableSoft" to chipOnTableSoft,
        "surfaceOnTable" to surfaceOnTable,
        "feedPill" to feedPill,
        "feedEmphasis" to feedEmphasis,
        "accentOnTable" to accentOnTable,
        "deepChip" to deepChip,
        "toolTile" to toolTile,
        "panelSoft" to panelSoft,
        "onPanelDisabled" to onPanelDisabled,
        "meterOver" to meterOver,
        "actionBase" to actionBase,
    )
