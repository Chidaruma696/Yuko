package com.yuko.app.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.yuko.app.R
import com.yuko.app.data.HistoryRepository
import com.yuko.app.download.DownloadItem
import com.yuko.app.download.DownloadRepository
import com.yuko.app.ui.AppPrefs
import com.yuko.app.ui.komi.KomiButton
import com.yuko.app.ui.komi.KomiButtonSize
import com.yuko.app.ui.komi.KomiButtonVariant
import com.yuko.app.ui.komi.KomiCheckbox
import com.yuko.app.ui.komi.KomiCircularProgress
import com.yuko.app.ui.komi.KomiListContainer
import com.yuko.app.ui.komi.KomiListRow
import com.yuko.app.ui.komi.KomiSegmented
import com.yuko.app.ui.komi.KomiSegmentedItem
import com.yuko.app.ui.komi.KomiSheet
import com.yuko.app.ui.komi.KomiText
import com.yuko.app.ui.komi.KomiTextRole
import com.yuko.app.ui.komi.LocalPersonality
import com.yuko.app.ui.komi.YukoTheme
import com.yuko.sources.ChapterRef
import com.yuko.sources.LoadedSource
import com.yuko.sources.MangaRef
import com.yuko.sources.MangaSources
import com.yuko.sources.SourcesRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** What the reader opens; handed over in memory because chapter lists are too big for an Intent. */
object ReaderLaunch {
	var manga: MangaRef? = null
	var chapters: List<ChapterRef> = emptyList()
	var index: Int = 0
	var local: DownloadItem? = null
}

/** One page: a remote URL resolved from the source, or a file on disk. */
private data class PageItem(val index: Int, val remote: org.koitharu.kotatsu.parsers.model.MangaPage? = null, val file: File? = null)

class ReaderActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		val manga = ReaderLaunch.manga
		if (manga == null) { finish(); return }
		setContent {
			YukoTheme {
				ReaderScreen(
					manga = manga,
					chapters = ReaderLaunch.chapters,
					startIndex = ReaderLaunch.index,
					local = ReaderLaunch.local,
					onClose = { finish() },
					onSystemBars = ::setSystemBars,
				)
			}
		}
	}

	private fun setSystemBars(visible: Boolean) {
		val controller = WindowCompat.getInsetsController(window, window.decorView)
		controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		if (visible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
	}

	companion object {
		fun start(context: Context, manga: MangaRef, chapters: List<ChapterRef>, index: Int) {
			ReaderLaunch.manga = manga
			ReaderLaunch.chapters = chapters
			ReaderLaunch.index = index.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
			ReaderLaunch.local = null
			context.startActivity(Intent(context, ReaderActivity::class.java))
		}

		fun startLocal(context: Context, item: DownloadItem) {
			ReaderLaunch.manga = item.manga
			ReaderLaunch.chapters = listOf(item.chapter)
			ReaderLaunch.index = 0
			ReaderLaunch.local = item
			context.startActivity(Intent(context, ReaderActivity::class.java))
		}
	}
}

@Composable
private fun ReaderScreen(
	manga: MangaRef,
	chapters: List<ChapterRef>,
	startIndex: Int,
	local: DownloadItem?,
	onClose: () -> Unit,
	onSystemBars: (Boolean) -> Unit,
) {
	val colors = LocalPersonality.current.colors
	val source = remember(manga.sourceId) { MangaSources.byId(manga.sourceId) }
	var chapterIndex by remember { mutableStateOf(startIndex) }
	val chapter = chapters.getOrNull(chapterIndex)
	var pages by remember { mutableStateOf<List<PageItem>?>(null) }
	var error by remember { mutableStateOf<String?>(null) }
	var controls by remember { mutableStateOf(false) }
	var showSettings by remember { mutableStateOf(false) }
	var startPage by remember { mutableStateOf(0) }
	val rtl = AppPrefs.readerRtl
	val webtoon = AppPrefs.readerMode == AppPrefs.MODE_WEBTOON

	LaunchedEffect(controls) { onSystemBars(controls) }

	// load the page list whenever the chapter changes
	LaunchedEffect(chapterIndex, local?.id) {
		pages = null
		error = null
		val ch = chapter ?: return@LaunchedEffect
		startPage = HistoryRepository.progress(manga.key)?.takeIf { it.chapter.id == ch.id }?.page ?: 0
		try {
			pages = withContext(Dispatchers.IO) {
				if (local != null) {
					DownloadRepository.pageFiles(local).mapIndexed { i, f -> PageItem(i, file = f) }
				} else {
					val parser = source?.parser ?: throw IllegalStateException("Fuente no disponible")
					withTimeout(60_000) { parser.getPages(ch.toChapter()) }.mapIndexed { i, p -> PageItem(i, remote = p) }
				}
			}
			if (pages.isNullOrEmpty()) error = "El capítulo no tiene páginas"
		} catch (e: kotlinx.coroutines.CancellationException) {
			throw e
		} catch (e: Throwable) {
			error = e.message ?: e.javaClass.simpleName
		}
	}

	val list = pages
	var currentPage by remember(chapterIndex) { mutableStateOf(startPage) }

	// persist progress
	LaunchedEffect(currentPage, list?.size) {
		val ch = chapter ?: return@LaunchedEffect
		val count = list?.size ?: return@LaunchedEffect
		if (count == 0) return@LaunchedEffect
		HistoryRepository.update(manga, ch, currentPage, count)
		if (currentPage >= count - 1) HistoryRepository.markRead(manga.key, ch.id)
	}

	Box(Modifier.fillMaxSize().background(Color.Black)) {
		when {
			error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
				KomiText(text = error.orEmpty(), role = KomiTextRole.Body, color = Color.White, uppercase = false, textAlign = TextAlign.Center)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					KomiButton(onClick = { val i = chapterIndex; chapterIndex = -1; chapterIndex = i }, label = stringResource(R.string.retry), size = KomiButtonSize.Sm)
					KomiButton(onClick = onClose, label = stringResource(R.string.close), size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline)
				}
			}
			list == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
				KomiCircularProgress(color = Color.White)
				KomiText(text = chapter?.displayName.orEmpty(), role = KomiTextRole.Label, color = Color.White, uppercase = false)
			}
			webtoon -> WebtoonPages(list, source, startPage, onPage = { currentPage = it }, onTap = { controls = !controls })
			else -> PagedPages(list, source, rtl, startPage, chapterIndex, onPage = { currentPage = it }, onTap = { controls = !controls })
		}

		if (controls) {
			ReaderControls(
				manga = manga,
				chapter = chapter,
				page = currentPage,
				pageCount = list?.size ?: 0,
				rtl = rtl,
				hasPrev = chapterIndex > 0 && local == null,
				hasNext = chapterIndex < chapters.lastIndex && local == null,
				onSeek = { currentPage = it; startPage = it },
				onPrev = { chapterIndex-- },
				onNext = { chapterIndex++ },
				onClose = onClose,
				onSettings = { showSettings = true },
			)
		}
	}

	if (showSettings) {
		KomiSheet(onDismiss = { showSettings = false }, title = stringResource(R.string.reader), titleJp = "読書") {
			KomiListContainer {
				KomiListRow(
					title = stringResource(R.string.reader_rtl),
					onClick = { AppPrefs.updateReaderRtl(!AppPrefs.readerRtl) },
					trailing = { KomiCheckbox(checked = AppPrefs.readerRtl, onCheckedChange = { AppPrefs.updateReaderRtl(it) }) },
					showDivider = true,
				)
				KomiListRow(
					title = stringResource(R.string.reader_mode),
					trailing = {
						KomiSegmented(
							selected = AppPrefs.readerMode,
							items = listOf(KomiSegmentedItem(AppPrefs.MODE_PAGED, stringResource(R.string.mode_paged)), KomiSegmentedItem(AppPrefs.MODE_WEBTOON, stringResource(R.string.mode_webtoon))),
							onSelect = { AppPrefs.updateReaderMode(it) },
							height = 34.dp,
						)
					},
					showDivider = true,
				)
				KomiListRow(
					title = stringResource(R.string.reader_scale),
					trailing = {
						KomiSegmented(
							selected = AppPrefs.readerScale,
							items = listOf(
								KomiSegmentedItem(AppPrefs.SCALE_HEIGHT, stringResource(R.string.scale_height)),
								KomiSegmentedItem(AppPrefs.SCALE_WIDTH, stringResource(R.string.scale_width)),
								KomiSegmentedItem(AppPrefs.SCALE_SCREEN, stringResource(R.string.scale_screen)),
							),
							onSelect = { AppPrefs.updateReaderScale(it) },
							height = 34.dp,
						)
					},
				)
			}
			Spacer(Modifier.height(8.dp))
		}
	}
}

/** Right-to-left (or left-to-right) page flipping with pinch zoom. */
@Composable
private fun PagedPages(
	pages: List<PageItem>,
	source: LoadedSource?,
	rtl: Boolean,
	startPage: Int,
	chapterIndex: Int,
	onPage: (Int) -> Unit,
	onTap: () -> Unit,
) {
	val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) { pages.size }
	val scope = rememberCoroutineScope()
	var zoomed by remember { mutableStateOf(false) }
	LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.collect { onPage(it) } }
	LaunchedEffect(startPage, chapterIndex) { if (pagerState.currentPage != startPage) pagerState.scrollToPage(startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }

	HorizontalPager(
		state = pagerState,
		modifier = Modifier.fillMaxSize(),
		reverseLayout = rtl,
		userScrollEnabled = !zoomed,
		beyondViewportPageCount = 1,
	) { index ->
		ZoomablePage(
			page = pages[index],
			source = source,
			onZoomChange = { if (index == pagerState.currentPage) zoomed = it },
			onTapZone = { zone ->
				when (zone) {
					0 -> Unit.also { onTap() }
					-1 -> scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
					else -> scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pages.size - 1)) }
				}
			},
			rtl = rtl,
		)
	}
}

/** Vertical strip, the way webtoons are meant to be read. */
@Composable
private fun WebtoonPages(pages: List<PageItem>, source: LoadedSource?, startPage: Int, onPage: (Int) -> Unit, onTap: () -> Unit) {
	val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
	LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex }.collect { onPage(it) } }
	LazyColumn(
		state = listState,
		modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
	) {
		itemsIndexed(pages, key = { _, p -> p.index }) { _, page ->
			val model = pageModel(page, source)
			val painter = rememberAsyncImagePainter(model)
			val state by painter.state.collectAsStateCompat()
			Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
				if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Empty) {
					Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { KomiCircularProgress(color = Color.White) }
				}
				if (state is AsyncImagePainter.State.Error) {
					Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
						KomiText(text = "✕ ${page.index + 1}", role = KomiTextRole.Label, color = Color.White)
					}
				}
				androidx.compose.foundation.Image(painter = painter, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
			}
		}
	}
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat() = collectAsState()

/**
 * A page that fits the screen height by default (or width / whole screen per settings),
 * with pinch zoom, drag to pan, double tap to zoom and tap zones on the sides to turn pages.
 */
@Composable
private fun ZoomablePage(page: PageItem, source: LoadedSource?, rtl: Boolean, onZoomChange: (Boolean) -> Unit, onTapZone: (Int) -> Unit) {
	val model = pageModel(page, source)
	val painter = rememberAsyncImagePainter(model)
	val state by painter.state.collectAsStateCompat()
	var zoom by remember(page.index) { mutableFloatStateOf(1f) }
	var offset by remember(page.index) { mutableStateOf(Offset.Zero) }
	val scaleMode = AppPrefs.readerScale
	LaunchedEffect(zoom) { onZoomChange(zoom > 1.02f) }

	BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
		val density = LocalDensity.current
		val containerW = with(density) { maxWidth.toPx() }
		val containerH = with(density) { maxHeight.toPx() }
		val intrinsic = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
		// size of the image once ContentScale.Fit has applied
		val fitScale = if (intrinsic != null && intrinsic.width > 0 && intrinsic.height > 0) minOf(containerW / intrinsic.width, containerH / intrinsic.height) else 1f
		val fitW = (intrinsic?.width ?: containerW) * fitScale
		val fitH = (intrinsic?.height ?: containerH) * fitScale
		val base = when (scaleMode) {
			AppPrefs.SCALE_HEIGHT -> if (fitH > 0) containerH / fitH else 1f
			AppPrefs.SCALE_WIDTH -> if (fitW > 0) containerW / fitW else 1f
			else -> 1f
		}.coerceAtLeast(1f)
		val scale = base * zoom
		fun clamp(o: Offset): Offset {
			val maxX = max(0f, (fitW * scale - containerW) / 2f)
			val maxY = max(0f, (fitH * scale - containerH) / 2f)
			return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
		}
		val effective = clamp(offset)

		Box(
			Modifier
				.fillMaxSize()
				.pointerInput(page.index, base, rtl) {
					detectTapGestures(
						onDoubleTap = { tap ->
							if (zoom > 1.02f) { zoom = 1f; offset = Offset.Zero } else {
								zoom = 2f
								val center = Offset(containerW / 2f, containerH / 2f)
								offset = clamp((center - tap) * 1f)
							}
						},
						onTap = { tap ->
							val third = containerW / 3f
							val zone = when {
								tap.x < third -> -1
								tap.x > 2 * third -> 1
								else -> 0
							}
							// in right-to-left reading the left side goes forward
							onTapZone(if (rtl) -zone else zone)
						},
					)
				}
				.pointerInput(page.index, base) {
					detectTransformGestures { _, pan, gestureZoom, _ ->
						val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
						zoom = newZoom
						offset = clamp(offset + pan)
					}
				},
			contentAlignment = Alignment.Center,
		) {
			when (state) {
				is AsyncImagePainter.State.Loading, is AsyncImagePainter.State.Empty -> KomiCircularProgress(color = Color.White)
				is AsyncImagePainter.State.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
					KomiText(text = "✕ ${page.index + 1}", role = KomiTextRole.Title, color = Color.White)
					KomiButton(onClick = { painter.restart() }, label = stringResource(R.string.retry), size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline)
				}
				else -> Unit
			}
			androidx.compose.foundation.Image(
				painter = painter,
				contentDescription = null,
				contentScale = ContentScale.Fit,
				modifier = Modifier
					.fillMaxSize()
					.graphicsLayer {
						scaleX = scale
						scaleY = scale
						translationX = effective.x
						translationY = effective.y
					},
			)
		}
	}
}

/** Image request for a page: local file, or the remote URL resolved through the source with its headers. */
@Composable
private fun pageModel(page: PageItem, source: LoadedSource?): Any? {
	val context = LocalContext.current
	if (page.file != null) return page.file
	val remote = page.remote ?: return null
	var url by remember(page.index) { mutableStateOf<String?>(null) }
	LaunchedEffect(page.index) {
		url = withContext(Dispatchers.IO) {
			runCatching { source?.parser?.getPageUrl(remote) }.getOrNull() ?: remote.url
		}
	}
	val resolved = url ?: return null
	return remember(resolved) {
		ImageRequest.Builder(context).data(resolved).apply {
			val headers = NetworkHeaders.Builder()
			source?.homeUrl?.takeIf { it.isNotBlank() }?.let { headers.set("Referer", it) }
			headers.set("User-Agent", SourcesRuntime.userAgent)
			httpHeaders(headers.build())
		}.build()
	}
}

@Composable
private fun ReaderControls(
	manga: MangaRef,
	chapter: ChapterRef?,
	page: Int,
	pageCount: Int,
	rtl: Boolean,
	hasPrev: Boolean,
	hasNext: Boolean,
	onSeek: (Int) -> Unit,
	onPrev: () -> Unit,
	onNext: () -> Unit,
	onClose: () -> Unit,
	onSettings: () -> Unit,
) {
	val colors = LocalPersonality.current.colors
	Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
		Row(
			Modifier.fillMaxWidth().background(colors.surface.copy(alpha = 0.94f)).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			KomiButton(onClick = onClose, label = "‹", size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline)
			Column(Modifier.weight(1f)) {
				KomiText(text = manga.title, role = KomiTextRole.Title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
				KomiText(text = chapter?.displayName.orEmpty(), role = KomiTextRole.Label, color = colors.onSurfaceVariant, uppercase = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			KomiButton(onClick = onSettings, label = "⚙", size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline)
		}
		Column(
			Modifier.fillMaxWidth().background(colors.surface.copy(alpha = 0.94f)).padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				KomiButton(onClick = if (rtl) onNext else onPrev, label = if (rtl) "‹ " + stringResource(R.string.next_chapter) else "‹ " + stringResource(R.string.prev_chapter), size = KomiButtonSize.Sm, variant = KomiButtonVariant.Text, enabled = if (rtl) hasNext else hasPrev)
				KomiText(text = "${page + 1} / ${pageCount.coerceAtLeast(1)}", role = KomiTextRole.Mono, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
				KomiButton(onClick = if (rtl) onPrev else onNext, label = if (rtl) stringResource(R.string.prev_chapter) + " ›" else stringResource(R.string.next_chapter) + " ›", size = KomiButtonSize.Sm, variant = KomiButtonVariant.Text, enabled = if (rtl) hasPrev else hasNext)
			}
			if (pageCount > 1) {
				val sliderValue = if (rtl) (pageCount - 1 - page).toFloat() else page.toFloat()
				Slider(
					value = sliderValue,
					onValueChange = { v -> val p = v.toInt().coerceIn(0, pageCount - 1); onSeek(if (rtl) pageCount - 1 - p else p) },
					valueRange = 0f..(pageCount - 1).toFloat(),
					steps = (pageCount - 2).coerceAtLeast(0),
					colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.outlineVariant, activeTickColor = colors.primary, inactiveTickColor = colors.outlineVariant),
				)
			}
		}
	}
}

@Suppress("unused")
private fun keepAbs(x: Float) = abs(x)
