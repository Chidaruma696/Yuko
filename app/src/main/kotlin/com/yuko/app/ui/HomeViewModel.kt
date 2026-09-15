package com.yuko.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuko.app.YukoApp
import com.yuko.sources.LoadedSource
import com.yuko.sources.MangaSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.SortOrder
import kotlin.random.Random

/** Which sources feed the Home screen. A curated set is on by default. */
object SourcePrefs {
	private const val FILE = "sources"
	private const val KEY = "enabled"

	/** Enum names of the Spanish sources a reader expects to see first. */
	private val defaultEnabled = listOf(
		"TUMANGAONLINE", "LECTORMANGA", "INMANGA", "MANGAONI", "MANGAFIRE_ESLA", "MANGAFIRE_ES",
		"WEBTOONS_ES", "MANGAPLUSPARSER_ES", "MANHWALATINO", "TU_MANHWAS", "SAMURAISCAN", "MANGABALL_ES",
	)

	private fun prefs() = YukoApp.instance.getSharedPreferences(FILE, Context.MODE_PRIVATE)

	fun enabledIds(): Set<String> {
		val stored = prefs().getStringSet(KEY, null)
		if (stored != null) return stored.toSet()
		val all = MangaSources.all
		val curated = all.filter { it.id in defaultEnabled && !it.isNsfw }
		return (curated.ifEmpty { all.filterNot { it.isNsfw }.take(6) }).map { it.id }.toSet()
	}

	fun setEnabled(id: String, enabled: Boolean) {
		val current = enabledIds().toMutableSet()
		if (enabled) current += id else current -= id
		prefs().edit().putStringSet(KEY, current).apply()
	}
}

data class FeedManga(val manga: Manga, val source: LoadedSource) {
	val key: String get() = "${source.id}|${manga.id}"
}

data class FeedRow(val title: String, val kicker: String?, val items: List<FeedManga>, val source: LoadedSource? = null)

data class HomeState(
	val recommended: List<FeedManga> = emptyList(),
	val latest: List<FeedManga> = emptyList(),
	val rows: List<FeedRow> = emptyList(),
	val isLoading: Boolean = false,
	val loadedSources: Int = 0,
	val enabledSources: Int = 0,
	val error: String? = null,
)

/** What one source answered for Home: its popular and latest lists, plus one list per liked genre it has a tag for. */
private class SourceResult(
	val source: LoadedSource,
	val popular: List<Manga> = emptyList(),
	val latest: List<Manga> = emptyList(),
	val byGenre: Map<String, List<Manga>> = emptyMap(),
)

/**
 * Home feed. For every enabled source: the first page of popular and of recently updated titles,
 * and one page per liked genre the source can filter by. From those come "Para ti" (only titles that
 * belong to a liked genre; nothing random fills the gaps), one row per liked genre mixing sources,
 * "Recientes" (interleaved) and one "Populares en X" row per source. Adult titles and excluded genres
 * are asked out at the source when it can, and dropped afterwards when it cannot.
 */
class HomeViewModel : ViewModel() {

	val state = MutableStateFlow(HomeState())
	private var job: Job? = null
	private var seed = Random.nextInt()

	fun load(force: Boolean = false) {
		if (!force && (state.value.rows.isNotEmpty() || job?.isActive == true)) return
		job?.cancel()
		if (force) seed = Random.nextInt()
		job = viewModelScope.launch {
			state.update { it.copy(isLoading = true, error = null, loadedSources = 0) }
			val enabled = withContext(Dispatchers.Default) {
				val ids = SourcePrefs.enabledIds()
				MangaSources.all.filter { it.id in ids && (AppPrefs.showAdult || !it.isNsfw) }
			}
			state.update { it.copy(enabledSources = enabled.size) }
			if (enabled.isEmpty()) {
				state.update { it.copy(isLoading = false, error = "No hay fuentes activas") }
				return@launch
			}
			val random = Random(seed)
			val liked = AppPrefs.selectedGenres.mapNotNull { Genres.byId[it] }.filter { AppPrefs.showAdult || !it.adult }
			// A few liked genres per load, so a shuffle brings different rows.
			val rowGenres = liked.shuffled(random).take(GENRE_ROWS)
			val results = withContext(Dispatchers.IO) {
				coroutineScope {
					enabled.map { src ->
						async {
							val result = runCatching { loadSource(src, rowGenres) }.getOrDefault(SourceResult(src))
							state.update { it.copy(loadedSources = it.loadedSources + 1) }
							result
						}
					}.map { it.await() }
				}
			}
			val genreRows = rowGenres.mapNotNull { genre ->
				val lists = results.mapNotNull { r -> r.byGenre[genre.id]?.map { FeedManga(it, r.source) } }
				val items = interleave(lists).filter { !it.manga.coverUrl.isNullOrBlank() }.distinctBy { ChapterMerge.normalizeTitle(it.manga.title) }.take(20)
				if (items.isEmpty()) null else FeedRow(title = genre.name, kicker = genre.kicker, items = items)
			}
			val sourceRows = results.filter { it.popular.isNotEmpty() }.map { r ->
				FeedRow(title = "Populares en ${r.source.name}", kicker = "人気", items = r.popular.take(20).map { FeedManga(it, r.source) }, source = r.source)
			}
			val latest = interleave(results.map { r -> r.latest.map { FeedManga(it, r.source) } }).distinctBy { ChapterMerge.normalizeTitle(it.manga.title) }.take(30)
			// "Para ti": what came in by a liked genre, plus anything else whose own tags name one. Nothing random any more.
			val likedIds = liked.map { it.id }.toSet()
			val fromGenres = results.flatMap { r -> r.byGenre.values.flatten().map { FeedManga(it, r.source) } }
			val tagged = if (likedIds.isEmpty()) emptyList() else results
				.flatMap { r -> (r.popular + r.latest).map { FeedManga(it, r.source) } }
				.filter { fm -> Genres.of(fm.manga.tags.map { it.title }).any { it in likedIds } }
			val recommended = (fromGenres + tagged)
				.filter { !it.manga.coverUrl.isNullOrBlank() }
				.distinctBy { ChapterMerge.normalizeTitle(it.manga.title) }
				.shuffled(random)
				.take(12)
			val rows = genreRows + sourceRows
			state.update {
				it.copy(
					recommended = recommended,
					latest = latest,
					rows = rows,
					isLoading = false,
					error = if (rows.isEmpty() && latest.isEmpty()) "Ninguna fuente respondió. Revisa la conexión." else null,
				)
			}
		}
	}

	/** One source, its requests one after another so no site gets hammered; sources run in parallel. */
	private suspend fun loadSource(src: LoadedSource, rowGenres: List<Genre>): SourceResult {
		val parser = runCatching { src.parser }.getOrNull() ?: return SourceResult(src)
		val options = SourceFilters.options(src)
		val orders = parser.availableSortOrders
		val popularOrder = listOf(SortOrder.POPULARITY, SortOrder.POPULARITY_WEEK, SortOrder.POPULARITY_MONTH, SortOrder.RATING).firstOrNull { it in orders }
			?: orders.firstOrNull()
		val filter = SourceFilters.base(parser, options)
		val popular = if (popularOrder != null) fetch(src) { parser.getList(0, popularOrder, filter) } else emptyList()
		val latest = if (SortOrder.UPDATED in orders) fetch(src) { parser.getList(0, SortOrder.UPDATED, filter) } else emptyList()
		val byGenre = HashMap<String, List<Manga>>()
		if (popularOrder != null) {
			for (genre in rowGenres) {
				val tag = SourceFilters.tagFor(options, genre) ?: continue
				val list = fetch(src) { parser.getList(0, popularOrder, SourceFilters.base(parser, options, setOf(tag))) }
				if (list.isNotEmpty()) byGenre[genre.id] = list
			}
		}
		return SourceResult(src, popular, latest, byGenre)
	}

	private suspend fun fetch(src: LoadedSource, call: suspend () -> List<Manga>): List<Manga> =
		withTimeoutOrNull(SOURCE_TIMEOUT) { runCatching { call() }.getOrNull() }
			.orEmpty()
			.filterNot { AppPrefs.isHidden(it, src) }

	private fun <T> interleave(lists: List<List<T>>): List<T> {
		val out = ArrayList<T>()
		val iterators = lists.map { it.iterator() }
		while (iterators.any { it.hasNext() }) {
			for (it in iterators) if (it.hasNext()) out += it.next()
		}
		return out
	}

	companion object {
		private const val SOURCE_TIMEOUT = 20_000L
		private const val GENRE_ROWS = 4
	}
}
