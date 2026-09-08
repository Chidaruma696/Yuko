package com.yuko.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.yuko.app.R
import com.yuko.app.data.HistoryRepository
import com.yuko.app.data.LibraryEntry
import com.yuko.app.data.LibraryRepository
import com.yuko.app.download.DownloadItem
import com.yuko.app.download.DownloadRepository
import com.yuko.app.download.DownloadService
import com.yuko.app.reader.ReaderActivity
import com.yuko.app.ui.AppPrefs
import com.yuko.app.ui.BrowseMode
import com.yuko.app.ui.BrowseViewModel
import com.yuko.app.ui.DetailsViewModel
import com.yuko.app.ui.SourcePrefs
import com.yuko.app.ui.SourcesViewModel
import com.yuko.app.ui.komi.KomiBadge
import com.yuko.app.ui.komi.KomiBadgeTone
import com.yuko.app.ui.komi.KomiButton
import com.yuko.app.ui.komi.KomiButtonSize
import com.yuko.app.ui.komi.KomiButtonVariant
import com.yuko.app.ui.komi.KomiCheckbox
import com.yuko.app.ui.komi.KomiChip
import com.yuko.app.ui.komi.KomiChipKind
import com.yuko.app.ui.komi.KomiCircularProgress
import com.yuko.app.ui.komi.KomiListContainer
import com.yuko.app.ui.komi.KomiListRow
import com.yuko.app.ui.komi.KomiScaffold
import com.yuko.app.ui.komi.KomiSectionHead
import com.yuko.app.ui.komi.KomiSegmented
import com.yuko.app.ui.komi.KomiSegmentedItem
import com.yuko.app.ui.komi.KomiSurface
import com.yuko.app.ui.komi.KomiSurfaceElevation
import com.yuko.app.ui.komi.KomiText
import com.yuko.app.ui.komi.KomiTextField
import com.yuko.app.ui.komi.KomiTextRole
import com.yuko.app.ui.komi.KomiTopBar
import com.yuko.app.ui.komi.LocalPersonality
import com.yuko.app.ui.komi.screentoneFill
import com.yuko.sources.LoadedSource
import com.yuko.sources.MangaRef
import com.yuko.sources.MangaSources
import com.yuko.sources.SourcesRuntime
import com.yuko.sources.toRef
import org.koitharu.kotatsu.parsers.model.MangaState

// ---------------------------------------------------------------- sources

@Composable
fun SourcesScreen(contentPadding: PaddingValues, onOpen: (LoadedSource) -> Unit) {
	val vm = viewModel<SourcesViewModel>()
	val sources by vm.sources.collectAsState()
	val colors = LocalPersonality.current.colors
	var enabled by remember { mutableStateOf<Set<String>>(emptySet()) }
	LaunchedEffect(sources) { if (sources != null) enabled = SourcePrefs.enabledIds() }
	val list = sources?.filter { AppPrefs.showAdult || !it.isNsfw }
	if (list == null) {
		Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { KomiCircularProgress() }
		return
	}
	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		item {
			KomiSectionHead(label = "${list.size} ${stringResource(R.string.sources)}", kicker = "ES")
			KomiText(text = stringResource(R.string.in_feed) + ": ${enabled.size}", role = KomiTextRole.Label, color = colors.onSurfaceVariant, uppercase = false)
		}
		item {
			KomiListContainer {
				list.forEachIndexed { index, src ->
					KomiListRow(
						title = src.name,
						subtitle = src.contentType.name.lowercase().replaceFirstChar { it.uppercase() },
						onClick = { onOpen(src) },
						showDivider = index < list.lastIndex,
						trailing = {
							Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
								if (src.isNsfw) KomiBadge(text = "18+", tone = KomiBadgeTone.Alert, tilt = true)
								KomiCheckbox(
									checked = src.id in enabled,
									onCheckedChange = { on ->
										SourcePrefs.setEnabled(src.id, on)
										enabled = SourcePrefs.enabledIds()
									},
								)
							}
						},
					)
				}
			}
		}
	}
}

// ---------------------------------------------------------------- browse

@Composable
fun BrowseScreen(source: LoadedSource, onOpen: (MangaRef) -> Unit, onBack: () -> Unit) {
	val vm = viewModel<BrowseViewModel>()
	val state by vm.state.collectAsState()
	val colors = LocalPersonality.current.colors
	var query by rememberSaveable { mutableStateOf("") }
	LaunchedEffect(source.id) { vm.open(source) }
	val gridState = rememberLazyGridState()
	val nearEnd by remember {
		derivedStateOf {
			val info = gridState.layoutInfo
			val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
			info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
		}
	}
	LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

	KomiScaffold(
		topBar = {
			KomiTopBar(
				title = source.name,
				subtitle = source.domain,
				leading = { KomiButton(onClick = onBack, label = "‹", size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline) },
			)
		},
	) { padding ->
		Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
			Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
				KomiSegmented(
					selected = state.mode,
					items = buildList {
						add(KomiSegmentedItem(BrowseMode.POPULAR, stringResource(R.string.popular)))
						if (state.supportsLatest) add(KomiSegmentedItem(BrowseMode.LATEST, stringResource(R.string.latest)))
						if (state.mode == BrowseMode.SEARCH) add(KomiSegmentedItem(BrowseMode.SEARCH, stringResource(R.string.search)))
					},
					onSelect = { if (it != BrowseMode.SEARCH) vm.open(source, it) },
					height = 36.dp,
				)
			}
			if (state.supportsSearch) {
				Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
					KomiTextField(
						value = query,
						onValueChange = { query = it },
						modifier = Modifier.weight(1f),
						placeholder = stringResource(R.string.search),
						onCommit = { if (query.isNotBlank()) vm.open(source, BrowseMode.SEARCH, query.trim()) },
					)
					KomiButton(onClick = { if (query.isNotBlank()) vm.open(source, BrowseMode.SEARCH, query.trim()) }, label = stringResource(R.string.search), size = KomiButtonSize.Sm, enabled = query.isNotBlank())
				}
			}
			LazyVerticalGrid(
				columns = GridCells.Adaptive(112.dp),
				state = gridState,
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding() + 24.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				items(state.items, key = { it.id }) { manga ->
					Column(Modifier.clickable { onOpen(manga.toRef()) }) {
						Cover(url = manga.coverUrl, source = source, modifier = Modifier.fillMaxWidth())
						KomiText(text = manga.title, role = KomiTextRole.Label, uppercase = false, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
					}
				}
				if (state.isLoading) {
					item(span = { GridItemSpan(maxLineSpan) }) {
						Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { KomiCircularProgress() }
					}
				}
				state.error?.let { err ->
					item(span = { GridItemSpan(maxLineSpan) }) {
						Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
							KomiText(text = err, role = KomiTextRole.Body, color = colors.error, uppercase = false, textAlign = TextAlign.Center)
							KomiButton(onClick = { vm.loadMore() }, label = stringResource(R.string.retry), variant = KomiButtonVariant.Outline)
						}
					}
				}
				if (!state.isLoading && state.error == null && state.items.isEmpty()) {
					item(span = { GridItemSpan(maxLineSpan) }) {
						KomiText(text = stringResource(R.string.nothing_found), role = KomiTextRole.Title, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
					}
				}
			}
		}
	}
}

// ---------------------------------------------------------------- details

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(source: LoadedSource, manga: MangaRef, onBack: () -> Unit) {
	val vm = viewModel<DetailsViewModel>()
	val state by vm.state.collectAsState()
	val colors = LocalPersonality.current.colors
	val context = LocalContext.current
	LaunchedEffect(source.id, manga.id) { vm.open(source, manga) }
	val details = state.manga ?: manga
	val downloads by DownloadRepository.items.collectAsState()
	val library by LibraryRepository.items.collectAsState()
	val readMap by HistoryRepository.read.collectAsState()
	val history by HistoryRepository.items.collectAsState()
	val category = library.firstOrNull { it.manga.key == details.key }?.category
	val readSet = readMap[details.key].orEmpty()
	val progress = history.firstOrNull { it.manga.key == details.key }
	var newestFirst by rememberSaveable { mutableStateOf(true) }
	val chapters = if (newestFirst) state.chapters.asReversed() else state.chapters

	fun read(index: Int) = ReaderActivity.start(context, details, state.chapters, index, state.alternates)

	KomiScaffold(
		topBar = {
			KomiTopBar(
				title = details.title,
				subtitle = source.name,
				leading = { KomiButton(onClick = onBack, label = "‹", size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline) },
			)
		},
	) { padding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			item {
				KomiSurface(modifier = Modifier.fillMaxWidth(), elevation = KomiSurfaceElevation.Card, contentPadding = PaddingValues(13.dp)) {
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						Cover(url = details.largeCoverUrl ?: details.coverUrl, source = source, modifier = Modifier.width(110.dp))
						Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
							KomiText(text = details.title, role = KomiTextRole.Title, maxLines = 3, overflow = TextOverflow.Ellipsis)
							val status = when (details.state) {
								MangaState.ONGOING.name -> stringResource(R.string.state_ongoing)
								MangaState.FINISHED.name -> stringResource(R.string.state_finished)
								MangaState.PAUSED.name -> stringResource(R.string.state_paused)
								MangaState.ABANDONED.name -> stringResource(R.string.state_abandoned)
								else -> null
							}
							FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
								status?.let { KomiChip(label = it, kind = KomiChipKind.Filter, selected = true, small = true, tilt = false) }
								details.authors.firstOrNull()?.takeIf { it.isNotBlank() }?.let { KomiChip(label = it, kind = KomiChipKind.Info, small = true, tilt = false) }
								if (details.rating > 0f) KomiChip(label = "★ ${String.format("%.1f", details.rating * 5)}", kind = KomiChipKind.Info, small = true, tilt = false)
							}
							details.tags.takeIf { it.isNotEmpty() }?.let { tags ->
								KomiText(text = tags.joinToString(" · ") { it.title }, role = KomiTextRole.Label, color = colors.onSurfaceVariant, fontSize = 11.sp, uppercase = false, maxLines = 3, overflow = TextOverflow.Ellipsis)
							}
						}
					}
				}
			}
			item {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					KomiButton(
						onClick = { LibraryRepository.toggle(details, LibraryEntry.FAVORITE) },
						label = if (category == LibraryEntry.FAVORITE) "★ " + stringResource(R.string.favorite) else "☆ " + stringResource(R.string.favorite),
						size = KomiButtonSize.Sm,
						variant = if (category == LibraryEntry.FAVORITE) KomiButtonVariant.Primary else KomiButtonVariant.Outline,
					)
					KomiButton(
						onClick = { LibraryRepository.toggle(details, LibraryEntry.LATER) },
						label = stringResource(R.string.read_later),
						size = KomiButtonSize.Sm,
						variant = if (category == LibraryEntry.LATER) KomiButtonVariant.Primary else KomiButtonVariant.Outline,
					)
				}
			}
			details.description?.takeIf { it.isNotBlank() }?.let { desc ->
				item { KomiText(text = desc, role = KomiTextRole.Body, color = colors.onSurfaceVariant, uppercase = false) }
			}
			if (state.chapters.isNotEmpty()) {
				item {
					val continueIndex = progress?.let { p -> state.chapters.indexOfFirst { it.id == p.chapter.id }.takeIf { it >= 0 } }
					val firstUnread = state.chapters.indexOfFirst { it.id !in readSet }.takeIf { it >= 0 }
					val target = continueIndex ?: firstUnread ?: 0
					val label = if (continueIndex != null) "${stringResource(R.string.continue_reading)} · ${state.chapters[target].displayName}" else "${stringResource(R.string.start_reading)} · ${state.chapters[target].displayName}"
					KomiButton(onClick = { read(target) }, label = label, emphasized = true, fullWidth = true)
				}
			}
			if (state.isMerging || state.extraSources.isNotEmpty()) {
				item {
					KomiText(
						text = if (state.isMerging) stringResource(R.string.merging_sources) else stringResource(R.string.completed_with, state.extraSources.joinToString(", ")),
						role = KomiTextRole.Label, color = colors.onSurfaceVariant, uppercase = false, fontSize = 11.sp,
					)
				}
			}
			item {
				KomiSectionHead(
					label = "${state.chapters.size} ${stringResource(R.string.chapters)}", kicker = "話",
					action = if (state.chapters.isNotEmpty()) {
						{
							Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
								KomiButton(onClick = { newestFirst = !newestFirst }, label = if (newestFirst) "↓" else "↑", size = KomiButtonSize.Sm, variant = KomiButtonVariant.Text)
								KomiButton(
									onClick = {
										val queued = state.chapters.count { ch -> DownloadRepository.enqueue(MangaSources.byId(ch.sourceId) ?: source, details, ch) != null }
										if (queued > 0) DownloadService.start(context)
										Toast.makeText(context, context.getString(R.string.download_all_queued, queued), Toast.LENGTH_SHORT).show()
									},
									label = stringResource(R.string.download_all), size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline,
								)
							}
						}
					} else null,
				)
			}
			when {
				state.isLoading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { KomiCircularProgress() } }
				state.error != null -> item {
					Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
						KomiText(text = state.error.orEmpty(), role = KomiTextRole.Body, color = colors.error, uppercase = false, textAlign = TextAlign.Center)
						KomiButton(onClick = { vm.retry() }, label = stringResource(R.string.retry), variant = KomiButtonVariant.Outline)
					}
				}
				state.chapters.isEmpty() && !state.isMerging -> item {
					KomiText(text = stringResource(R.string.no_chapters), role = KomiTextRole.Body, color = colors.onSurfaceVariant, uppercase = false, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
				}
				else -> item {
					KomiListContainer {
						chapters.forEachIndexed { index, ch ->
							val isRead = ch.id in readSet
							val foreign = ch.sourceId != details.sourceId
							val subtitleParts = listOfNotNull(
								if (foreign) MangaSources.byId(ch.sourceId)?.name else null,
								ch.scanlator?.takeIf { it.isNotBlank() },
								ch.branch?.takeIf { state.branches.size > 1 },
							)
							KomiListRow(
								title = ch.displayName,
								subtitle = subtitleParts.joinToString(" · ").ifBlank { null },
								onClick = { read(state.chapters.indexOf(ch)) },
								onLongClick = { HistoryRepository.markRead(details.key, ch.id, !isRead) },
								showDivider = index < chapters.lastIndex,
								selected = progress?.chapter?.id == ch.id,
								trailing = {
									val existing = downloads.firstOrNull { it.manga.key == details.key && it.chapter.id == ch.id }
									Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
										when (existing?.status) {
											DownloadItem.STATUS_DONE -> KomiBadge(text = "⤓", tone = KomiBadgeTone.Neutral)
											DownloadItem.STATUS_RUNNING, DownloadItem.STATUS_QUEUED -> KomiBadge(text = "…", tone = KomiBadgeTone.Neutral)
											else -> KomiButton(
												onClick = {
													if (DownloadRepository.enqueue(MangaSources.byId(ch.sourceId) ?: source, details, ch) != null) {
														DownloadService.start(context)
														Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
													}
												},
												label = "⤓", size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline,
											)
										}
										KomiText(text = if (isRead) "✓" else "○", role = KomiTextRole.Label, color = if (isRead) colors.onSurfaceVariant else colors.primary)
									}
								},
							)
						}
					}
				}
			}
		}
	}
}

// ---------------------------------------------------------------- shared

/** Cover image fetched with the source's referer and user agent, framed the Komi way. */
@Composable
fun Cover(url: String?, source: LoadedSource?, modifier: Modifier = Modifier) {
	val colors = LocalPersonality.current.colors
	val context = LocalContext.current
	Box(
		modifier = modifier
			.aspectRatio(2f / 3f)
			.background(colors.surfaceVariant)
			.screentoneFill(color = colors.onSurface, opacity = colors.screentoneOpacity + 0.04f)
			.border(2.5.dp, colors.outline)
			.padding(2.5.dp)
			.clipToBounds(),
		contentAlignment = Alignment.Center,
	) {
		if (!url.isNullOrBlank()) {
			val request = remember(url, source?.id) {
				ImageRequest.Builder(context).data(url).apply {
					val headers = NetworkHeaders.Builder()
					source?.homeUrl?.takeIf { it.isNotBlank() }?.let { headers.set("Referer", it) }
					headers.set("User-Agent", SourcesRuntime.userAgent)
					source?.let { headers.set(com.yuko.app.SourceTaggingCallFactory.HEADER, it.id) }
					httpHeaders(headers.build())
				}.build()
			}
			AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
		}
	}
}
