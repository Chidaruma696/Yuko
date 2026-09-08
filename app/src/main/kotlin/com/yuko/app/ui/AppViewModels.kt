package com.yuko.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuko.sources.ChapterRef
import com.yuko.sources.LoadedSource
import com.yuko.sources.MangaRef
import com.yuko.sources.MangaSources
import com.yuko.sources.toRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.SortOrder

enum class BrowseMode { POPULAR, LATEST, SEARCH }

data class BrowseState(
	val items: List<Manga> = emptyList(),
	val hasNext: Boolean = true,
	val isLoading: Boolean = false,
	val error: String? = null,
	val mode: BrowseMode = BrowseMode.POPULAR,
	val query: String = "",
	val supportsLatest: Boolean = true,
	val supportsSearch: Boolean = true,
)

/** Catalogue of one source: popular / latest / search with offset paging. */
class BrowseViewModel : ViewModel() {

	val state = MutableStateFlow(BrowseState())
	private var source: LoadedSource? = null
	private var job: Job? = null
	private var offset = 0

	fun open(source: LoadedSource, mode: BrowseMode = BrowseMode.POPULAR, query: String = "") {
		if (this.source?.id == source.id && state.value.mode == mode && state.value.query == query && state.value.items.isNotEmpty()) return
		this.source = source
		job?.cancel()
		offset = 0
		val parser = runCatching { source.parser }.getOrNull()
		state.value = BrowseState(
			mode = mode,
			query = query,
			supportsLatest = parser?.availableSortOrders?.contains(SortOrder.UPDATED) ?: true,
			supportsSearch = parser?.filterCapabilities?.isSearchSupported ?: true,
		)
		loadMore()
	}

	fun loadMore() {
		val src = source ?: return
		val s = state.value
		if (s.isLoading || !s.hasNext) return
		job = viewModelScope.launch {
			state.update { it.copy(isLoading = true, error = null) }
			try {
				val result = withContext(Dispatchers.IO) {
					val parser = src.parser
					val orders = parser.availableSortOrders
					when (s.mode) {
						BrowseMode.POPULAR -> {
							val order = listOf(SortOrder.POPULARITY, SortOrder.POPULARITY_WEEK, SortOrder.POPULARITY_MONTH, SortOrder.RATING).firstOrNull { it in orders } ?: orders.first()
							parser.getList(offset, order, MangaListFilter())
						}
						BrowseMode.LATEST -> parser.getList(offset, SortOrder.UPDATED, MangaListFilter())
						BrowseMode.SEARCH -> {
							val order = if (SortOrder.RELEVANCE in orders) SortOrder.RELEVANCE else orders.first()
							parser.getList(offset, order, MangaListFilter(query = s.query))
						}
					}
				}
				val visible = result.filterNot { AppPrefs.isHidden(it, src) }
				offset += result.size
				state.update {
					val merged = (it.items + visible).distinctBy { m -> m.id }
					it.copy(items = merged, hasNext = result.isNotEmpty() && merged.size > it.items.size || (result.isNotEmpty() && visible.isEmpty()), isLoading = false)
				}
				// a page made only of hidden titles: keep going so the grid is not left empty
				if (result.isNotEmpty() && visible.isEmpty() && offset < 400) loadMore()
			} catch (e: kotlinx.coroutines.CancellationException) {
				throw e
			} catch (e: Throwable) {
				state.update { it.copy(isLoading = false, error = e.message ?: e.javaClass.simpleName) }
			}
		}
	}
}

data class DetailsState(
	val manga: MangaRef? = null,
	val chapters: List<ChapterRef> = emptyList(),
	val branches: List<String> = emptyList(),
	val isLoading: Boolean = false,
	val error: String? = null,
)

/** Manga page: details and the chapter list. Chapters are kept oldest first. */
class DetailsViewModel : ViewModel() {

	val state = MutableStateFlow(DetailsState())
	private var source: LoadedSource? = null

	fun open(source: LoadedSource, manga: MangaRef) {
		if (this.source?.id == source.id && state.value.manga?.id == manga.id && state.value.chapters.isNotEmpty()) return
		this.source = source
		state.value = DetailsState(manga = manga, isLoading = true)
		viewModelScope.launch {
			try {
				val details = withContext(Dispatchers.IO) { source.parser.getDetails(manga.toManga()) }
				val chapters = details.chapters.orEmpty()
				val branches = chapters.mapNotNull { it.branch }.distinct()
				state.update {
					it.copy(
						manga = details.toRef().let { ref -> if (ref.coverUrl.isNullOrBlank()) ref.copy(coverUrl = manga.coverUrl) else ref },
						chapters = chapters.map { c -> c.toRef() }.sortedWith(compareBy({ it.volume }, { it.number })),
						branches = branches,
						isLoading = false,
					)
				}
			} catch (e: kotlinx.coroutines.CancellationException) {
				throw e
			} catch (e: Throwable) {
				state.update { it.copy(isLoading = false, error = e.message ?: e.javaClass.simpleName) }
			}
		}
	}

	fun retry() {
		val src = source ?: return
		val m = state.value.manga ?: return
		source = null
		open(src, m)
	}
}

/** All sources of the enabled languages. */
class SourcesViewModel : ViewModel() {
	val sources = MutableStateFlow<List<LoadedSource>?>(null)

	init {
		viewModelScope.launch(Dispatchers.Default) { sources.value = MangaSources.all }
	}
}
