package com.yuko.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.yuko.app.data.HistoryRepository
import com.yuko.app.data.LibraryRepository
import com.yuko.app.download.DownloadRepository
import com.yuko.app.ui.AppPrefs
import com.yuko.app.ui.komi.ThemePrefs
import com.yuko.sources.SourcesRuntime

class YukoApp : Application(), SingletonImageLoader.Factory {

	override fun onCreate() {
		super.onCreate()
		instance = this
		ThemePrefs.init(this)
		AppPrefs.init(this)
		LibraryRepository.init(this)
		HistoryRepository.init(this)
		DownloadRepository.init(this)
		SourcesRuntime.init(this)
	}

	/** Covers and pages go through the sources' OkHttp client so cookies, user agent and Cloudflare apply. */
	override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
		ImageLoader.Builder(context)
			.components {
				add(OkHttpNetworkFetcherFactory(callFactory = { SourceTaggingCallFactory }))
			}
			.crossfade(true)
			.build()

	companion object {
		lateinit var instance: YukoApp
			private set
	}
}

/**
 * Coil cannot tag OkHttp requests, so image requests carry the source in a private header;
 * here it becomes the [MangaSource] tag, which makes the parser add the headers (referer,
 * user agent, cookies) and the image fixes its site needs.
 */
object SourceTaggingCallFactory : okhttp3.Call.Factory {
	const val HEADER = "X-Yuko-Source"

	override fun newCall(request: okhttp3.Request): okhttp3.Call {
		val sourceId = request.header(HEADER)
		val source = sourceId?.let { runCatching { org.koitharu.kotatsu.parsers.model.MangaParserSource.valueOf(it) }.getOrNull() }
		val cleaned = request.newBuilder().removeHeader(HEADER).apply {
			if (source != null) tag(org.koitharu.kotatsu.parsers.model.MangaSource::class.java, source)
		}.build()
		return SourcesRuntime.client.newCall(cleaned)
	}
}
