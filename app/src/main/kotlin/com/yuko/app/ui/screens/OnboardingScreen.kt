package com.yuko.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuko.app.R
import com.yuko.app.ui.AppPrefs
import com.yuko.app.ui.Genres
import com.yuko.app.ui.komi.KomiButton
import com.yuko.app.ui.komi.KomiButtonSize
import com.yuko.app.ui.komi.KomiButtonVariant
import com.yuko.app.ui.komi.KomiCheckbox
import com.yuko.app.ui.komi.KomiChip
import com.yuko.app.ui.komi.KomiChipKind
import com.yuko.app.ui.komi.KomiListContainer
import com.yuko.app.ui.komi.KomiListRow
import com.yuko.app.ui.komi.KomiScaffold
import com.yuko.app.ui.komi.KomiSectionHead
import com.yuko.app.ui.komi.KomiSegmented
import com.yuko.app.ui.komi.KomiSegmentedItem
import com.yuko.app.ui.komi.KomiText
import com.yuko.app.ui.komi.KomiTextField
import com.yuko.app.ui.komi.KomiTextRole
import com.yuko.app.ui.komi.KomiTopBar
import com.yuko.app.ui.komi.LocalPersonality
import com.yuko.app.ui.komi.ThemePrefs
import com.yuko.app.ui.komi.Themes

/** First start: adult content (off by default), genres you like (at least three) and the look of the app. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val enough = AppPrefs.selectedGenres.size >= 3
	KomiScaffold(
		topBar = { KomiTopBar(title = stringResource(R.string.app_name), titleAccent = "ko", subtitle = stringResource(R.string.onboarding_kicker)) },
	) { padding ->
		Column(Modifier.fillMaxSize().padding(padding)) {
			Column(
				modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 16.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				KomiText(text = stringResource(R.string.onboarding_title), role = KomiTextRole.Display)
				KomiText(text = stringResource(R.string.onboarding_text), role = KomiTextRole.Body, color = colors.onSurfaceVariant, uppercase = false)

				KomiSectionHead(label = stringResource(R.string.content), kicker = "内容")
				AdultRow()

				KomiSectionHead(label = stringResource(R.string.genres_you_like), kicker = "好み")
				KomiText(text = stringResource(R.string.genres_pick_hint), role = KomiTextRole.Body, color = colors.onSurfaceVariant, uppercase = false)
				GenrePicker(selected = AppPrefs.selectedGenres, onToggle = { AppPrefs.toggleSelectedGenre(it) })

				KomiSectionHead(label = stringResource(R.string.appearance), kicker = "外観")
				ThemePicker()
			}
			Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
				KomiText(text = "${AppPrefs.selectedGenres.size}/3", role = KomiTextRole.Label, color = if (enough) colors.onSurfaceVariant else colors.error)
				KomiButton(onClick = { AppPrefs.setOnboardingDone(); onDone() }, label = stringResource(R.string.start), emphasized = true, enabled = enough)
			}
		}
	}
}

@Composable
fun AdultRow() {
	KomiListContainer {
		KomiListRow(
			title = stringResource(R.string.show_adult),
			subtitle = stringResource(R.string.show_adult_summary),
			onClick = { AppPrefs.updateShowAdult(!AppPrefs.showAdult) },
			trailing = { KomiCheckbox(checked = AppPrefs.showAdult, onCheckedChange = { AppPrefs.updateShowAdult(it) }) },
		)
	}
}

/**
 * Genre chips with a search box: the main genres are shown by default, the rest appear when
 * searching. Adult genres only show up when adult content is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenrePicker(selected: Set<String>, onToggle: (String) -> Unit, tone: KomiChipKind = KomiChipKind.Filter) {
	var query by rememberSaveable { mutableStateOf("") }
	var showAll by rememberSaveable { mutableStateOf(false) }
	val visible = when {
		query.isNotBlank() -> Genres.search(query)
		showAll -> Genres.all
		else -> Genres.all.filter { it.main || it.id in selected }
	}.filter { AppPrefs.showAdult || !it.adult }
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
			KomiTextField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.search_genre), modifier = Modifier.weight(1f))
			KomiButton(onClick = { showAll = !showAll; query = "" }, label = if (showAll) stringResource(R.string.main_genres) else stringResource(R.string.all_genres), size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline)
		}
		FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			visible.forEachIndexed { index, genre ->
				KomiChip(
					label = genre.name,
					kind = tone,
					selected = genre.id in selected,
					index = index,
					tilt = false,
					onClick = { onToggle(genre.id) },
				)
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker() {
	val colors = LocalPersonality.current.colors
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		KomiSegmented(
			selected = ThemePrefs.mode,
			items = listOf(
				KomiSegmentedItem("system", stringResource(R.string.mode_system)),
				KomiSegmentedItem("light", stringResource(R.string.mode_light)),
				KomiSegmentedItem("dark", stringResource(R.string.mode_dark)),
			),
			onSelect = { ThemePrefs.selectMode(it) },
			modifier = Modifier.fillMaxWidth(),
			fillWidth = true,
		)
		FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Themes.all.forEach { theme ->
				val selected = theme.id == ThemePrefs.themeId
				val palette = if (colors.isDark) theme.dark else theme.light
				Row(
					modifier = Modifier
						.background(if (selected) colors.primary else colors.surface)
						.border(2.5.dp, colors.outline)
						.clickable { ThemePrefs.selectTheme(theme.id) }
						.padding(horizontal = 10.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Row {
						Swatch(Color(palette.primary)); Swatch(Color(palette.secondary)); Swatch(Color(palette.gold))
					}
					KomiText(text = theme.name, role = KomiTextRole.Label, color = if (selected) colors.onPrimary else colors.onSurface, uppercase = false, fontSize = 12.sp)
				}
			}
		}
	}
}

@Composable
private fun Swatch(color: Color) {
	val colors = LocalPersonality.current.colors
	Box(Modifier.size(14.dp).background(color).border(1.5.dp, colors.outline))
	Spacer(Modifier.width(2.dp))
}
