package com.cardgames.core.ui.chrome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The main action: brass, 60dp, a Spectral 800 label with an optional [sublabel] under it
 * ("Throw 8♥ and go out"), standing on a solid base shadow. Disabled, it goes flat — a tile-coloured
 * slot with faded text and no base.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sublabel: String? = null,
) {
    val colors = CardGamesTheme.colors
    // Turning on or off cross-fades rather than snapping (Solitaire's Complete, Gin's Knock).
    val fill by animateColorAsState(if (enabled) colors.action else colors.toolTile, CardGamesMotion.Base.tween(), label = "fill")
    val ink by animateColorAsState(if (enabled) colors.onAction else colors.onPanelDisabled, CardGamesMotion.Base.tween(), label = "ink")
    ChromeButton(
        text = text,
        sublabel = sublabel,
        onClick = onClick,
        enabled = enabled,
        fill = fill,
        ink = ink,
        base = colors.actionBase.takeIf { enabled },
        modifier = modifier,
    )
}

/** The alternative beside a [PrimaryButton] ("Keep playing", "Pass"): a tile-coloured button, no base. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sublabel: String? = null,
) {
    val colors = CardGamesTheme.colors
    ChromeButton(
        text = text,
        sublabel = sublabel,
        onClick = onClick,
        enabled = enabled,
        fill = colors.toolTile,
        ink = if (enabled) colors.onPanel else colors.onPanelDisabled,
        base = null,
        modifier = modifier,
    )
}

/** The main action on the felt (a result screen's "Deal again", "Next hand"): the same brass button. */
@Composable
fun TablePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
) {
    PrimaryButton(text = text, onClick = onClick, modifier = modifier, sublabel = sublabel)
}

/** The quieter action on the felt ("Back to games", "Home"): a soft chip, 52dp, small bold label. */
@Composable
fun TableSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Box(
        modifier =
            modifier
                .clip(ChromeShapes.Tile)
                .background(colors.chipOnTableSoft)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = chromeMetrics.secondaryButtonHeight)
                .padding(horizontal = CardGamesTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.onTable, style = CardGamesTheme.type.pill)
    }
}

/** A quiet link-like action on the felt ("All matches"): a hairline outline, no fill. */
@Composable
fun TableOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Box(
        modifier =
            modifier
                .clip(ChromeShapes.Icon)
                .border(1.dp, colors.lineOnTable, ChromeShapes.Icon)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = OUTLINE_BUTTON_HEIGHT)
                .padding(horizontal = CardGamesTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.onTable, style = CardGamesTheme.type.pill)
    }
}

@Composable
private fun ChromeButton(
    text: String,
    sublabel: String?,
    onClick: () -> Unit,
    enabled: Boolean,
    fill: Color,
    ink: Color,
    base: Color?,
    modifier: Modifier,
) {
    // In the bottom panel the button matches the 48dp tool tiles beside it; elsewhere it's the full 60dp.
    val compact = LocalCompactButtons.current
    val shape = if (compact) ChromeShapes.ToolTile else ChromeShapes.Button
    val baseDepth = if (compact) COMPACT_BUTTON_BASE else BUTTON_BASE
    Box(
        modifier =
            modifier
                .padding(bottom = if (base != null) baseDepth else 0.dp)
                .drawBehind {
                    // The solid base: the button's own shape, set down a few dp in a darker brass.
                    if (base != null) {
                        val radius = (if (compact) COMPACT_BUTTON_RADIUS else BUTTON_RADIUS).toPx()
                        drawRoundRect(base, topLeft = Offset(0f, baseDepth.toPx()), size = size, cornerRadius = CornerRadius(radius))
                    }
                }.clip(shape)
                .background(fill)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .heightIn(min = if (compact) chromeMetrics.compactButtonHeight else chromeMetrics.buttonHeight)
                // Tighter in the panel, where it shares the row with the tools.
                .padding(horizontal = if (compact) CardGamesTheme.spacing.sm else CardGamesTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Shrinks to fit rather than cutting off — a narrow phone fits a long label beside the tools.
            val style = if (compact) CardGamesTheme.type.panelAction else CardGamesTheme.type.actionLabel
            BasicText(
                text = text,
                style = style.copy(color = ink),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = style.fontSize),
            )
            val subStyle = if (compact) CardGamesTheme.type.toolLabel else CardGamesTheme.type.actionSub
            if (sublabel != null) Text(sublabel, color = ink, style = subStyle, maxLines = 1)
        }
    }
}

/**
 * A square action on the panel (Undo, Hint, New): a 48dp tile holding the icon, its label below,
 * and an optional [badge] count on the tile's corner (hints left).
 */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: Int? = null,
) {
    val colors = CardGamesTheme.colors
    val metrics = chromeMetrics
    val shown by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, CardGamesMotion.StatusFade.tween(), label = "tool")
    Column(
        modifier =
            modifier
                .width(metrics.toolWidth)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = label,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TOOL_LABEL_GAP),
    ) {
        // Each part fades in its own layer: one layer round the whole button would clip the badge
        // where it overhangs the tile's corner whenever the button wasn't fully opaque.
        val fade = Modifier.graphicsLayer { alpha = shown }
        Box {
            Box(
                modifier = fade.size(metrics.toolTile).background(colors.toolTile, RoundedCornerShape(metrics.toolTileRadius)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.onPanel, modifier = Modifier.size(TOOL_ICON))
            }
            if (badge != null) {
                CountBadge(
                    count = badge,
                    fill = colors.opponent,
                    ink = colors.panel,
                    size = TOOL_BADGE,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).then(fade),
                )
            }
        }
        Text(label, color = colors.onPanel, style = CardGamesTheme.type.toolLabel, maxLines = 1, modifier = fade)
    }
}

/** A filled icon button on the felt (top bar: pause, settings, back), [ChromeMetrics.topBarButton] across; 48dp touch area. */
@Composable
fun TableIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardGamesTheme.colors
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .size(chromeMetrics.topBarButton)
                .clip(ChromeShapes.Icon)
                .background(colors.chipOnTableSoft)
                .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.onTable, modifier = Modifier.size(TABLE_ICON))
    }
}

private val OUTLINE_BUTTON_HEIGHT = 46.dp
private const val DISABLED_ALPHA = 0.45f

/** How small a button label may shrink to fit — low enough for "Complete" beside the tools at the largest table text. */
private val MIN_LABEL_SIZE = 12.sp
private val BUTTON_RADIUS: Dp = 20.dp
private val BUTTON_BASE: Dp = 5.dp
private val COMPACT_BUTTON_BASE: Dp = 4.dp
private val COMPACT_BUTTON_RADIUS: Dp = 16.dp
private val TOOL_ICON: Dp = 20.dp
private val TOOL_BADGE: Dp = 20.dp
private val TABLE_ICON: Dp = 20.dp
