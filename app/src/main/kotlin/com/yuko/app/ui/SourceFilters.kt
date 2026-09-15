package com.yuko.app.ui

import com.yuko.sources.LoadedSource
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaTag
import java.util.concurrent.ConcurrentHashMap

/**
 * What each source can filter on, asked once per run, and the filters Yuko builds from the user's
 * choices: no adult titles while the switch is off, the excluded genres left out, one tag per liked genre.
 *
 * Lists from a source rarely carry tags or a content rating (those come with the details), so hiding
 * titles afterwards with [AppPrefs.isHidden] misses a lot. Asking the source itself is the only filter
 * that really works; whatever a source cannot do is still checked afterwards.
 */
object SourceFilters {
	private val cache = ConcurrentHashMap<String, MangaListFilterOptions>()

	/** The source's filter options, or null when it has none or did not answer in time. */
	suspend fun options(src: LoadedSource): MangaListFilterOptions? =
		cache[src.id] ?: withTimeoutOrNull(OPTIONS_TIMEOUT) { runCatching { src.parser.getFilterOptions() }.getOrNull() }
			?.also { cache[src.id] = it }

	/** The source's own tag for one of Yuko's genres, if it has one. */
	fun tagFor(options: MangaListFilterOptions?, genre: Genre): MangaTag? =
		options?.availableTags?.firstOrNull { genre.matches(it.title) }

	/**
	 * The filter every plain list gets. A source that rates content is asked to leave adult titles out
	 * while the switch is off; a source that can exclude tags is asked to leave the excluded genres out.
	 * [tags] narrows the list to a genre when the source has a tag for it.
	 */
	fun base(parser: MangaParser, options: MangaListFilterOptions?, tags: Set<MangaTag> = emptySet()): MangaListFilter {
		val ratings = options?.availableContentRating.orEmpty()
		val contentRating = if (!AppPrefs.showAdult && ContentRating.ADULT in ratings) ratings - ContentRating.ADULT else emptySet()
		val exclude = if (options != null && parser.filterCapabilities.isTagsExclusionSupported) {
			AppPrefs.excludedGenres.mapNotNull { id -> Genres.byId[id]?.let { tagFor(options, it) } }.toSet() - tags
		} else emptySet()
		return MangaListFilter(tags = tags, tagsExclude = exclude, contentRating = contentRating)
	}

	private const val OPTIONS_TIMEOUT = 15_000L
}
