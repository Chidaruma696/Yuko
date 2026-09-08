package com.yuko.app.ui

import com.yuko.sources.ChapterRef
import com.yuko.sources.LoadedSource
import com.yuko.sources.MangaRef
import com.yuko.sources.MangaSources
import com.yuko.sources.toRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * Chapters of one title gathered from every enabled source, so a series that one site has
 * only up to chapter 51 continues with chapters 52+ from another. Chapters are matched by
 * number (or by title when a source does not number them); the manga's own source wins on
 * duplicates and the rest are kept as alternates for when a server fails.
 */
data class MergedChapters(
	/** Oldest first; each chapter carries the source it comes from. */
	val chapters: List<ChapterRef>,
	/** mergeKey -> other copies of the same chapter, best first. */
	val alternates: Map<String, List<ChapterRef>>,
	/** Names of the sources that contributed chapters besides the primary. */
	val extraSources: List<String>,
)

object ChapterMerge {

	/** Chapters with the same key are the same chapter, whatever site they come from. */
	fun key(ch: ChapterRef): String = when {
		ch.number > 0f -> "n:" + (if (ch.number % 1f == 0f) ch.number.toInt().toString() else ch.number.toString())
		else -> "t:" + Genres.normalize(ch.title.orEmpty()).replace(Regex("[^a-z0-9]+"), " ").trim()
	}

	fun normalizeTitle(s: String): String = Genres.normalize(s).replace(Regex("[^a-z0-9]+"), " ").trim()

	/** Finds the same title in the other enabled sources and returns their chapter lists. */
	suspend fun findElsewhere(primary: LoadedSource, manga: MangaRef, onlyEnabled: Boolean = true): List<Pair<LoadedSource, List<ChapterRef>>> = withContext(Dispatchers.IO) {
		val wanted = normalizeTitle(manga.title)
		if (wanted.length < 3) return@withContext emptyList()
		val enabled = SourcePrefs.enabledIds()
		val candidates = MangaSources.all.filter { it.id != primary.id && (!onlyEnabled || it.id in enabled) && (AppPrefs.showAdult || !it.isNsfw) }
		coroutineScope {
			candidates.map { src ->
				async {
					withTimeoutOrNull(SEARCH_TIMEOUT) {
						runCatching {
							val parser = src.parser
							if (!parser.filterCapabilities.isSearchSupported) return@runCatching null
							val orders = parser.availableSortOrders
							val order = if (SortOrder.RELEVANCE in orders) SortOrder.RELEVANCE else orders.first()
							val results = parser.getList(0, order, MangaListFilter(query = manga.title))
							val match = pickMatch(results, wanted, manga) ?: return@runCatching null
							val details = parser.getDetails(match)
							val chapters = details.chapters.orEmpty().map { it.toRef() }
							if (chapters.isEmpty()) null else src to chapters
						}.getOrNull()
					}
				}
			}.mapNotNull { it.await() }
		}
	}

	private fun pickMatch(results: List<Manga>, wanted: String, manga: MangaRef): Manga? {
		val alts = manga.altTitles.map(::normalizeTitle)
		return results.firstOrNull { normalizeTitle(it.title) == wanted }
			?: results.firstOrNull { m -> m.altTitles.any { normalizeTitle(it) == wanted } || alts.any { it.isNotBlank() && it == normalizeTitle(m.title) } }
			?: results.firstOrNull { val t = normalizeTitle(it.title); t.startsWith(wanted) || wanted.startsWith(t) && t.length >= wanted.length * 0.8 }
	}

	/** Merges the primary list with the others; primary wins, others fill gaps and become alternates. */
	fun merge(primary: List<ChapterRef>, others: List<Pair<LoadedSource, List<ChapterRef>>>): MergedChapters {
		val chosen = LinkedHashMap<String, ChapterRef>()
		val alternates = HashMap<String, MutableList<ChapterRef>>()
		primary.forEach { ch -> chosen.putIfAbsent(key(ch), ch) }
		val used = LinkedHashSet<String>()
		for ((src, list) in others) {
			for (ch in list) {
				val k = key(ch)
				if (chosen.containsKey(k)) {
					alternates.getOrPut(k) { mutableListOf() } += ch
				} else {
					chosen[k] = ch
					used += src.name
				}
			}
		}
		// primary copies are also alternates of the chapters that other sources supplied
		val merged = chosen.values.sortedWith(compareBy({ if (it.number > 0f) 0 else 1 }, { it.number }, { it.volume }))
		return MergedChapters(merged, alternates, used.toList())
	}

	private const val SEARCH_TIMEOUT = 15_000L
}
