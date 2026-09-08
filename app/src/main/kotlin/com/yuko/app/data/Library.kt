package com.yuko.app.data

import android.content.Context
import com.yuko.sources.ChapterRef
import com.yuko.sources.MangaRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

/** A manga saved by the user, either as a favourite or to read later. */
@Serializable
data class LibraryEntry(
	val manga: MangaRef,
	val category: String,
	val addedAt: Long = System.currentTimeMillis(),
) {
	companion object {
		const val FAVORITE = "favorite"
		const val LATER = "later"
	}
}

/** Favourites and "read later", as a JSON file in the app's private storage. */
object LibraryRepository {

	val items = MutableStateFlow<List<LibraryEntry>>(emptyList())
	private lateinit var file: File
	private val lock = Any()

	fun init(context: Context) {
		file = File(context.filesDir, "library.json")
		items.value = runCatching { json.decodeFromString<List<LibraryEntry>>(file.readText()) }.getOrDefault(emptyList())
	}

	fun categoryOf(key: String): String? = items.value.firstOrNull { it.manga.key == key }?.category

	fun contains(key: String): Boolean = items.value.any { it.manga.key == key }

	/** Sets the category, or removes the entry when it already has that category. */
	fun toggle(manga: MangaRef, category: String) = synchronized(lock) {
		val current = categoryOf(manga.key)
		items.update { list ->
			val without = list.filterNot { it.manga.key == manga.key }
			if (current == category) without else without + LibraryEntry(manga, category)
		}
		persist()
	}

	fun remove(key: String) = synchronized(lock) {
		items.update { list -> list.filterNot { it.manga.key == key } }
		persist()
	}

	private fun persist() {
		runCatching { file.writeText(json.encodeToString(items.value)) }
	}
}

/** Where the user is in a manga: last chapter opened and page within it. */
@Serializable
data class HistoryEntry(
	val manga: MangaRef,
	val chapter: ChapterRef,
	val page: Int,
	val pageCount: Int,
	val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class HistoryFile(
	val entries: List<HistoryEntry> = emptyList(),
	val read: Map<String, Set<Long>> = emptyMap(),
)

/** Reading history and the set of chapters already read, per manga. */
object HistoryRepository {

	/** Most recent first. */
	val items = MutableStateFlow<List<HistoryEntry>>(emptyList())
	/** manga key -> ids of chapters read to the end. */
	val read = MutableStateFlow<Map<String, Set<Long>>>(emptyMap())
	private lateinit var file: File
	private val lock = Any()

	fun init(context: Context) {
		file = File(context.filesDir, "history.json")
		val data = runCatching { json.decodeFromString<HistoryFile>(file.readText()) }.getOrDefault(HistoryFile())
		items.value = data.entries.sortedByDescending { it.updatedAt }
		read.value = data.read
	}

	fun progress(mangaKey: String): HistoryEntry? = items.value.firstOrNull { it.manga.key == mangaKey }

	fun isRead(mangaKey: String, chapterId: Long): Boolean = read.value[mangaKey]?.contains(chapterId) == true

	fun update(manga: MangaRef, chapter: ChapterRef, page: Int, pageCount: Int) = synchronized(lock) {
		items.update { list ->
			(listOf(HistoryEntry(manga, chapter, page, pageCount)) + list.filterNot { it.manga.key == manga.key }).take(200)
		}
		persist()
	}

	fun markRead(mangaKey: String, chapterId: Long, isRead: Boolean = true) = synchronized(lock) {
		read.update { map ->
			val set = map[mangaKey].orEmpty()
			map + (mangaKey to if (isRead) set + chapterId else set - chapterId)
		}
		persist()
	}

	fun remove(mangaKey: String) = synchronized(lock) {
		items.update { list -> list.filterNot { it.manga.key == mangaKey } }
		persist()
	}

	private fun persist() {
		runCatching { file.writeText(json.encodeToString(HistoryFile(items.value, read.value))) }
	}
}
