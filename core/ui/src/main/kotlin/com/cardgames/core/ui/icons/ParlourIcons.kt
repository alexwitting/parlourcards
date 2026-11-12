package com.cardgames.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's stroke icons, in the Lucide style the design uses: a 24-unit grid, 2-unit round
 * strokes, no fills. Path data from Lucide (ISC licence, `res/raw/license_lucide.txt`). Draw them
 * with `Icon(…, tint = …)` at 24dp (20dp inside small tool buttons).
 */
object ParlourIcons {
    val Back: ImageVector by lazy { icon("Back", "m12 19-7-7 7-7", "M19 12H5") }

    val ChevronLeft: ImageVector by lazy { icon("ChevronLeft", "m15 18-6-6 6-6") }

    val ChevronRight: ImageVector by lazy { icon("ChevronRight", "m9 18 6-6-6-6") }

    val Close: ImageVector by lazy { icon("Close", "M18 6 6 18", "m6 6 12 12") }

    val Pause: ImageVector by lazy {
        icon(
            "Pause",
            "M7 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
            "M15 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
        )
    }

    val Play: ImageVector by lazy { icon("Play", "M6 3l14 9-14 9z") }

    val Sliders: ImageVector by lazy {
        icon(
            "Sliders",
            "M21 4h-7",
            "M10 4H3",
            "M21 12h-9",
            "M8 12H3",
            "M21 20h-5",
            "M12 20H3",
            "M14 2v4",
            "M8 10v4",
            "M16 18v4",
        )
    }

    val Undo: ImageVector by lazy {
        icon("Undo", "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11")
    }

    val Redo: ImageVector by lazy {
        icon("Redo", "m15 14 5-5-5-5", "M20 9H9.5A5.5 5.5 0 0 0 4 14.5A5.5 5.5 0 0 0 9.5 20H13")
    }

    val Lightbulb: ImageVector by lazy {
        icon(
            "Lightbulb",
            "M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5",
            "M9 18h6",
            "M10 22h4",
        )
    }

    val Shuffle: ImageVector by lazy {
        icon(
            "Shuffle",
            "m18 14 4 4-4 4",
            "m18 2 4 4-4 4",
            "M2 18h1.4c1.3 0 2.5-.6 3.3-1.7l6.1-8.6c.7-1.1 2-1.7 3.3-1.7H22",
            "M2 6h1.9c1.5 0 2.9.9 3.6 2.2",
            "M22 18h-5.9c-1.3 0-2.6-.7-3.3-1.8l-.5-.8",
        )
    }

    val Restart: ImageVector by lazy {
        icon("Restart", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5")
    }

    val Book: ImageVector by lazy {
        icon(
            "Book",
            "M12 7v14",
            "M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13" +
                "a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z",
        )
    }

    val Exit: ImageVector by lazy { icon("Exit", "m16 17 5-5-5-5", "M21 12H9", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4") }

    val Stats: ImageVector by lazy { icon("Stats", "M3 3v16a2 2 0 0 0 2 2h16", "M18 17V9", "M13 17V5", "M8 17v-3") }

    val Check: ImageVector by lazy { icon("Check", "M20 6 9 17l-5-5") }

    val Plus: ImageVector by lazy { icon("Plus", "M5 12h14", "M12 5v14") }

    val Home: ImageVector by lazy {
        icon(
            "Home",
            "M15 21v-8a1 1 0 0 0-1-1h-2a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }

    val Trophy: ImageVector by lazy {
        icon(
            "Trophy",
            "M6 9H4.5a2.5 2.5 0 0 1 0-5H6",
            "M18 9h1.5a2.5 2.5 0 0 0 0-5H18",
            "M4 22h16",
            "M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22",
            "M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22",
            "M18 2H6v7a6 6 0 0 0 12 0V2Z",
        )
    }

    /** Every icon by name, for the design-system catalogue. */
    val All: List<Pair<String, ImageVector>> by lazy {
        listOf(
            "Back" to Back,
            "ChevronLeft" to ChevronLeft,
            "ChevronRight" to ChevronRight,
            "Close" to Close,
            "Pause" to Pause,
            "Play" to Play,
            "Sliders" to Sliders,
            "Undo" to Undo,
            "Redo" to Redo,
            "Lightbulb" to Lightbulb,
            "Shuffle" to Shuffle,
            "Restart" to Restart,
            "Book" to Book,
            "Exit" to Exit,
            "Stats" to Stats,
            "Check" to Check,
            "Plus" to Plus,
            "Home" to Home,
            "Trophy" to Trophy,
        )
    }
}

private const val GRID = 24f
private const val STROKE = 2f

private fun icon(
    name: String,
    vararg paths: String,
): ImageVector =
    ImageVector
        .Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = GRID, viewportHeight = GRID)
        .apply {
            for (path in paths) {
                addPath(
                    pathData = addPathNodes(path),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
