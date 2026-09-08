package com.yuko.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuko.app.R
import com.yuko.app.data.HistoryRepository
import com.yuko.app.data.LibraryEntry
import com.yuko.app.data.LibraryRepository
import com.yuko.app.ui.komi.KomiButton
import com.yuko.app.ui.komi.KomiButtonSize
import com.yuko.app.ui.komi.KomiButtonVariant
import com.yuko.app.ui.komi.KomiSegmented
import com.yuko.app.ui.komi.KomiSegmentedItem
import com.yuko.app.ui.komi.KomiText
import com.yuko.app.ui.komi.KomiTextRole
import com.yuko.app.ui.komi.LocalPersonality
import com.yuko.sources.MangaRef

private enum class Shelf { FAVORITES, LATER, HISTORY }

/** Favourites, read later and reading history in one place. */
@Composable
fun LibraryScreen(contentPadding: PaddingValues, onOpen: (MangaRef) -> Unit) {
	val colors = LocalPersonality.current.colors
	val library by LibraryRepository.items.collectAsState()
	val history by HistoryRepository.items.collectAsState()
	var shelf by rememberSaveable { mutableStateOf(Shelf.FAVORITES) }

	Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
		KomiSegmented(
			selected = shelf,
			items = listOf(
				KomiSegmentedItem(Shelf.FAVORITES, stringResource(R.string.favorites)),
				KomiSegmentedItem(Shelf.LATER, stringResource(R.string.read_later)),
				KomiSegmentedItem(Shelf.HISTORY, stringResource(R.string.history)),
			),
			onSelect = { shelf = it },
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
			fillWidth = true,
		)
		val entries: List<Pair<MangaRef, String?>> = when (shelf) {
			Shelf.FAVORITES -> library.filter { it.category == LibraryEntry.FAVORITE }.sortedByDescending { it.addedAt }.map { it.manga to null }
			Shelf.LATER -> library.filter { it.category == LibraryEntry.LATER }.sortedByDescending { it.addedAt }.map { it.manga to null }
			Shelf.HISTORY -> history.map { it.manga to "${it.chapter.displayName} · ${it.page + 1}/${it.pageCount.coerceAtLeast(1)}" }
		}
		LazyVerticalGrid(
			columns = GridCells.Adaptive(112.dp),
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			if (entries.isEmpty()) {
				item(span = { GridItemSpan(maxLineSpan) }) {
					Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
						KomiText(
							text = when (shelf) {
								Shelf.FAVORITES -> stringResource(R.string.favorites_empty)
								Shelf.LATER -> stringResource(R.string.later_empty)
								Shelf.HISTORY -> stringResource(R.string.history_empty)
							},
							role = KomiTextRole.Body, color = colors.onSurfaceVariant, uppercase = false, textAlign = TextAlign.Center,
						)
					}
				}
			}
			items(entries, key = { it.first.key }) { (manga, subtitle) ->
				Column {
					CoverCard(manga = manga, subtitle = subtitle ?: com.yuko.sources.MangaSources.byId(manga.sourceId)?.name, onClick = { onOpen(manga) })
					if (shelf == Shelf.HISTORY) {
						KomiButton(onClick = { HistoryRepository.remove(manga.key) }, label = stringResource(R.string.remove), size = KomiButtonSize.Sm, variant = KomiButtonVariant.Text)
					}
				}
			}
		}
	}
}
