package com.yuko.sources

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Host services for the Kotatsu parsers: HTTP, cookies, JavaScript, configuration, bitmaps. */
class YukoLoaderContext(private val app: Application) : MangaLoaderContext() {

	private lateinit var client: OkHttpClient
	private val mainHandler = Handler(Looper.getMainLooper())

	internal fun attach(client: OkHttpClient) {
		this.client = client
	}

	override val httpClient: OkHttpClient get() = client
	override val cookieJar: CookieJar get() = SourcesRuntime.cookieJar

	private val userAgent: String by lazy {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			defaultWebViewUserAgent(app) ?: FALLBACK_UA
		} else {
			var result: String? = null
			val latch = CountDownLatch(1)
			mainHandler.post {
				result = defaultWebViewUserAgent(app)
				latch.countDown()
			}
			latch.await(5, TimeUnit.SECONDS)
			result ?: FALLBACK_UA
		}
	}

	override fun getDefaultUserAgent(): String = userAgent

	override fun encodeBase64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

	override fun decodeBase64(data: String): ByteArray = Base64.decode(data, Base64.DEFAULT)

	@Deprecated("Provide a base url")
	override suspend fun evaluateJs(script: String): String? = evaluateJs("", script, 10_000L)

	/**
	 * Runs [script] in a throwaway WebView on the main thread. The result is what
	 * `evaluateJavascript` returns: a JSON-encoded value (strings keep their quotes), or null.
	 */
	@SuppressLint("SetJavaScriptEnabled")
	override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? =
		withTimeoutOrNull(timeout) {
			suspendCancellableCoroutine { cont ->
				var webView: WebView? = null
				fun finish(value: String?) {
					mainHandler.post { webView?.destroy(); webView = null }
					if (cont.isActive) cont.resume(value)
				}
				mainHandler.post {
					try {
						val wv = WebView(app)
						webView = wv
						wv.settings.javaScriptEnabled = true
						wv.settings.domStorageEnabled = true
						wv.settings.userAgentString = userAgent
						val run = { wv.evaluateJavascript(script) { result -> finish(result?.takeUnless { it == "null" }) } }
						if (baseUrl.isBlank()) {
							run()
						} else {
							wv.webViewClient = object : android.webkit.WebViewClient() {
								override fun onPageFinished(view: WebView, url: String) = run()
							}
							wv.loadDataWithBaseURL(baseUrl, "<html><body></body></html>", "text/html", "utf-8", null)
						}
					} catch (e: Throwable) {
						finish(null)
					}
				}
				cont.invokeOnCancellation { mainHandler.post { webView?.destroy(); webView = null } }
			}
		}

	override fun getConfig(source: MangaSource): MangaSourceConfig = SourceConfig(app, source)

	override fun createBitmap(width: Int, height: Int): Bitmap =
		AndroidBitmap(android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888))

	/** Lets a parser descramble an image: decode, redraw, re-encode as PNG. */
	override fun redrawImageResponse(response: Response, redraw: (image: Bitmap) -> Bitmap): Response {
		val body = response.body
		val bytes = body.bytes()
		val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
			?: return response.newBuilder().body(bytes.toResponseBody(body.contentType())).build()
		val mutable = if (decoded.isMutable) decoded else decoded.copy(android.graphics.Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
		val result = redraw(AndroidBitmap(mutable)) as AndroidBitmap
		val out = ByteArrayOutputStream()
		result.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
		if (result.bitmap !== mutable) mutable.recycle()
		result.bitmap.recycle()
		return response.newBuilder()
			.header("Content-Type", "image/png")
			.removeHeader("Content-Length")
			.body(out.toByteArray().toResponseBody("image/png".toMediaType()))
			.build()
	}

	companion object {
		const val FALLBACK_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
	}
}

/** Parser-side bitmap over an Android bitmap. */
class AndroidBitmap(val bitmap: android.graphics.Bitmap) : Bitmap {
	override val width: Int get() = bitmap.width
	override val height: Int get() = bitmap.height

	override fun drawBitmap(sourceBitmap: Bitmap, src: Rect, dst: Rect) {
		val source = (sourceBitmap as AndroidBitmap).bitmap
		Canvas(bitmap).drawBitmap(
			source,
			android.graphics.Rect(src.left, src.top, src.right, src.bottom),
			android.graphics.Rect(dst.left, dst.top, dst.right, dst.bottom),
			null,
		)
	}
}

/**
 * Per-source settings (domain override, user agent, ...) in SharedPreferences.
 * Anything not stored falls back to the key's default.
 */
class SourceConfig(context: Context, source: MangaSource) : MangaSourceConfig {

	private val prefs = context.getSharedPreferences("source_${source.name}", Context.MODE_PRIVATE)

	@Suppress("UNCHECKED_CAST")
	override fun <T> get(key: ConfigKey<T>): T {
		val default = key.defaultValue
		val stored = prefs.all[key.key] ?: return default
		return when (default) {
			is String -> stored as? String ?: default
			is Boolean -> stored as? Boolean ?: default
			is Int -> stored as? Int ?: default
			is Long -> stored as? Long ?: default
			is Float -> stored as? Float ?: default
			else -> default
		} as T
	}

	fun set(key: String, value: String?) {
		prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
	}
}
