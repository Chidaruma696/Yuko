package com.yuko.sources

import kotlinx.serialization.Serializable
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag

/** Serializable snapshot of a [Manga], enough to show it and to ask its source for details again. */
@Serializable
data class MangaRef(
	val id: Long,
	val title: String,
	val url: String,
	val publicUrl: String,
	val sourceId: String,
	val coverUrl: String? = null,
	val largeCoverUrl: String? = null,
	val tags: List<TagRef> = emptyList(),
	val contentRating: String? = null,
	val state: String? = null,
	val authors: List<String> = emptyList(),
	val altTitles: List<String> = emptyList(),
	val description: String? = null,
	val rating: Float = -1f,
) {
	val key: String get() = "$sourceId|$id"
	val isNsfw: Boolean get() = contentRating == ContentRating.ADULT.name

	fun toManga(): Manga {
		val source = MangaParserSource.valueOf(sourceId)
		return Manga(
			id = id,
			title = title,
			altTitles = altTitles.toSet(),
			url = url,
			publicUrl = publicUrl,
			rating = rating,
			contentRating = contentRating?.let { runCatching { ContentRating.valueOf(it) }.getOrNull() },
			coverUrl = coverUrl,
			tags = tags.map { MangaTag(it.title, it.key, source) }.toSet(),
			state = state?.let { runCatching { MangaState.valueOf(it) }.getOrNull() },
			authors = authors.toSet(),
			largeCoverUrl = largeCoverUrl,
			description = description,
			chapters = null,
			source = source,
		)
	}
}

@Serializable
data class TagRef(val title: String, val key: String)

fun Manga.toRef(): MangaRef = MangaRef(
	id = id,
	title = title,
	url = url,
	publicUrl = publicUrl,
	sourceId = (source as? MangaParserSource)?.name ?: source.name,
	coverUrl = coverUrl,
	largeCoverUrl = largeCoverUrl,
	tags = tags.map { TagRef(it.title, it.key) },
	contentRating = contentRating?.name,
	state = state?.name,
	authors = authors.toList(),
	altTitles = altTitles.toList(),
	description = description,
	rating = rating,
)

/** Serializable snapshot of a [MangaChapter]. */
@Serializable
data class ChapterRef(
	val id: Long,
	val title: String? = null,
	val number: Float = 0f,
	val volume: Int = 0,
	val url: String,
	val scanlator: String? = null,
	val uploadDate: Long = 0,
	val branch: String? = null,
	val sourceId: String,
) {
	val displayName: String
		get() {
			val t = title?.takeIf { it.isNotBlank() }
			val n = if (number > 0f) (if (number % 1f == 0f) "Capítulo ${number.toInt()}" else "Capítulo $number") else null
			return when {
				t != null && n != null && !t.contains(number.toInt().toString()) -> "$n · $t"
				t != null -> t
				n != null -> n
				else -> "Capítulo"
			}
		}

	fun toChapter(): MangaChapter = MangaChapter(
		id = id,
		title = title,
		number = number,
		volume = volume,
		url = url,
		scanlator = scanlator,
		uploadDate = uploadDate,
		branch = branch,
		source = MangaParserSource.valueOf(sourceId),
	)
}

fun MangaChapter.toRef(): ChapterRef = ChapterRef(
	id = id,
	title = title,
	number = number,
	volume = volume,
	url = url,
	scanlator = scanlator,
	uploadDate = uploadDate,
	branch = branch,
	sourceId = (source as? MangaParserSource)?.name ?: source.name,
)
