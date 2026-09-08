package com.yuko.sources

import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource

/** One source Yuko exposes, wrapping a Kotatsu parser. */
class LoadedSource(val source: MangaParserSource) {
	/** Stable key, the enum constant name. */
	val id: String get() = source.name
	val name: String get() = source.title
	val lang: String get() = source.locale
	val contentType: ContentType get() = source.contentType
	val isNsfw: Boolean get() = source.contentType == ContentType.HENTAI
	val isBroken: Boolean get() = source.isBroken
	val parser: MangaParser get() = SourcesRuntime.parser(source)
	val domain: String get() = runCatching { parser.domain }.getOrDefault("")
	val homeUrl: String get() = domain.takeIf { it.isNotBlank() }?.let { "https://$it/" } ?: ""

	override fun equals(other: Any?): Boolean = other is LoadedSource && other.source == source
	override fun hashCode(): Int = source.hashCode()
	override fun toString(): String = name
}

/**
 * Registry of the sources Yuko shows. The Kotatsu library carries ~1300 parsers in 40
 * languages; Yuko starts with the Spanish ones that are not flagged as broken upstream.
 */
object MangaSources {

	/** Languages exposed to the user, in display order. */
	val languages: List<String> = listOf("es")

	val all: List<LoadedSource> by lazy {
		MangaParserSource.entries
			.filter { it.locale in languages && !it.isBroken }
			.map { LoadedSource(it) }
			.sortedBy { it.name.lowercase() }
	}

	/** Every parser in the library, including other languages and broken ones. */
	val everything: List<LoadedSource> by lazy { MangaParserSource.entries.map { LoadedSource(it) } }

	private val byId: Map<String, LoadedSource> by lazy { everything.associateBy { it.id } }

	fun byId(id: String): LoadedSource? = byId[id]
}
