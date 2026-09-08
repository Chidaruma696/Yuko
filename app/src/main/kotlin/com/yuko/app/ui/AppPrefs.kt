package com.yuko.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.yuko.app.YukoApp
import com.yuko.sources.LoadedSource
import com.yuko.sources.MangaRef
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga

/** App-wide switches, observable from Compose and persisted in SharedPreferences. */
object AppPrefs {
	private const val FILE = "app"

	const val MODE_PAGED = "paged"
	const val MODE_WEBTOON = "webtoon"
	const val SCALE_HEIGHT = "height"
	const val SCALE_WIDTH = "width"
	const val SCALE_SCREEN = "screen"

	var onboardingDone by mutableStateOf(false)
		private set
	var showAdult by mutableStateOf(false)
		private set
	/** Keep Android Open notice on Home; hidden once dismissed, can be shown again from Settings. */
	var kaoBannerVisible by mutableStateOf(true)
		private set
	var parallelDownloads by mutableStateOf(2)
		private set
	/** Right-to-left page order, the way manga is printed. */
	var readerRtl by mutableStateOf(true)
		private set
	var readerMode by mutableStateOf(MODE_PAGED)
		private set
	/** Pages fill the screen height by default; a wider page pans sideways. */
	var readerScale by mutableStateOf(SCALE_HEIGHT)
		private set
	/** Genre ids picked at first start; they drive the "Para ti" row. */
	var selectedGenres by mutableStateOf<Set<String>>(emptySet())
		private set
	/** Genre ids never shown on Home nor in catalogues. */
	var excludedGenres by mutableStateOf<Set<String>>(emptySet())
		private set

	fun init(context: Context) {
		val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		onboardingDone = p.getBoolean("onboarding_done", false)
		showAdult = p.getBoolean("show_adult", false)
		kaoBannerVisible = p.getBoolean("kao_banner", true)
		parallelDownloads = p.getInt("parallel_downloads", 2)
		readerRtl = p.getBoolean("reader_rtl", true)
		readerMode = p.getString("reader_mode", MODE_PAGED) ?: MODE_PAGED
		readerScale = p.getString("reader_scale", SCALE_HEIGHT) ?: SCALE_HEIGHT
		selectedGenres = p.getStringSet("genres", null)?.toSet().orEmpty()
		excludedGenres = p.getStringSet("genres_excluded", null)?.toSet().orEmpty()
	}

	private fun prefs() = YukoApp.instance.getSharedPreferences(FILE, Context.MODE_PRIVATE)

	fun setOnboardingDone() { onboardingDone = true; prefs().edit { putBoolean("onboarding_done", true) } }
	fun updateShowAdult(value: Boolean) { showAdult = value; prefs().edit { putBoolean("show_adult", value) } }
	fun updateKaoBanner(value: Boolean) { kaoBannerVisible = value; prefs().edit { putBoolean("kao_banner", value) } }
	fun updateParallelDownloads(value: Int) { parallelDownloads = value.coerceIn(1, 4); prefs().edit { putInt("parallel_downloads", parallelDownloads) } }
	fun updateReaderRtl(value: Boolean) { readerRtl = value; prefs().edit { putBoolean("reader_rtl", value) } }
	fun updateReaderMode(value: String) { readerMode = value; prefs().edit { putString("reader_mode", value) } }
	fun updateReaderScale(value: String) { readerScale = value; prefs().edit { putString("reader_scale", value) } }
	fun updateSelectedGenres(value: Set<String>) { selectedGenres = value; prefs().edit { putStringSet("genres", value) } }
	fun updateExcludedGenres(value: Set<String>) { excludedGenres = value; prefs().edit { putStringSet("genres_excluded", value) } }

	fun toggleSelectedGenre(id: String) = updateSelectedGenres(if (id in selectedGenres) selectedGenres - id else selectedGenres + id)
	fun toggleExcludedGenre(id: String) = updateExcludedGenres(if (id in excludedGenres) excludedGenres - id else excludedGenres + id)

	private val adultWords = listOf("hentai", "+18", "18+", "adulto", "adult", "smut", "erótico", "erotico", "porn", "xxx", "yaoi hard", "doujinshi")

	/** True when a title should stay hidden while adult content is off. */
	fun isAdult(manga: Manga, source: LoadedSource?): Boolean {
		if (source?.isNsfw == true) return true
		if (manga.contentRating == ContentRating.ADULT) return true
		val tags = manga.tags.map { it.title.lowercase() }
		return tags.any { t -> adultWords.any { it in t } } || manga.title.lowercase().contains("hentai")
	}

	fun isAdult(ref: MangaRef, source: LoadedSource?): Boolean {
		if (source?.isNsfw == true || ref.isNsfw) return true
		val tags = ref.tags.map { it.title.lowercase() }
		return tags.any { t -> adultWords.any { it in t } } || ref.title.lowercase().contains("hentai")
	}

	/** True when any of the manga's tags belongs to an excluded genre. */
	fun isExcluded(manga: Manga): Boolean {
		if (excludedGenres.isEmpty()) return false
		val genres = Genres.of(manga.tags.map { it.title })
		return genres.any { it in excludedGenres }
	}

	/** Hidden by either the adult switch or the excluded genres. */
	fun isHidden(manga: Manga, source: LoadedSource?): Boolean =
		(!showAdult && isAdult(manga, source)) || isExcluded(manga)
}
