package com.yuko.sources

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * When a site answers with a Cloudflare challenge (403/503 from a cloudflare server), load the
 * page in a hidden WebView so it solves the JavaScript challenge, wait for the `cf_clearance`
 * cookie and retry the request. Cookies are shared through [WebViewCookieJar].
 */
class CloudflareInterceptor(
	private val context: Context,
	private val cookieJar: WebViewCookieJar,
	private val userAgent: () -> String,
) : Interceptor {

	private val mainHandler = Handler(Looper.getMainLooper())

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val response = chain.proceed(request)
		if (response.code !in ERROR_CODES || response.header("Server")?.lowercase(Locale.ROOT)?.contains("cloudflare") != true) {
			return response
		}
		if (!supportsWebView()) return response
		try {
			response.close()
			cookieJar.remove(request.url, COOKIE_NAMES, 0)
			val oldCookie = cookieJar.get(request.url).firstOrNull { it.name == "cf_clearance" }
			resolveWithWebView(request, oldCookie)
			return chain.proceed(request)
		} catch (e: CloudflareBypassException) {
			throw IOException("No se pudo pasar la protección de Cloudflare de ${request.url.host}", e)
		} catch (e: Exception) {
			throw IOException(e)
		}
	}

	private fun supportsWebView(): Boolean = try {
		context.packageManager.hasSystemFeature("android.software.webview") && WebSettings.getDefaultUserAgent(context).isNotEmpty()
	} catch (_: Throwable) {
		false
	}

	@SuppressLint("SetJavaScriptEnabled")
	private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
		val latch = CountDownLatch(1)
		var webView: WebView? = null
		var challengeFound = false
		var bypassed = false
		val url = originalRequest.url.toString()
		val headers = safeHeaders(originalRequest.headers)

		mainHandler.post {
			val wv = WebView(context)
			webView = wv
			with(wv.settings) {
				javaScriptEnabled = true
				domStorageEnabled = true
				databaseEnabled = true
				useWideViewPort = true
				loadWithOverviewMode = true
				userAgentString = originalRequest.header("User-Agent") ?: userAgent()
			}
			wv.webViewClient = object : WebViewClient() {
				override fun onPageFinished(view: WebView, finishedUrl: String) {
					val cookie = cookieJar.get(originalRequest.url).firstOrNull { it.name == "cf_clearance" }
					if (cookie != null && cookie != oldCookie) {
						bypassed = true
						latch.countDown()
					}
					if (finishedUrl == url && !challengeFound) latch.countDown()
				}

				override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
					if (request.isForMainFrame) {
						if (error.errorCode in ERROR_CODES) challengeFound = true else latch.countDown()
					}
				}
			}
			wv.loadUrl(url, headers)
		}

		latch.await(30, TimeUnit.SECONDS)
		mainHandler.post { webView?.run { stopLoading(); destroy() } }
		if (!bypassed) throw CloudflareBypassException()
	}

	private fun safeHeaders(headers: Headers): Map<String, String> = headers
		.filter { (name, value) -> isRequestHeaderSafe(name, value) }
		.groupBy({ it.first }) { it.second }
		.mapValues { it.value.first() }

	private fun isRequestHeaderSafe(rawName: String, rawValue: String): Boolean {
		val name = rawName.lowercase(Locale.ENGLISH)
		val value = rawValue.lowercase(Locale.ENGLISH)
		if (name in UNSAFE_HEADERS || name.startsWith("proxy-")) return false
		if (name == "connection" && value == "upgrade") return false
		return true
	}

	private class CloudflareBypassException : Exception()

	companion object {
		private val ERROR_CODES = listOf(403, 503)
		private val COOKIE_NAMES = listOf("cf_clearance")
		private val UNSAFE_HEADERS = listOf("content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive", "transfer-encoding", "set-cookie")
	}
}
