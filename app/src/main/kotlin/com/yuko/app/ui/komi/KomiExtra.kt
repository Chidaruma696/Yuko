/*
 * Adapted from Komi Store (komi-store/komi-store), Apache License 2.0:
 * KomiSegmented, KomiList (container + row), KomiSheet (bottom placement), KomiDialog,
 * KomiProgress, KomiFab and the settings section head. "Manga" branch only.
 */
package com.yuko.app.ui.komi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ---------------------------------------------------------------- segmented

@Immutable
data class KomiSegmentedItem<T>(val value: T, val title: String? = null, val icon: ImageVector? = null)


@Composable
fun <T> KomiSegmented(
	selected: T,
	items: List<KomiSegmentedItem<T>>,
	onSelect: (T) -> Unit,
	modifier: Modifier = Modifier,
	height: Dp = 40.dp,
	fillWidth: Boolean = false,
) {
	val colors = LocalPersonality.current.colors
	val border = 2.5.dp
	Row(
		modifier = modifier
			.height(height)
			.hardShadow(offset = DpOffset(3.dp, 3.dp), color = colors.shadow, shape = RectangleShape)
			.background(colors.surface)
			.border(border, colors.outline)
			.clip(RectangleShape),
		verticalAlignment = Alignment.CenterVertically,
	) {
		items.forEachIndexed { index, item ->
			if (index > 0) {
				Box(Modifier.width(border).fillMaxHeight().background(colors.outline))
			}
			val active = selected == item.value
			val interaction = remember { MutableInteractionSource() }
			val focused by interaction.collectIsFocusedAsState()
			Box(
				modifier = Modifier
					.fillMaxHeight()
					.then(if (fillWidth) Modifier.weight(1f) else Modifier.defaultMinSize(minWidth = height))
					.inkFocusRing(focused = { focused }, color = colors.primary)
					.background(if (active) colors.primary else Color.Transparent)
					.clickable(interactionSource = interaction, indication = null) { onSelect(item.value) }
					.padding(horizontal = Spacing.md),
				contentAlignment = Alignment.Center,
			) {
				val ink = if (active) colors.onPrimary else colors.onSurface
				Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
					item.icon?.let { Icon(it, contentDescription = item.title, modifier = Modifier.size(18.dp), tint = ink) }
					item.title?.let { KomiText(it, role = KomiTextRole.Stamp, color = ink, fontSize = 13.sp, maxLines = 1) }
				}
			}
		}
	}
}

// ---------------------------------------------------------------- lists

@Composable
fun KomiListContainer(
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	Column(
		modifier = modifier
			.fillMaxWidth()
			.hardShadow(offset = DpOffset(4.dp, 4.dp), color = colors.shadow, shape = RectangleShape)
			.background(colors.surface)
			.border(width = personality.shape.borderPanel, color = colors.outline),
		content = content,
	)
}

@Composable
fun KomiListRow(
	title: String,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	icon: ImageVector? = null,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	strong: Boolean = false,
	destructive: Boolean = false,
	selected: Boolean = false,
	enabled: Boolean = true,
	showDivider: Boolean = false,
	trailing: @Composable (() -> Unit)? = null,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val interaction = remember { MutableInteractionSource() }
	val focused by interaction.collectIsFocusedAsState()
	val titleColor = if (destructive) colors.error else colors.onSurface
	val clickModifier = if (onClick == null && onLongClick == null) {
		Modifier
	} else {
		Modifier.combinedClickable(
			interactionSource = interaction,
			indication = null,
			enabled = enabled,
			onLongClick = onLongClick,
			onClick = onClick ?: {},
		)
	}
	Row(
		modifier = modifier
			.fillMaxWidth()
			.inkFocusRing(focused = { focused }, color = colors.primary)
			.then(if (selected) Modifier.background(colors.surfaceVariant) else Modifier)
			.then(
				if (showDivider) Modifier.drawBehind {
					val stroke = 2.dp.toPx()
					val y = size.height - stroke / 2f
					drawLine(color = colors.outline, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = stroke)
				} else Modifier,
			)
			.then(
				if (selected) Modifier.drawBehind {
					drawRect(color = colors.primary, size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height))
				} else Modifier,
			)
			.then(clickModifier)
			.padding(horizontal = 14.dp, vertical = 12.dp)
			.alpha(if (enabled) 1f else 0.45f),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		if (icon != null) {
			Box(
				modifier = Modifier
					.size(42.dp)
					.background(colors.background)
					.border(width = personality.shape.borderChip, color = colors.outline),
				contentAlignment = Alignment.Center,
			) {
				Icon(icon, contentDescription = null, tint = if (destructive) colors.error else colors.onSurface, modifier = Modifier.size(22.dp))
			}
		}
		Column(modifier = Modifier.weight(1f)) {
			KomiText(
				text = title,
				role = KomiTextRole.Label,
				color = titleColor,
				fontWeight = if (strong) FontWeight.Black else null,
				uppercase = false,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			subtitle?.let {
				KomiText(text = it, role = KomiTextRole.Body, color = colors.onSurfaceVariant, uppercase = false, maxLines = 2, overflow = TextOverflow.Ellipsis)
			}
		}
		trailing?.invoke()
	}
}

private val SkewedStamp = GenericShape { size, _ ->
	val k = size.height * 0.21f
	moveTo(k, 0f)
	lineTo(size.width, 0f)
	lineTo(size.width - k, size.height)
	lineTo(0f, size.height)
	close()
}

/** Section head with the skewed accent stamp, as in Komi's settings. */
@Composable
fun KomiSectionHead(
	label: String,
	modifier: Modifier = Modifier,
	kicker: String? = null,
	action: (@Composable () -> Unit)? = null,
) {
	val colors = LocalPersonality.current.colors
	Row(
		modifier = modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Box(
			modifier = Modifier
				.size(width = 11.dp, height = 21.dp)
				.background(colors.primary, SkewedStamp)
				.border(2.dp, colors.outline, SkewedStamp),
		)
		KomiText(text = label, role = KomiTextRole.Stamp, color = colors.onSurface, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
		if (kicker != null) {
			KomiText(text = kicker, role = KomiTextRole.Label, color = colors.onSurfaceVariant, fontSize = 11.sp, uppercase = false, maxLines = 1)
		}
		Box(Modifier.weight(1f).height(2.dp).background(colors.outline.copy(alpha = 0.3f)))
		action?.invoke()
	}
}

// ---------------------------------------------------------------- overlays

private val MarkerShape = GenericShape { size, _ ->
	val k = size.height * 0.25f
	moveTo(k, 0f)
	lineTo(size.width, 0f)
	lineTo(size.width - k, size.height)
	lineTo(0f, size.height)
	close()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KomiSheet(
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
	title: String? = null,
	titleJp: String? = null,
	screentone: Boolean = true,
	footer: (@Composable () -> Unit)? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		modifier = modifier,
		sheetState = sheetState,
		shape = RectangleShape,
		containerColor = colors.background,
		contentColor = colors.onSurface,
		scrimColor = colors.shadow.copy(alpha = 0.5f),
		dragHandle = null,
	) {
		Column(Modifier.fillMaxWidth()) {
			Box(Modifier.fillMaxWidth().height(4.dp).background(colors.outline))
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.then(if (screentone) Modifier.screentoneCorner(colors.onSurface, colors.screentoneOpacity, boost = 1.6f, regionWidth = 150.dp, regionHeight = 110.dp) else Modifier)
					.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 22.dp),
			) {
				Box(Modifier.fillMaxWidth().padding(bottom = if (title != null) 12.dp else 8.dp), contentAlignment = Alignment.Center) {
					Box(Modifier.size(width = 46.dp, height = 5.dp).background(colors.outline))
				}
				if (title != null) {
					Row(
						modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Box(Modifier.size(width = 10.dp, height = 18.dp).background(colors.primary, MarkerShape).border(2.dp, colors.outline, MarkerShape))
						KomiText(text = title, role = KomiTextRole.Title, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
						if (titleJp != null) {
							KomiText(text = titleJp, role = KomiTextRole.Label, color = colors.onSurfaceVariant, fontSize = 11.sp, uppercase = false, maxLines = 1)
						}
					}
				}
				Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
				if (footer != null) {
					Box(Modifier.fillMaxWidth().padding(top = 16.dp)) { footer() }
				}
			}
		}
	}
}

@Composable
fun KomiDialog(
	onDismissRequest: () -> Unit,
	confirmButton: @Composable () -> Unit,
	modifier: Modifier = Modifier,
	dismissButton: (@Composable () -> Unit)? = null,
	title: (@Composable () -> Unit)? = null,
	text: (@Composable () -> Unit)? = null,
	properties: DialogProperties = DialogProperties(),
) {
	Dialog(onDismissRequest = onDismissRequest, properties = properties) {
		KomiSurface(
			modifier = modifier.fillMaxWidth(),
			elevation = KomiSurfaceElevation.Modal,
			contentPadding = PaddingValues(20.dp),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
				title?.invoke()
				text?.invoke()
				Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
					dismissButton?.invoke()
					confirmButton()
				}
			}
		}
	}
}

// ---------------------------------------------------------------- progress

@Composable
fun KomiCircularProgress(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
	val colors = LocalPersonality.current.colors
	CircularProgressIndicator(
		modifier = modifier,
		color = if (color != Color.Unspecified) color else colors.primary,
		strokeWidth = 4.dp,
		strokeCap = StrokeCap.Butt,
		trackColor = colors.surfaceVariant,
	)
}

@Composable
fun KomiLinearProgress(modifier: Modifier = Modifier, progress: (() -> Float)? = null) {
	val colors = LocalPersonality.current.colors
	if (progress == null) {
		LinearProgressIndicator(modifier = modifier, color = colors.primary, trackColor = colors.surfaceVariant, strokeCap = StrokeCap.Butt)
	} else {
		LinearProgressIndicator(progress = progress, modifier = modifier, color = colors.primary, trackColor = colors.surfaceVariant, strokeCap = StrokeCap.Butt)
	}
}

// ---------------------------------------------------------------- fab

@Composable
fun KomiFab(
	onClick: () -> Unit,
	icon: ImageVector,
	contentDescription: String,
	modifier: Modifier = Modifier,
	label: String? = null,
) {
	val colors = LocalPersonality.current.colors
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val focused by interaction.collectIsFocusedAsState()
	val press = animateFloatAsState(if (pressed) 1f else 0f, label = "komiFabPress")
	val shadow = DpOffset((5 - 5 * press.value).dp, (5 - 5 * press.value).dp)
	val base = modifier
		.hardShadow(offset = shadow, color = colors.shadow, shape = RectangleShape)
		.background(colors.primary)
		.border(3.dp, colors.outline)
		.inkFocusRing(focused = { focused }, color = colors.primary)
		.clickable(interactionSource = interaction, indication = null, onClick = onClick)
	if (label != null) {
		Row(
			modifier = base.height(56.dp).padding(horizontal = 20.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Icon(icon, contentDescription, modifier = Modifier.size(22.dp), tint = colors.onPrimary)
			KomiText(label, role = KomiTextRole.Label, color = colors.onPrimary, fontSize = 15.sp)
		}
	} else {
		Box(modifier = base.size(56.dp), contentAlignment = Alignment.Center) {
			Icon(icon, contentDescription, modifier = Modifier.size(26.dp), tint = colors.onPrimary)
		}
	}
}

/** Bounded-height helper for sheets that host their own lazy lists. */
fun Modifier.sheetMaxHeight(max: Dp): Modifier = heightIn(max = max)
