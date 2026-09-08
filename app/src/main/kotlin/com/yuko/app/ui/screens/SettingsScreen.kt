package com.yuko.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuko.app.BuildConfig
import com.yuko.app.R
import com.yuko.app.ui.AppPrefs
import com.yuko.app.ui.komi.KomiCheckbox
import com.yuko.app.ui.komi.KomiChipKind
import com.yuko.app.ui.komi.KomiListContainer
import com.yuko.app.ui.komi.KomiListRow
import com.yuko.app.ui.komi.KomiSectionHead
import com.yuko.app.ui.komi.KomiSegmented
import com.yuko.app.ui.komi.KomiSegmentedItem
import com.yuko.app.ui.komi.KomiText
import com.yuko.app.ui.komi.KomiTextRole
import com.yuko.app.ui.komi.LocalPersonality

/** Settings: appearance, content (adult switch, liked and excluded genres), reader, downloads, about. */
@Composable
fun SettingsScreen(contentPadding: PaddingValues, onOpenDownloads: () -> Unit) {
	val colors = LocalPersonality.current.colors
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(contentPadding)
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp)
			.padding(top = 4.dp, bottom = 24.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		KomiSectionHead(label = stringResource(R.string.appearance), kicker = "外観")
		ThemePicker()

		KomiSectionHead(label = stringResource(R.string.content), kicker = "内容")
		AdultRow()

		KomiSectionHead(label = stringResource(R.string.genres_you_like), kicker = "好み")
		GenrePicker(selected = AppPrefs.selectedGenres, onToggle = { AppPrefs.toggleSelectedGenre(it) })

		KomiSectionHead(label = stringResource(R.string.genres_excluded), kicker = "除外")
		KomiText(text = stringResource(R.string.genres_excluded_summary), role = KomiTextRole.Body, color = colors.onSurfaceVariant, uppercase = false)
		GenrePicker(selected = AppPrefs.excludedGenres, onToggle = { AppPrefs.toggleExcludedGenre(it) }, tone = KomiChipKind.Info)

		KomiSectionHead(label = stringResource(R.string.reader), kicker = "読書")
		KomiListContainer {
			KomiListRow(
				title = stringResource(R.string.reader_rtl),
				subtitle = stringResource(R.string.reader_rtl_summary),
				onClick = { AppPrefs.updateReaderRtl(!AppPrefs.readerRtl) },
				trailing = { KomiCheckbox(checked = AppPrefs.readerRtl, onCheckedChange = { AppPrefs.updateReaderRtl(it) }) },
				showDivider = true,
			)
			KomiListRow(
				title = stringResource(R.string.reader_mode),
				trailing = {
					KomiSegmented(
						selected = AppPrefs.readerMode,
						items = listOf(KomiSegmentedItem(AppPrefs.MODE_PAGED, stringResource(R.string.mode_paged)), KomiSegmentedItem(AppPrefs.MODE_WEBTOON, stringResource(R.string.mode_webtoon))),
						onSelect = { AppPrefs.updateReaderMode(it) },
						height = 34.dp,
					)
				},
				showDivider = true,
			)
			KomiListRow(
				title = stringResource(R.string.reader_scale),
				subtitle = stringResource(R.string.reader_scale_summary),
				trailing = {
					KomiSegmented(
						selected = AppPrefs.readerScale,
						items = listOf(
							KomiSegmentedItem(AppPrefs.SCALE_HEIGHT, stringResource(R.string.scale_height)),
							KomiSegmentedItem(AppPrefs.SCALE_WIDTH, stringResource(R.string.scale_width)),
							KomiSegmentedItem(AppPrefs.SCALE_SCREEN, stringResource(R.string.scale_screen)),
						),
						onSelect = { AppPrefs.updateReaderScale(it) },
						height = 34.dp,
					)
				},
			)
		}

		KomiSectionHead(label = stringResource(R.string.downloads), kicker = "保存")
		KomiListContainer {
			KomiListRow(title = stringResource(R.string.downloads), subtitle = stringResource(R.string.downloads_summary), onClick = onOpenDownloads, showDivider = true)
			KomiListRow(
				title = stringResource(R.string.parallel_downloads),
				subtitle = stringResource(R.string.parallel_downloads_summary),
				trailing = {
					KomiSegmented(
						selected = AppPrefs.parallelDownloads,
						items = listOf(1, 2, 3, 4).map { KomiSegmentedItem(it, it.toString()) },
						onSelect = { AppPrefs.updateParallelDownloads(it) },
						height = 34.dp,
					)
				},
			)
		}

		KomiSectionHead(label = stringResource(R.string.about), kicker = "情報")
		AboutSection()

		KomiText(
			text = "― ${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME} ―",
			role = KomiTextRole.Stamp, color = colors.onSurfaceVariant, fontSize = 11.sp, uppercase = false, textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
		)
	}
}
