package com.yuko.app.ui

import com.yuko.sources.ChapterRef
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterRefTest {
	private fun ch(number: Float, title: String?) = ChapterRef(id = 1, title = title, number = number, url = "/x", sourceId = "S")

	@Test
	fun `display name combines number and title without repeating the number`() {
		assertEquals("Capítulo 12", ch(12f, null).displayName)
		assertEquals("Capítulo 12.5", ch(12.5f, null).displayName)
		assertEquals("Capítulo 12 · La lluvia", ch(12f, "La lluvia").displayName)
		assertEquals("Chapter 12: Rain", ch(12f, "Chapter 12: Rain").displayName)
		assertEquals("Oneshot", ch(0f, "Oneshot").displayName)
		assertEquals("Capítulo", ch(0f, "  ").displayName)
	}
}
