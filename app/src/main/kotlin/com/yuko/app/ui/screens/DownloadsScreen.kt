package com.yuko.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuko.app.R
import com.yuko.app.download.DownloadItem
import com.yuko.app.download.DownloadRepository
import com.yuko.app.download.DownloadService
import com.yuko.app.download.humanSize
import com.yuko.app.reader.ReaderActivity
import com.yuko.app.ui.komi.KomiBadge
import com.yuko.app.ui.komi.KomiBadgeTone
import com.yuko.app.ui.komi.KomiButton
import com.yuko.app.ui.komi.KomiButtonSize
import com.yuko.app.ui.komi.KomiButtonVariant
import com.yuko.app.ui.komi.KomiLinearProgress
import com.yuko.app.ui.komi.KomiListContainer
import com.yuko.app.ui.komi.KomiListRow
import com.yuko.app.ui.komi.KomiSectionHead
import com.yuko.app.ui.komi.KomiText
import com.yuko.app.ui.komi.KomiTextRole
import com.yuko.app.ui.komi.LocalPersonality
import com.yuko.sources.MangaRef

@Composable
fun DownloadsScreen(contentPadding: PaddingValues, onOpen: (MangaRef) -> Unit) {
	val items by DownloadRepository.items.collectAsState()
	val colors = LocalPersonality.current.colors
	val context = LocalContext.current
	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		item { KomiSectionHead(label = "${items.size} ${stringResource(R.string.downloads)}", kicker = "保存") }
		if (items.isEmpty()) {
			item {
				Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
					KomiText(text = stringResource(R.string.downloads_empty), role = KomiTextRole.Body, color = colors.onSurfaceVariant, uppercase = false, textAlign = TextAlign.Center)
				}
			}
		}
		items(items.sortedByDescending { it.addedAt }, key = { it.id }) { item ->
			KomiListContainer {
				val subtitle = when (item.status) {
					DownloadItem.STATUS_DONE -> "${item.sourceName} · ${item.pageCount} ${stringResource(R.string.pages)} · ${humanSize(item.bytes)}"
					DownloadItem.STATUS_FAILED -> item.error ?: stringResource(R.string.download_failed)
					DownloadItem.STATUS_RUNNING -> if (item.pageCount > 0) "${item.pagesDone}/${item.pageCount} ${stringResource(R.string.pages)}" else stringResource(R.string.download_preparing)
					else -> stringResource(R.string.download_queued)
				}
				KomiListRow(
					title = "${item.manga.title} · ${item.chapter.displayName}",
					subtitle = subtitle,
					onClick = { if (item.status == DownloadItem.STATUS_DONE) ReaderActivity.startLocal(context, item) else onOpen(item.manga) },
					trailing = {
						when (item.status) {
							DownloadItem.STATUS_DONE -> KomiBadge(text = "OK", tone = KomiBadgeTone.Neutral)
							DownloadItem.STATUS_FAILED -> KomiBadge(text = "!", tone = KomiBadgeTone.Alert, tilt = true)
							else -> Unit
						}
					},
				)
				if (item.status == DownloadItem.STATUS_RUNNING && item.pageCount > 0) {
					KomiLinearProgress(
						modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp),
						progress = { (item.pagesDone.toFloat() / item.pageCount).coerceIn(0f, 1f) },
					)
				}
				Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					if (item.status == DownloadItem.STATUS_DONE) {
						KomiButton(onClick = { ReaderActivity.startLocal(context, item) }, label = stringResource(R.string.read), size = KomiButtonSize.Sm)
					}
					if (item.status == DownloadItem.STATUS_FAILED) {
						KomiButton(
							onClick = {
								DownloadRepository.update(item.id) { it.copy(status = DownloadItem.STATUS_QUEUED, error = null) }
								DownloadService.start(context)
							},
							label = stringResource(R.string.retry), size = KomiButtonSize.Sm,
						)
					}
					KomiButton(onClick = { DownloadRepository.remove(item) }, label = stringResource(R.string.delete), size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline)
				}
			}
		}
	}
}
