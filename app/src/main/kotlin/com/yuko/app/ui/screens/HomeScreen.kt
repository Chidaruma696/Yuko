package com.yuko.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuko.app.R
import com.yuko.app.data.HistoryRepository
import com.yuko.app.ui.AppPrefs
import com.yuko.app.ui.FeedManga
import com.yuko.app.ui.HomeViewModel
import com.yuko.app.ui.komi.KomiButton
import com.yuko.app.ui.komi.KomiButtonSize
import com.yuko.app.ui.komi.KomiButtonVariant
import com.yuko.app.ui.komi.KomiCircularProgress
import com.yuko.app.ui.komi.KomiLinearProgress
import com.yuko.app.ui.komi.KomiScreentone
import com.yuko.app.ui.komi.KomiSectionHead
import com.yuko.app.ui.komi.KomiSurface
import com.yuko.app.ui.komi.KomiSurfaceElevation
import com.yuko.app.ui.komi.KomiText
import com.yuko.app.ui.komi.KomiTextRole
import com.yuko.app.ui.komi.LocalPersonality
import com.yuko.sources.LoadedSource
import com.yuko.sources.MangaRef
import com.yuko.sources.toRef

/** Home: continue reading, recommendations by genre, latest updates and one popular row per source. */
@Composable
fun HomeScreen(contentPadding: PaddingValues, onOpen: (FeedManga) -> Unit, onOpenSource: (LoadedSource) -> Unit) {
	val vm = viewModel<HomeViewModel>()
	val state by vm.state.collectAsState()
	val history by HistoryRepository.items.collectAsState()
	val colors = LocalPersonality.current.colors
	LaunchedEffect(Unit) { vm.load() }

	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		if (AppPrefs.kaoBannerVisible) {
			item(key = "kao") { KeepAndroidOpenBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
		}
		if (history.isNotEmpty()) {
			item(key = "continue_head") { KomiSectionHead(label = stringResource(R.string.continue_reading), kicker = "続き", modifier = Modifier.padding(horizontal = 16.dp)) }
			item(key = "continue") {
				LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					items(history.take(10), key = { it.manga.key }) { entry ->
						val src = com.yuko.sources.MangaSources.byId(entry.manga.sourceId)
						CoverCard(
							manga = entry.manga,
							subtitle = "${entry.chapter.displayName} · ${entry.page + 1}/${entry.pageCount.coerceAtLeast(1)}",
							onClick = { if (src != null) onOpen(FeedManga(entry.manga.toManga(), src)) },
						)
					}
				}
			}
		}
		if (state.isLoading) {
			item(key = "progress") {
				Column(Modifier.padding(horizontal = 16.dp)) {
					KomiLinearProgress(modifier = Modifier.fillMaxWidth())
					KomiText(
						text = "${state.loadedSources}/${state.enabledSources} ${stringResource(R.string.sources).lowercase()}",
						role = KomiTextRole.Label, color = colors.onSurfaceVariant, fontSize = 11.sp, uppercase = false,
						modifier = Modifier.padding(top = 4.dp),
					)
				}
			}
		}
		state.error?.let { err ->
			item(key = "error") {
				Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
					KomiText(text = err, role = KomiTextRole.Body, color = colors.error, uppercase = false, textAlign = TextAlign.Center)
					KomiButton(onClick = { vm.load(force = true) }, label = stringResource(R.string.retry), variant = KomiButtonVariant.Outline)
				}
			}
		}
		if (state.recommended.isNotEmpty()) {
			item(key = "recommended_head") {
				KomiSectionHead(
					label = stringResource(R.string.for_you), kicker = "おすすめ",
					modifier = Modifier.padding(horizontal = 16.dp),
					action = { KomiButton(onClick = { vm.load(force = true) }, label = stringResource(R.string.shuffle), size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline, enabled = !state.isLoading) },
				)
			}
			item(key = "recommended") { HeroRow(items = state.recommended, onOpen = onOpen) }
		}
		if (state.latest.isNotEmpty()) {
			item(key = "latest_head") { KomiSectionHead(label = stringResource(R.string.latest_updates), kicker = "新着", modifier = Modifier.padding(horizontal = 16.dp)) }
			item(key = "latest") { FeedRowList(items = state.latest, onOpen = onOpen, showSource = true) }
		}
		state.rows.forEach { row ->
			item(key = "row_head_${row.title}") {
				KomiSectionHead(
					label = row.title, kicker = row.kicker,
					modifier = Modifier.padding(horizontal = 16.dp),
					action = row.source?.let { src -> { KomiButton(onClick = { onOpenSource(src) }, label = stringResource(R.string.see_all), size = KomiButtonSize.Sm, variant = KomiButtonVariant.Text) } },
				)
			}
			item(key = "row_${row.title}") { FeedRowList(items = row.items, onOpen = onOpen, showSource = false) }
		}
		if (!state.isLoading && state.error == null && state.rows.isEmpty()) {
			item(key = "empty") {
				Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { KomiCircularProgress() }
			}
		}
	}
}

@Composable
private fun HeroRow(items: List<FeedManga>, onOpen: (FeedManga) -> Unit) {
	val colors = LocalPersonality.current.colors
	LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		items(items, key = { it.key }) { item ->
			KomiSurface(
				modifier = Modifier.width(280.dp),
				elevation = KomiSurfaceElevation.Card,
				screentone = KomiScreentone.Corner,
				onClick = { onOpen(item) },
				contentPadding = PaddingValues(12.dp),
			) {
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					Cover(url = item.manga.coverUrl, source = item.source, modifier = Modifier.width(96.dp))
					Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
						KomiText(text = item.manga.title, role = KomiTextRole.Title, maxLines = 3, overflow = TextOverflow.Ellipsis)
						KomiText(text = item.source.name, role = KomiTextRole.Label, color = colors.onSurfaceVariant, fontSize = 11.sp, uppercase = false, maxLines = 1)
						item.manga.tags.take(3).takeIf { it.isNotEmpty() }?.let { tags ->
							KomiText(text = tags.joinToString(" · ") { it.title }, role = KomiTextRole.Body, color = colors.onSurfaceVariant, fontSize = 12.sp, uppercase = false, maxLines = 2, overflow = TextOverflow.Ellipsis)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun FeedRowList(items: List<FeedManga>, onOpen: (FeedManga) -> Unit, showSource: Boolean) {
	LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
		items(items, key = { it.key }) { item ->
			CoverCard(
				manga = item.manga.toRef(),
				subtitle = if (showSource) item.source.name else null,
				onClick = { onOpen(item) },
			)
		}
	}
}

/** Narrow cover + title, used in horizontal rows. */
@Composable
fun CoverCard(manga: MangaRef, subtitle: String?, onClick: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val source = com.yuko.sources.MangaSources.byId(manga.sourceId)
	Column(Modifier.width(112.dp).clickable(onClick = onClick)) {
		Cover(url = manga.coverUrl, source = source, modifier = Modifier.fillMaxWidth())
		KomiText(text = manga.title, role = KomiTextRole.Label, uppercase = false, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
		if (subtitle != null) {
			KomiText(text = subtitle, role = KomiTextRole.Label, color = colors.onSurfaceVariant, fontSize = 10.sp, uppercase = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
		}
	}
}
