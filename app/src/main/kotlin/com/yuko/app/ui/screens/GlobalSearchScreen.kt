package com.yuko.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuko.app.R
import com.yuko.app.ui.GlobalSearchViewModel
import com.yuko.app.ui.komi.KomiButton
import com.yuko.app.ui.komi.KomiButtonSize
import com.yuko.app.ui.komi.KomiButtonVariant
import com.yuko.app.ui.komi.KomiLinearProgress
import com.yuko.app.ui.komi.KomiScaffold
import com.yuko.app.ui.komi.KomiSectionHead
import com.yuko.app.ui.komi.KomiText
import com.yuko.app.ui.komi.KomiTextField
import com.yuko.app.ui.komi.KomiTextRole
import com.yuko.app.ui.komi.KomiTopBar
import com.yuko.app.ui.komi.LocalPersonality
import com.yuko.sources.MangaRef
import com.yuko.sources.toRef

/** One query, every enabled source: a row of covers per source that found something. */
@Composable
fun GlobalSearchScreen(onOpen: (MangaRef) -> Unit, onBack: () -> Unit) {
	val vm = viewModel<GlobalSearchViewModel>()
	val state by vm.state.collectAsState()
	val colors = LocalPersonality.current.colors
	var query by rememberSaveable { mutableStateOf(state.query) }
	// leaving the screen stops the sources still in flight
	DisposableEffect(Unit) { onDispose { vm.cancel() } }

	fun submit() { if (query.isNotBlank()) vm.search(query) }

	KomiScaffold(
		topBar = {
			KomiTopBar(
				title = stringResource(R.string.global_search),
				subtitle = stringResource(R.string.global_search_kicker),
				leading = { KomiButton(onClick = onBack, label = "‹", size = KomiButtonSize.Sm, variant = KomiButtonVariant.Outline) },
			)
		},
	) { padding ->
		Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
			Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
				KomiTextField(
					value = query,
					onValueChange = { query = it; if (it.isBlank()) vm.search("") },
					modifier = Modifier.weight(1f),
					placeholder = stringResource(R.string.global_search_placeholder),
					onCommit = { submit() },
				)
				KomiButton(onClick = { submit() }, label = stringResource(R.string.search), size = KomiButtonSize.Sm, enabled = query.isNotBlank())
			}
			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				if (state.isSearching) {
					item(key = "progress") {
						Column(Modifier.padding(horizontal = 16.dp)) {
							KomiLinearProgress(modifier = Modifier.fillMaxWidth())
							KomiText(
								text = stringResource(R.string.sources_progress, state.done, state.total),
								role = KomiTextRole.Label, color = colors.onSurfaceVariant, fontSize = 11.sp, uppercase = false,
								modifier = Modifier.padding(top = 4.dp),
							)
						}
					}
				}
				state.hits.forEach { hit ->
					item(key = "head_${hit.source.id}") {
						KomiSectionHead(label = hit.source.name, kicker = "${hit.items.size} 件", modifier = Modifier.padding(horizontal = 16.dp))
					}
					item(key = "row_${hit.source.id}") {
						LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
							items(hit.items, key = { it.id }) { manga ->
								val ref = manga.toRef()
								CoverCard(manga = ref, subtitle = null, onClick = { onOpen(ref) })
							}
						}
					}
				}
				when {
					state.query.isBlank() -> item(key = "hint") {
						KomiText(
							text = stringResource(R.string.global_search_hint), role = KomiTextRole.Body, color = colors.onSurfaceVariant,
							uppercase = false, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp),
						)
					}
					!state.isSearching && state.total == 0 -> item(key = "no_sources") {
						KomiText(
							text = stringResource(R.string.global_search_no_sources), role = KomiTextRole.Body, color = colors.error,
							uppercase = false, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp),
						)
					}
					!state.isSearching && state.hits.isEmpty() -> item(key = "empty") {
						KomiText(
							text = stringResource(R.string.nothing_found), role = KomiTextRole.Title, color = colors.onSurfaceVariant,
							textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp),
						)
					}
				}
			}
		}
	}
}
