package com.yuko.app.ui

import com.yuko.sources.ChapterRef
import com.yuko.sources.LoadedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.MangaParserSource

class ChapterMergeTest {
	private val a = MangaParserSource.entries[0]
	private val b = MangaParserSource.entries[1]
	private fun ch(number: Float, src: MangaParserSource, title: String? = null, volume: Int = 0) =
		ChapterRef(id = (number * 1000).toLong() + src.ordinal, title = title, number = number, volume = volume, url = "/$number", sourceId = src.name)

	@Test
	fun `chapters match by number, decimals kept, or by normalised title`() {
		assertEquals("n:52", ChapterMerge.key(ch(52f, a)))
		assertEquals("n:52", ChapterMerge.key(ch(52f, b, title = "Capítulo 52: El regreso")))
		assertEquals("n:52.5", ChapterMerge.key(ch(52.5f, a)))
		assertEquals("t:extra el regreso", ChapterMerge.key(ch(0f, a, title = "Extra: ¡El Regreso!")))
		assertEquals(ChapterMerge.key(ch(0f, a, title = "EXTRA - el regreso")), ChapterMerge.key(ch(0f, b, title = "extra el regreso")))
	}

	@Test
	fun `the primary source wins, others fill the gaps and become alternates`() {
		val primary = listOf(ch(1f, a), ch(2f, a), ch(3f, a))
		val other = LoadedSource(b) to listOf(ch(2f, b), ch(3f, b), ch(4f, b), ch(5f, b))
		val merged = ChapterMerge.merge(primary, listOf(other))
		assertEquals(listOf(1f, 2f, 3f, 4f, 5f), merged.chapters.map { it.number })
		assertEquals(listOf(a.name, a.name, a.name, b.name, b.name), merged.chapters.map { it.sourceId })
		assertEquals(listOf(b.name), merged.alternates["n:2"]?.map { it.sourceId })
		assertEquals("a chapter only one site has needs no alternate", null, merged.alternates["n:4"])
		assertEquals(listOf(LoadedSource(b).name), merged.extraSources)
	}

	@Test
	fun `merged chapters come oldest first, unnumbered extras last`() {
		val primary = listOf(ch(3f, a), ch(1f, a), ch(0f, a, title = "Oneshot"))
		val merged = ChapterMerge.merge(primary, listOf(LoadedSource(b) to listOf(ch(2f, b), ch(1.5f, b))))
		assertEquals(listOf(1f, 1.5f, 2f, 3f, 0f), merged.chapters.map { it.number })
		assertEquals("Oneshot", merged.chapters.last().title)
	}

	@Test
	fun `a source that only repeats what the primary has is not an extra source`() {
		val primary = listOf(ch(1f, a), ch(2f, a))
		val merged = ChapterMerge.merge(primary, listOf(LoadedSource(b) to listOf(ch(1f, b), ch(2f, b))))
		assertTrue(merged.extraSources.isEmpty())
		assertEquals(2, merged.chapters.size)
		assertEquals(2, merged.alternates.size)
	}

	@Test
	fun `titles normalise accents, case and punctuation`() {
		assertEquals("one piece", ChapterMerge.normalizeTitle("  ONE  PIECE!! "))
		assertEquals("kimetsu no yaiba", ChapterMerge.normalizeTitle("Kimetsu no Yaiba"))
		assertEquals("cancion de hielo", ChapterMerge.normalizeTitle("Canción de Hielo"))
	}
}
