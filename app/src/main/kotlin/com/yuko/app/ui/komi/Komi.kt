/*
 * Componentes adaptados de Komi Store (kurikomi-labs/komi-store), Apache License 2.0:
 * KomiText, KomiHeadline, KomiSurface, KomiButton, KomiIconButton, KomiChip, KomiBadge,
 * KomiCheckbox, KomiTextField, KomiHorizontalDivider, KomiTopBar, KomiBottomBar, KomiScaffold.
 * Solo la rama «Manga» de cada componente; sin hover de escritorio, sin toasts, sin loading.
 */
package com.yuko.app.ui.komi

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.yuko.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---------------------------------------------------------------- texto

enum class KomiTextRole { Display, Title, Stamp, Label, Body, Mono }

private val KomiTextRole.isHeading: Boolean
    get() = this == KomiTextRole.Display || this == KomiTextRole.Title || this == KomiTextRole.Stamp || this == KomiTextRole.Label

private fun komiTextStyle(
    type: PersonalityType, role: KomiTextRole,
    fontSize: TextUnit, fontWeight: FontWeight?, letterSpacing: TextUnit, lineHeight: TextUnit,
): TextStyle {
    var style = when (role) {
        KomiTextRole.Display -> type.display
        KomiTextRole.Title -> type.title
        KomiTextRole.Stamp -> type.stamp
        KomiTextRole.Body -> type.body
        KomiTextRole.Label -> type.label
        KomiTextRole.Mono -> type.mono
    }
    if (fontSize.isSpecified) style = style.copy(fontSize = fontSize)
    if (fontWeight != null) style = style.copy(fontWeight = fontWeight)
    if (letterSpacing.isSpecified) style = style.copy(letterSpacing = letterSpacing)
    if (lineHeight.isSpecified) style = style.copy(lineHeight = lineHeight)
    return style
}

@Composable
fun KomiText(
    text: String,
    modifier: Modifier = Modifier,
    role: KomiTextRole = KomiTextRole.Body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    uppercase: Boolean? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val personality = LocalPersonality.current
    val style = komiTextStyle(personality.type, role, fontSize, fontWeight, letterSpacing, lineHeight)
    val resolvedUppercase = uppercase ?: (role.isHeading && personality.type.uppercaseHeadings)
    Text(
        text = if (resolvedUppercase) text.uppercase() else text,
        modifier = modifier,
        color = if (color != Color.Unspecified) color else personality.colors.onSurface,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        style = style,
    )
}

@Composable
fun KomiText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    role: KomiTextRole = KomiTextRole.Body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val personality = LocalPersonality.current
    val style = komiTextStyle(personality.type, role, fontSize, fontWeight, letterSpacing, lineHeight)
    Text(
        text = text,
        modifier = modifier,
        color = if (color != Color.Unspecified) color else personality.colors.onSurface,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        style = style,
    )
}

private val SkewStampShape = GenericShape { size, _ ->
    val skew = size.height * 0.30f
    moveTo(skew, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - skew, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
fun HeadlineStamp(fill: Color, border: Color) {
    Box(
        Modifier
            .size(width = 12.dp, height = 22.dp)
            .background(color = fill, shape = SkewStampShape)
            .border(width = 2.dp, color = border, shape = SkewStampShape),
    )
}

@Composable
private fun HeadlineSpeedLines(color: Color) {
    Canvas(Modifier.size(width = 24.dp, height = 24.dp)) {
        val count = 4
        val stroke = 3.dp.toPx()
        val rise = size.height * 0.3f
        val gap = (size.height - rise) / (count + 1)
        for (i in 1..count) {
            val y = rise + gap * i
            drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y - rise), strokeWidth = stroke, cap = StrokeCap.Square)
        }
    }
}

@Composable
fun KomiHeadline(
    text: String,
    modifier: Modifier = Modifier,
    role: KomiTextRole = KomiTextRole.Title,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    if (personality.headlineMarker == HeadlineMarker.None) {
        KomiText(text, modifier, role, color, maxLines, TextOverflow.Ellipsis)
        return
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (personality.headlineMarker) {
            HeadlineMarker.Stamp -> HeadlineStamp(fill = colors.primary, border = colors.outline)
            HeadlineMarker.SpeedLines -> HeadlineSpeedLines(color = colors.onSurface)
            HeadlineMarker.None -> Unit
        }
        KomiText(text = text, role = role, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- superficies

enum class KomiSurfaceElevation { Flat, Card, Raised, Modal }
enum class KomiSurfacePaper { Surface, Background }
enum class KomiScreentone { None, Corner, Fill }

private data class MangaElevation(val shadow: Dp, val border: Dp)

private fun mangaElevation(elevation: KomiSurfaceElevation): MangaElevation =
    when (elevation) {
        KomiSurfaceElevation.Flat -> MangaElevation(0.dp, 3.dp)
        KomiSurfaceElevation.Card -> MangaElevation(6.dp, 3.dp)
        KomiSurfaceElevation.Raised -> MangaElevation(10.dp, 3.dp)
        KomiSurfaceElevation.Modal -> MangaElevation(14.dp, 4.dp)
    }

@Composable
fun KomiSurface(
    modifier: Modifier = Modifier,
    elevation: KomiSurfaceElevation = KomiSurfaceElevation.Card,
    paper: KomiSurfacePaper = KomiSurfacePaper.Surface,
    screentone: KomiScreentone = KomiScreentone.None,
    screentoneBoost: Float = 1f,
    onClick: (() -> Unit)? = null,
    tilt: Float = 0f,
    topEdgeOnly: Boolean = false,
    borderColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val elev = mangaElevation(elevation)
    val borderWidth = if (topEdgeOnly) maxOf(elev.border, 4.dp) else elev.border
    val fill = if (paper == KomiSurfacePaper.Surface) colors.surface else colors.background
    val contentInk = if (paper == KomiSurfacePaper.Surface) colors.onSurface else colors.onBackground
    val line = if (borderColor != Color.Unspecified) borderColor else colors.outline
    val tiltModifier = if (tilt != 0f) Modifier.rotate(tilt) else Modifier
    val depthModifier =
        if (onClick != null) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            val pressProgress = animateFloatAsState(if (pressed) 1f else 0f, label = "komiSurfacePress")
            Modifier
                .inkFocusRing(focused = { focused }, color = colors.primary)
                .inkPress(
                    pressProgress = { pressProgress.value },
                    hoverProgress = { 0f },
                    shadow = DpOffset(elev.shadow, elev.shadow),
                    shadowColor = colors.shadow,
                    shape = RectangleShape,
                    pressInset = 2.dp,
                    hoverLift = 3.dp,
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        } else if (elev.shadow > 0.dp) {
            Modifier.hardShadow(offset = DpOffset(elev.shadow, elev.shadow), color = colors.shadow)
        } else {
            Modifier
        }
    val toneModifier = when (screentone) {
        KomiScreentone.None -> Modifier
        KomiScreentone.Corner -> Modifier.screentoneCorner(colors.onSurface, colors.screentoneOpacity, boost = screentoneBoost)
        KomiScreentone.Fill -> Modifier.screentoneFill(colors.onSurface, colors.screentoneOpacity)
    }
    val borderModifier = if (topEdgeOnly) Modifier else Modifier.border(width = borderWidth, color = line)
    Box(
        modifier = modifier
            .then(tiltModifier)
            .then(depthModifier)
            .background(color = fill)
            .then(toneModifier)
            .then(borderModifier)
            .then(if (topEdgeOnly) Modifier.topInkEdge(line, borderWidth) else Modifier)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentInk) { content() }
    }
}

private fun Modifier.topInkEdge(color: Color, width: Dp): Modifier =
    drawBehind {
        drawRect(color = color, topLeft = Offset.Zero, size = Size(size.width, width.toPx()))
    }

// ---------------------------------------------------------------- botones

enum class KomiButtonVariant { Primary, Tonal, Outline, Text, Destructive }
enum class KomiButtonSize { Sm, Md, Lg }

private data class ButtonMetrics(val height: Dp, val hPadding: Dp, val font: TextUnit, val icon: Dp, val gap: Dp, val shadow: Dp, val border: Dp)

private fun buttonMetrics(size: KomiButtonSize): ButtonMetrics =
    when (size) {
        KomiButtonSize.Sm -> ButtonMetrics(34.dp, 14.dp, 12.sp, 15.dp, 7.dp, 3.dp, 2.5.dp)
        KomiButtonSize.Md -> ButtonMetrics(44.dp, 20.dp, 13.5.sp, 18.dp, 9.dp, 4.dp, 2.5.dp)
        KomiButtonSize.Lg -> ButtonMetrics(54.dp, 26.dp, 16.sp, 22.dp, 10.dp, 5.dp, 3.dp)
    }

internal fun mangaButtonContainer(variant: KomiButtonVariant, colors: PersonalityColors): Color =
    when (variant) {
        KomiButtonVariant.Primary -> colors.primary
        KomiButtonVariant.Destructive -> colors.error
        KomiButtonVariant.Tonal -> colors.surfaceVariant
        KomiButtonVariant.Outline, KomiButtonVariant.Text -> Color.Transparent
    }

internal fun mangaButtonContent(variant: KomiButtonVariant, colors: PersonalityColors, ambient: Color): Color =
    when (variant) {
        KomiButtonVariant.Primary -> ensureContrast(colors.onPrimary, colors.primary, colors.onBackground, colors.background)
        KomiButtonVariant.Destructive -> ensureContrast(colors.onError, colors.error, colors.onBackground, colors.background)
        KomiButtonVariant.Tonal -> ensureContrast(colors.onSurface, colors.surfaceVariant, colors.onBackground, colors.background)
        KomiButtonVariant.Outline, KomiButtonVariant.Text -> ambient
    }

@Composable
fun KomiButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    variant: KomiButtonVariant = KomiButtonVariant.Primary,
    size: KomiButtonSize = KomiButtonSize.Md,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    fullWidth: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    container: Color = Color.Unspecified,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val metrics = buttonMetrics(size)
    val active = enabled
    val alpha = if (active) 1f else 0.45f
    val flat = variant == KomiButtonVariant.Text
    val ambientInk = LocalContentColor.current
    val containerColor = if (container != Color.Unspecified) container else mangaButtonContainer(variant, colors)
    val contentColor =
        if (container != Color.Unspecified) ensureContrast(colors.onPrimary, container, colors.onBackground, colors.background)
        else mangaButtonContent(variant, colors, ambientInk)
    val borderColor = if (containerColor == Color.Transparent) contentColor else colors.outline
    val stamped = !flat && containerColor != Color.Transparent
    val sweep = emphasized && active && (variant == KomiButtonVariant.Primary || variant == KomiButtonVariant.Destructive)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressProgress = animateFloatAsState(if (pressed && active) 1f else 0f, label = "komiButtonPress")
    val pressModifier =
        if (!stamped) {
            Modifier.offset { IntOffset(0, (1.dp.toPx() * pressProgress.value).roundToInt()) }
        } else {
            Modifier.inkPress(
                pressProgress = { pressProgress.value },
                hoverProgress = { 0f },
                shadow = DpOffset(metrics.shadow, metrics.shadow),
                shadowColor = colors.shadow.copy(alpha = alpha),
                shape = RectangleShape,
            )
        }
    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .inkFocusRing(focused = { focused }, color = colors.primary)
            .then(pressModifier)
            .then(if (sweep) Modifier.clipToBounds() else Modifier)
            .background(color = containerColor.copy(alpha = containerColor.alpha * alpha))
            .then(if (sweep) Modifier.speedLines(color = contentColor, opacity = 0.16f) else Modifier)
            .then(if (!flat) Modifier.border(width = metrics.border, color = borderColor.copy(alpha = alpha)) else Modifier)
            .clickable(enabled = active, interactionSource = interaction, indication = null, onClick = onClick)
            .height(metrics.height)
            .padding(horizontal = metrics.hPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor.copy(alpha = alpha)) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(metrics.icon))
                Spacer(Modifier.width(metrics.gap))
            }
            KomiText(label, role = KomiTextRole.Label, color = contentColor.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = metrics.font)
            if (trailingIcon != null) {
                Spacer(Modifier.width(metrics.gap))
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(metrics.icon))
            }
        }
    }
}

@Composable
fun KomiIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KomiButtonVariant = KomiButtonVariant.Outline,
    enabled: Boolean = true,
    size: Dp = 40.dp,
) {
    val colors = LocalPersonality.current.colors
    val alpha = if (enabled) 1f else 0.4f
    val container = mangaButtonContainer(variant, colors)
    val content = mangaButtonContent(variant, colors, LocalContentColor.current)
    val borderColor = if (container == Color.Transparent) content else colors.outline
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressProgress = animateFloatAsState(if (pressed && enabled) 1f else 0f, label = "komiIconButtonPress")
    Box(
        modifier = modifier
            .then(
                if (container != Color.Transparent) {
                    Modifier.inkPress({ pressProgress.value }, { 0f }, DpOffset(3.dp, 3.dp), colors.shadow.copy(alpha = alpha), RectangleShape)
                } else {
                    Modifier.offset { IntOffset(0, (1.dp.toPx() * pressProgress.value).roundToInt()) }
                },
            )
            .size(size)
            .background(container.copy(alpha = container.alpha * alpha))
            .border(2.5.dp, borderColor.copy(alpha = alpha))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp), tint = content.copy(alpha = alpha))
    }
}

// ---------------------------------------------------------------- chips y badges

enum class KomiChipKind { Info, Filter }

private data class ChipMetrics(val height: Dp, val paddingX: Dp, val font: Float, val icon: Dp, val border: Dp)

private fun chipMetrics(kind: KomiChipKind, small: Boolean): ChipMetrics =
    when {
        kind == KomiChipKind.Filter && small -> ChipMetrics(26.dp, 11.dp, 12f, 14.dp, 2.5.dp)
        kind == KomiChipKind.Filter -> ChipMetrics(34.dp, 14.dp, 14f, 16.dp, 2.5.dp)
        small -> ChipMetrics(24.dp, 9.dp, 11f, 13.dp, 2.dp)
        else -> ChipMetrics(28.dp, 10.dp, 11.5f, 14.dp, 2.dp)
    }

private const val ChipSkewDegrees = 8f

/**
 * Chip manga. `fill`/`ink` permiten un sello de color semántico (lo usa Patchy para las notas).
 */
@Composable
fun KomiChip(
    label: String,
    modifier: Modifier = Modifier,
    kind: KomiChipKind = KomiChipKind.Info,
    small: Boolean = false,
    selected: Boolean = false,
    index: Int = 0,
    tilt: Boolean = true,
    leadingIcon: ImageVector? = null,
    fill: Color = Color.Unspecified,
    ink: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val metrics = chipMetrics(kind, small)
    val isFilter = kind == KomiChipKind.Filter
    val tappable = onClick != null
    val doSkew = isFilter && tilt
    val rot = if (doSkew) (if (index % 2 != 0) 0.4f else -0.4f) else 0f
    val bg = when {
        fill != Color.Unspecified -> fill
        selected -> colors.primary
        isFilter -> colors.surface
        else -> colors.background
    }
    val fg = when {
        ink != Color.Unspecified -> ink
        fill != Color.Unspecified -> inkOn(fill, colors.onBackground, colors.background)
        selected -> colors.onPrimary
        else -> colors.onSurface
    }
    val hasShadow = (selected && isFilter) || fill != Color.Unspecified
    val shape = remember(doSkew) { if (doSkew) stampShape(ChipSkewDegrees) else RectangleShape }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressProgress = animateFloatAsState(if (pressed) 1f else 0f, label = "komiChipPress")
    Row(
        modifier = modifier
            .then(if (rot != 0f) Modifier.rotate(rot) else Modifier)
            .offset { IntOffset(0, (1.dp.toPx() * pressProgress.value).roundToInt()) }
            .then(if (hasShadow) Modifier.hardShadow(DpOffset(2.5.dp, 2.5.dp), colors.shadow, shape) else Modifier)
            .background(color = bg, shape = shape)
            .border(width = metrics.border, color = colors.outline, shape = shape)
            .then(if (tappable) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick ?: {}) else Modifier)
            .height(metrics.height)
            .padding(horizontal = metrics.paddingX),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(metrics.icon), tint = fg)
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = if (isFilter) personality.type.display.fontFamily else personality.type.body.fontFamily,
                fontWeight = if (isFilter) FontWeight.Normal else FontWeight.W800,
                fontSize = metrics.font.sp,
                letterSpacing = if (isFilter) 0.05.em else 0.04.em,
            ),
            color = fg, maxLines = 1, overflow = TextOverflow.Clip,
        )
    }
}

enum class KomiBadgeTone { Alert, Neutral }

@Composable
fun KomiBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: KomiBadgeTone = KomiBadgeTone.Neutral,
    tilt: Boolean = false,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val fill = if (tone == KomiBadgeTone.Neutral) colors.primary else colors.error
    val fg = if (tone == KomiBadgeTone.Neutral) colors.onPrimary else colors.onError
    Box(
        modifier = modifier
            .then(if (tilt) Modifier.graphicsLayer { rotationZ = -4f } else Modifier)
            .height(20.dp).widthIn(min = 20.dp)
            .hardShadow(DpOffset(2.dp, 2.dp), colors.shadow)
            .background(fill)
            .border(2.dp, colors.outline)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = fg, style = personality.type.label.copy(fontWeight = FontWeight.W900, fontSize = 12.5.sp, fontFeatureSettings = "tnum"))
    }
}

// ---------------------------------------------------------------- inputs

@Composable
fun KomiCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = LocalPersonality.current.colors
    val alpha = if (enabled) 1f else 0.45f
    val fill by animateColorAsState(if (checked) colors.primary else colors.surface, label = "komiCheckboxFill")
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val toggleModifier =
        if (onCheckedChange != null) {
            Modifier.toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
        } else Modifier
    Box(
        modifier = modifier
            .then(toggleModifier)
            .size(22.dp)
            .inkFocusRing(focused = { focused }, color = colors.primary)
            .background(color = fill.copy(alpha = fill.alpha * alpha))
            .border(width = 2.5.dp, color = colors.outline.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(ImageVector.vectorResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.onPrimary.copy(alpha = alpha))
    }
}

@Composable
fun KomiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    helper: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    multiline: Boolean = false,
    rows: Int = 4,
    onCommit: (() -> Unit)? = null,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val type = personality.type
    val hasAccent = colors.primary != colors.onSurface
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val shellInteraction = remember { MutableInteractionSource() }
    val shadowColor = if (focused && hasAccent) colors.primary else colors.shadow
    val fill = if (enabled) colors.surface else colors.surfaceVariant
    Column(modifier = modifier.then(if (enabled) Modifier else Modifier.alpha(0.5f))) {
        if (label != null) {
            Text(text = label.uppercase(), style = TextStyle(fontFamily = type.display.fontFamily, fontSize = 13.sp, letterSpacing = 0.05.em), color = colors.onSurface)
            Spacer(Modifier.height(7.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.hardShadow(offset = DpOffset(4.dp, 4.dp), color = shadowColor) else Modifier)
                .background(color = fill)
                .border(width = 3.dp, color = colors.outline)
                .then(if (enabled) Modifier.clickable(interactionSource = shellInteraction, indication = null, onClick = { focusRequester.requestFocus() }) else Modifier)
                .then(if (multiline) Modifier.padding(10.dp) else Modifier.heightIn(min = 46.dp).padding(horizontal = 12.dp)),
            verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                enabled = enabled,
                textStyle = TextStyle(fontFamily = type.body.fontFamily, fontWeight = FontWeight.W700, fontSize = 15.sp, color = colors.onSurface),
                cursorBrush = SolidColor(if (hasAccent) colors.primary else colors.onSurface),
                singleLine = !multiline,
                minLines = if (multiline) rows else 1,
                maxLines = if (multiline) Int.MAX_VALUE else 1,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (!multiline && onCommit != null) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { onCommit?.invoke() }),
                interactionSource = interaction,
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, style = TextStyle(fontFamily = type.body.fontFamily, fontWeight = FontWeight.W700, fontSize = 15.sp), color = colors.onSurfaceVariant, maxLines = 1)
                    }
                    inner()
                },
            )
        }
        if (helper != null) {
            Spacer(Modifier.height(6.dp))
            Text(text = helper, style = TextStyle(fontFamily = type.body.fontFamily, fontWeight = FontWeight.W700, fontSize = 11.5.sp), color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun KomiHorizontalDivider(modifier: Modifier = Modifier, thickness: Dp = 2.5.dp, color: Color = Color.Unspecified) {
    val colors = LocalPersonality.current.colors
    val resolved = if (color != Color.Unspecified) color else colors.outline.copy(alpha = 0.4f)
    Box(modifier.fillMaxWidth().height(thickness).background(resolved))
}

// ---------------------------------------------------------------- barras y scaffold

private fun mangaTitle(title: String, accentSub: String?, accent: Color): AnnotatedString {
    val upper = title.uppercase()
    if (accentSub.isNullOrEmpty()) return AnnotatedString(upper)
    val accentUpper = accentSub.uppercase()
    val idx = upper.indexOf(accentUpper)
    if (idx < 0) return AnnotatedString(upper)
    return buildAnnotatedString {
        append(upper.substring(0, idx))
        withStyle(SpanStyle(color = accent)) { append(upper.substring(idx, idx + accentUpper.length)) }
        append(upper.substring(idx + accentUpper.length))
    }
}

@Composable
fun KomiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleAccent: String? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val annotatedTitle = remember(title, titleAccent, colors.primary) { mangaTitle(title, titleAccent, colors.primary) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .clipToBounds()
            .then(if (personality.speedLines) Modifier.speedLineWash(color = colors.onSurface) else Modifier)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = annotatedTitle, style = personality.type.title.copy(fontSize = 27.sp), color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                KomiText(text = it, modifier = Modifier.padding(top = 2.dp), role = KomiTextRole.Label, color = colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.W800, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions?.let { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = it) }
    }
}

@Immutable
data class KomiNavItem(val id: String, val label: String, val icon: ImageVector)

@Composable
fun KomiBottomBar(items: List<KomiNavItem>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier = modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding().height(66.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.id == selectedId
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = interaction, indication = null, onClick = { onSelect(item.id) })
                    .inkFocusRing(focused = { focused }, color = colors.primary),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 30.dp)
                        .background(color = if (selected) colors.primary else Color.Transparent)
                        .border(width = 2.5.dp, color = if (selected) colors.outline else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp), tint = if (selected) colors.onPrimary else colors.onSurface)
                }
                Spacer(Modifier.height(4.dp))
                KomiText(item.label, role = KomiTextRole.Label, color = if (selected) colors.onSurface else colors.onSurfaceVariant, fontSize = 10.5.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun KomiScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    grid: Boolean = true,
    dividers: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentColor = colors.onBackground,
        topBar = {
            topBar?.let { bar ->
                Column {
                    bar()
                    if (dividers) HorizontalDivider(thickness = 3.dp, color = colors.outline)
                }
            }
        },
        bottomBar = {
            bottomBar?.let { bar ->
                Column {
                    if (dividers) HorizontalDivider(thickness = 3.dp, color = colors.outline)
                    bar()
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            if (grid) Box(Modifier.fillMaxSize().gridPaper(color = colors.onSurface, opacity = colors.gridOpacity))
            content(innerPadding)
        }
    }
}
