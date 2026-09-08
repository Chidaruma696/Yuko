package com.yuko.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yuko.app.MainActivity
import com.yuko.app.R
import com.yuko.app.ui.AppPrefs
import com.yuko.sources.ChapterRef
import com.yuko.sources.LoadedSource
import com.yuko.sources.MangaRef
import com.yuko.sources.MangaSources
import com.yuko.sources.SourcesRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** One saved (or in-progress) chapter: a folder of numbered page images. */
@Serializable
data class DownloadItem(
	val id: String,
	val manga: MangaRef,
	val chapter: ChapterRef,
	val sourceName: String,
	val dirName: String,
	val status: String = STATUS_QUEUED,
	val pagesDone: Int = 0,
	val pageCount: Int = -1,
	val bytes: Long = 0,
	val error: String? = null,
	val addedAt: Long = System.currentTimeMillis(),
) {
	val key: String get() = "${manga.key}|${chapter.id}"

	companion object {
		const val STATUS_QUEUED = "queued"
		const val STATUS_RUNNING = "running"
		const val STATUS_DONE = "done"
		const val STATUS_FAILED = "failed"
	}
}

/**
 * Downloads live in the app's external files dir (no storage permission needed) with a
 * JSON index next to them. Pages are fetched by the service through the source.
 */
object DownloadRepository {

	val items = MutableStateFlow<List<DownloadItem>>(emptyList())
	private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
	private lateinit var dir: File
	private lateinit var index: File
	private val lock = Any()

	fun init(context: Context) {
		dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }
		index = File(dir, "index.json")
		items.value = runCatching { json.decodeFromString<List<DownloadItem>>(index.readText()) }.getOrDefault(emptyList())
			.map { if (it.status == DownloadItem.STATUS_RUNNING) it.copy(status = DownloadItem.STATUS_QUEUED) else it }
			.map { if (it.status == DownloadItem.STATUS_DONE && !folder(it).isDirectory) it.copy(status = DownloadItem.STATUS_FAILED, error = "Carpeta eliminada") else it }
	}

	fun folder(item: DownloadItem): File = File(dir, item.dirName)

	/** Page files of a finished download, in reading order. */
	fun pageFiles(item: DownloadItem): List<File> =
		folder(item).listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.sortedBy { it.name }.orEmpty()

	fun freeBytes(): Long = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)

	fun find(mangaKey: String, chapterId: Long): DownloadItem? =
		items.value.firstOrNull { it.manga.key == mangaKey && it.chapter.id == chapterId }

	/** Queues a chapter. Returns null if it is already queued, running or saved. */
	fun enqueue(source: LoadedSource, manga: MangaRef, chapter: ChapterRef): DownloadItem? = synchronized(lock) {
		val existing = find(manga.key, chapter.id)
		if (existing != null && existing.status != DownloadItem.STATUS_FAILED) return null
		if (existing != null) removeInternal(existing)
		val safeManga = manga.title.take(60).replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
		val safeChapter = "${chapter.number.let { if (it > 0) String.format("%06.1f", it) else "000000" }} ${chapter.displayName.take(40)}".replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
		val item = DownloadItem(
			id = UUID.randomUUID().toString(),
			manga = manga,
			chapter = chapter,
			sourceName = source.name,
			dirName = "$safeManga (${source.id})/$safeChapter [${chapter.id}]",
		)
		items.update { it + item }
		persist()
		item
	}

	fun update(id: String, transform: (DownloadItem) -> DownloadItem) = synchronized(lock) {
		items.update { list -> list.map { if (it.id == id) transform(it) else it } }
		persist()
	}

	fun remove(item: DownloadItem) = synchronized(lock) {
		removeInternal(item)
		persist()
	}

	private fun removeInternal(item: DownloadItem) {
		items.update { list -> list.filterNot { it.id == item.id } }
		runCatching { folder(item).deleteRecursively() }
	}

	/** Atomically takes the next queued item and marks it running. */
	fun claimNext(): DownloadItem? = synchronized(lock) {
		val next = items.value.firstOrNull { it.status == DownloadItem.STATUS_QUEUED } ?: return null
		val running = next.copy(status = DownloadItem.STATUS_RUNNING, error = null)
		items.update { list -> list.map { if (it.id == next.id) running else it } }
		persist()
		running
	}

	fun hasQueued(): Boolean = items.value.any { it.status == DownloadItem.STATUS_QUEUED }

	private fun persist() {
		runCatching { index.writeText(json.encodeToString(items.value)) }
	}
}

/**
 * Foreground service that drains the queue with a few parallel workers. Each worker asks the
 * source for the page list, then fetches every page through the shared OkHttp client (tagged
 * with the source so its headers apply), skipping pages already on disk.
 */
class DownloadService : Service() {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val active = AtomicInteger(0)
	private var workers = 0
	private var supervisor: Job? = null

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		createChannel()
		startForegroundCompat(buildNotification(getString(R.string.download_preparing)))
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (supervisor?.isActive != true) {
			workers = AppPrefs.parallelDownloads
			supervisor = scope.launch {
				val jobs = List(workers) { launch { workerLoop() } }
				jobs.forEach { it.join() }
				stopSelf()
			}
		}
		return START_NOT_STICKY
	}

	private suspend fun workerLoop() {
		while (true) {
			val item = DownloadRepository.claimNext() ?: break
			active.incrementAndGet()
			notify(item)
			try {
				checkSpace()
				downloadChapter(item)
				DownloadRepository.update(item.id) { it.copy(status = DownloadItem.STATUS_DONE, error = null) }
			} catch (e: Throwable) {
				Log.w("Yuko", "download failed", e)
				DownloadRepository.update(item.id) { it.copy(status = DownloadItem.STATUS_FAILED, error = e.message ?: e.javaClass.simpleName) }
			} finally {
				active.decrementAndGet()
			}
		}
	}

	private fun checkSpace() {
		if (DownloadRepository.freeBytes() < MIN_FREE_BYTES) throw IllegalStateException("Sin espacio suficiente en el almacenamiento")
	}

	private suspend fun downloadChapter(item: DownloadItem) {
		val source = MangaSources.byId(item.manga.sourceId) ?: throw IllegalStateException("Fuente no disponible")
		val parser = source.parser
		val pages = withTimeout(RESOLVE_TIMEOUT) { parser.getPages(item.chapter.toChapter()) }
		if (pages.isEmpty()) throw IllegalStateException("El capítulo no tiene páginas")
		val folder = DownloadRepository.folder(item).apply { mkdirs() }
		DownloadRepository.update(item.id) { it.copy(pageCount = pages.size, pagesDone = 0) }
		var bytes = 0L
		pages.forEachIndexed { index, page ->
			val existing = folder.listFiles()?.firstOrNull { it.name.startsWith(String.format("%03d.", index + 1)) }
			if (existing != null && existing.length() > 0) {
				bytes += existing.length()
			} else {
				val url = withTimeout(RESOLVE_TIMEOUT) { parser.getPageUrl(page) }
				val request = Request.Builder().url(url).tag(MangaSource::class.java, source.source).build()
				SourcesRuntime.client.newCall(request).execute().use { response ->
					if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} en la página ${index + 1}")
					val ext = when (response.header("Content-Type")?.substringBefore(';')?.trim()) {
						"image/png" -> "png"
						"image/webp" -> "webp"
						"image/gif" -> "gif"
						else -> "jpg"
					}
					val tmp = File(folder, ".${index + 1}.part")
					tmp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
					if (tmp.length() == 0L) { tmp.delete(); throw IllegalStateException("Página ${index + 1} vacía") }
					val target = File(folder, String.format("%03d.%s", index + 1, ext))
					tmp.renameTo(target)
					bytes += target.length()
				}
			}
			DownloadRepository.update(item.id) { it.copy(pagesDone = index + 1, bytes = bytes) }
			notify(item)
		}
	}

	// -------------------------------------------------------------- notifications

	private fun notify(item: DownloadItem) {
		val running = DownloadRepository.items.value.filter { it.status == DownloadItem.STATUS_RUNNING }
		val text = if (running.size <= 1) "${item.manga.title} · ${item.chapter.displayName}" else "${running.size} capítulos en curso"
		val manager = getSystemService(NotificationManager::class.java)
		manager.notify(NOTIFICATION_ID, buildNotification(text))
	}

	private fun buildNotification(text: String): Notification {
		val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
		return NotificationCompat.Builder(this, CHANNEL)
			.setSmallIcon(R.drawable.ic_downloads)
			.setContentTitle(getString(R.string.downloads))
			.setContentText(text)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setContentIntent(open)
			.build()
	}

	private fun createChannel() {
		val channel = NotificationChannel(CHANNEL, getString(R.string.downloads), NotificationManager.IMPORTANCE_LOW)
		getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
	}

	private fun startForegroundCompat(notification: Notification) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		} else {
			startForeground(NOTIFICATION_ID, notification)
		}
	}

	override fun onDestroy() {
		scope.cancel()
		super.onDestroy()
	}

	companion object {
		private const val CHANNEL = "downloads"
		private const val NOTIFICATION_ID = 1
		private const val MIN_FREE_BYTES = 100L * 1024 * 1024
		private const val RESOLVE_TIMEOUT = 90_000L

		fun start(context: Context) {
			if (!DownloadRepository.hasQueued()) return
			val intent = Intent(context, DownloadService::class.java)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
		}
	}
}

fun humanSize(bytes: Long): String = when {
	bytes >= 1L shl 30 -> String.format("%.1f GB", bytes / (1L shl 30).toDouble())
	bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / (1L shl 20).toDouble())
	bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / (1L shl 10).toDouble())
	else -> "$bytes B"
}

