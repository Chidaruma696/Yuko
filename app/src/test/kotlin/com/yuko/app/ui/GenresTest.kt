package com.yuko.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenresTest {
	@Test
	fun `normalize strips accents and case`() {
		assertEquals("accion", Genres.normalize("Acción"))
		assertEquals("fantasia", Genres.normalize("FANTASÍA "))
		assertEquals("recuentos de la vida", Genres.normalize("Recuentos de la vida"))
	}

	@Test
	fun `a genre matches its synonyms in any language and inside compound tags`() {
		val accion = Genres.all.first { it.id == "accion" }
		assertTrue(accion.matches("Action"))
		assertTrue(accion.matches("acción"))
		assertTrue(accion.matches("Action/Adventure"))
		assertFalse(accion.matches("Romance"))
		val romance = Genres.all.first { it.id == "romance" }
		assertTrue(romance.matches("Romántico"))
		assertTrue(romance.matches("romantic comedy"))
	}

	@Test
	fun `short synonyms only match whole tags, never fragments`() {
		val gore = Genres.all.first { it.id == "gore" }
		assertTrue(gore.matches("gore"))
		assertFalse("a four-letter synonym needs a whole word", gore.matches("gorey stuff"))
	}

	@Test
	fun `ids are unique and main genres come with a kicker`() {
		assertEquals(Genres.all.size, Genres.all.map { it.id }.toSet().size)
		Genres.all.filter { it.main }.forEach { assertTrue("${it.id} has a kicker", it.kicker.isNotBlank()) }
		assertTrue(Genres.all.any { it.adult })
	}
}
