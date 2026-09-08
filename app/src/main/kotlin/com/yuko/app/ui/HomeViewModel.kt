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
import org.koitharu.kotatsu.parsers.model.MangaListFilter
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

/**
 * Home feed: for every enabled source, the first page of popular and of recently updated
 * titles, mixed into "Para ti" (titles matching the chosen genres, random picks otherwise),
 * "Recientes" (interleaved) and one "Populares en X" row per source.
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
			val results = withContext(Dispatchers.IO) {
				coroutineScope {
					enabled.map { src ->
						async {
							val parser = runCatching { src.parser }.getOrNull()
							val orders = parser?.availableSortOrders.orEmpty()
							val popular = if (parser != null) {
								val order = listOf(SortOrder.POPULARITY, SortOrder.POPULARITY_WEEK, SortOrder.POPULARITY_MONTH, SortOrder.RATING).firstOrNull { it in orders }
									?: orders.firstOrNull()
								if (order != null) fetch(src) { parser.getList(0, order, MangaListFilter()) } else emptyList()
							} else emptyList()
							val latest = if (parser != null && SortOrder.UPDATED in orders) fetch(src) { parser.getList(0, SortOrder.UPDATED, MangaListFilter()) } else emptyList()
							state.update { it.copy(loadedSources = it.loadedSources + 1) }
							Triple(src, popular, latest)
						}
					}.map { it.await() }
				}
			}
			val rows = results.filter { it.second.isNotEmpty() }.map { (src, popular, _) ->
				FeedRow(title = "Populares en ${src.name}", kicker = "人気", items = popular.take(20).map { FeedManga(it, src) }, source = src)
			}
			val latest = interleave(results.map { (src, _, latest) -> latest.map { FeedManga(it, src) } }).distinctBy { ChapterMerge.normalizeTitle(it.manga.title) }.take(30)
			val pool = results.flatMap { (src, popular, latest) -> (popular + latest).map { FeedManga(it, src) } }
				.filter { !it.manga.coverUrl.isNullOrBlank() }
				.distinctBy { ChapterMerge.normalizeTitle(it.manga.title) }
			val wanted = AppPrefs.selectedGenres
			val matching = if (wanted.isEmpty()) emptyList() else pool.filter { fm -> Genres.of(fm.manga.tags.map { it.title }).any { it in wanted } }
			val recommended = (matching.shuffled(random) + pool.shuffled(random)).distinctBy { it.key }.take(12)
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
	}
}
