package com.yuko.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuko.sources.LoadedSource
import com.yuko.sources.MangaSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.SortOrder

/** What one source answered to a query. */
data class SourceHits(val source: LoadedSource, val items: List<Manga>)

data class GlobalSearchState(
	val query: String = "",
	val isSearching: Boolean = false,
	/** Sources already answered (with results, empty or failed). */
	val done: Int = 0,
	val total: Int = 0,
	/** Only the sources that found something, in the order they answered. */
	val hits: List<SourceHits> = emptyList(),
)

/**
 * One query against every enabled source at once. Each source runs in its own coroutine,
 * a few at a time, with its own timeout; a source that fails or answers nothing is simply
 * left out. Sections appear as the sources answer.
 */
class GlobalSearchViewModel : ViewModel() {

	val state = MutableStateFlow(GlobalSearchState())
	private var job: Job? = null

	fun search(raw: String) {
		val query = raw.trim()
		job?.cancel()
		if (query.isBlank()) { state.value = GlobalSearchState(); return }
		job = viewModelScope.launch {
			val sources = withContext(Dispatchers.Default) {
				val enabled = SourcePrefs.enabledIds()
				MangaSources.all.filter { it.id in enabled && (AppPrefs.showAdult || !it.isNsfw) }
			}
			state.value = GlobalSearchState(query = query, isSearching = true, total = sources.size)
			val gate = Semaphore(MAX_PARALLEL)
			withContext(Dispatchers.IO) {
				coroutineScope {
					for (src in sources) launch {
						val found = gate.withPermit { searchIn(src, query) }
						state.update { s ->
							s.copy(done = s.done + 1, hits = if (found.isEmpty()) s.hits else s.hits + SourceHits(src, found))
						}
					}
				}
			}
			state.update { it.copy(isSearching = false) }
		}
	}

	/** Stops whatever is in flight; what was already found stays on screen. */
	fun cancel() {
		job?.cancel()
		job = null
		state.update { it.copy(isSearching = false) }
	}

	private suspend fun searchIn(src: LoadedSource, query: String): List<Manga> =
		withTimeoutOrNull(SOURCE_TIMEOUT) {
			runCatching {
				val parser = src.parser
				if (!parser.filterCapabilities.isSearchSupported) return@runCatching emptyList()
				val orders = parser.availableSortOrders
				val order = if (SortOrder.RELEVANCE in orders) SortOrder.RELEVANCE else orders.first()
				val filter = if (parser.filterCapabilities.isSearchWithFiltersSupported) SourceFilters.base(parser, SourceFilters.options(src)).copy(query = query) else MangaListFilter(query = query)
				parser.getList(0, order, filter)
			}.getOrDefault(emptyList())
		}.orEmpty()
			.filterNot { AppPrefs.isHidden(it, src) }
			.distinctBy { it.id }
			.take(MAX_PER_SOURCE)

	companion object {
		private const val MAX_PARALLEL = 6
		private const val SOURCE_TIMEOUT = 15_000L
		private const val MAX_PER_SOURCE = 30
	}
}
