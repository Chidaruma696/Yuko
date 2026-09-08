package com.yuko.sources

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebSettings
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Everything the Kotatsu parsers need from their host, created once at app start:
 * an OkHttp client that shares cookies with the WebView (so a Cloudflare clearance obtained
 * there is reused), a loader context, and a cache of parser instances.
 */
object SourcesRuntime {

	lateinit var app: Application
		private set

	val cookieJar = WebViewCookieJar()

	lateinit var client: OkHttpClient
		private set

	lateinit var loaderContext: YukoLoaderContext
		private set

	private val parsers = ConcurrentHashMap<MangaParserSource, MangaParser>()

	fun init(app: Application) {
		this.app = app
		loaderContext = YukoLoaderContext(app)
		client = OkHttpClient.Builder()
			.cookieJar(cookieJar)
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.callTimeout(3, TimeUnit.MINUTES)
			.addInterceptor(UserAgentInterceptor { loaderContext.getDefaultUserAgent() })
			.addInterceptor(ParserHeadersInterceptor())
			.addInterceptor(CloudflareInterceptor(app, cookieJar) { loaderContext.getDefaultUserAgent() })
			.addInterceptor(BrotliInterceptor)
			.build()
		loaderContext.attach(client)
	}

	/** One parser per source, created lazily. */
	fun parser(source: MangaParserSource): MangaParser =
		parsers.getOrPut(source) { loaderContext.newParserInstance(source) }

	/** The WebView user agent, once known. */
	val userAgent: String get() = loaderContext.getDefaultUserAgent()
}

/** Adds the default user agent when a request has none. */
private class UserAgentInterceptor(private val userAgent: () -> String) : Interceptor {
	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		if (request.header("User-Agent") != null) return chain.proceed(request)
		return chain.proceed(request.newBuilder().header("User-Agent", userAgent()).build())
	}
}

/**
 * Parsers tag their requests with their [MangaSource]; each parser is itself an OkHttp
 * interceptor that adds the headers (referer, user agent, ...) its site expects.
 */
private class ParserHeadersInterceptor : Interceptor {
	override fun intercept(chain: Interceptor.Chain): Response {
		val source = chain.request().tag(MangaSource::class.java) as? MangaParserSource
			?: return chain.proceed(chain.request())
		return SourcesRuntime.parser(source).intercept(chain)
	}
}

/**
 * Cookie jar backed by the WebView's [CookieManager], so cookies set by a Cloudflare
 * challenge solved in a WebView are visible to OkHttp and vice versa.
 */
class WebViewCookieJar : CookieJar {

	private val manager: CookieManager by lazy { CookieManager.getInstance() }

	override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
		val urlString = url.toString()
		cookies.forEach { manager.setCookie(urlString, it.toString()) }
	}

	override fun loadForRequest(url: HttpUrl): List<Cookie> = get(url)

	fun get(url: HttpUrl): List<Cookie> {
		val cookies = manager.getCookie(url.toString()) ?: return emptyList()
		return cookies.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
	}

	/** Removes the named cookies (or all of them) for a URL. Returns how many were removed. */
	fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
		val urlString = url.toString()
		val cookies = manager.getCookie(urlString) ?: return 0
		fun List<String>.filterNames(): List<String> = when (cookieNames) {
			null -> this
			else -> filter { it in cookieNames }
		}
		return cookies.split(";")
			.map { it.substringBefore("=").trim() }
			.filterNames()
			.onEach { manager.setCookie(urlString, "$it=;Max-Age=$maxAge") }
			.count()
	}

	fun removeAll() {
		manager.removeAllCookies {}
	}
}

internal fun defaultWebViewUserAgent(app: Application): String? = try {
	WebSettings.getDefaultUserAgent(app)
} catch (_: Throwable) {
	null
}
