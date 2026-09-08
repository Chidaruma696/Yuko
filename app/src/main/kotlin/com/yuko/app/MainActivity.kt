package com.yuko.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yuko.app.ui.AppPrefs
import com.yuko.app.ui.komi.KomiBottomBar
import com.yuko.app.ui.komi.KomiNavItem
import com.yuko.app.ui.komi.KomiScaffold
import com.yuko.app.ui.komi.KomiTopBar
import com.yuko.app.ui.komi.YukoTheme
import com.yuko.app.ui.screens.BrowseScreen
import com.yuko.app.ui.screens.DetailsScreen
import com.yuko.app.ui.screens.DownloadsScreen
import com.yuko.app.ui.screens.HomeScreen
import com.yuko.app.ui.screens.LibraryScreen
import com.yuko.app.ui.screens.OnboardingScreen
import com.yuko.app.ui.screens.SettingsScreen
import com.yuko.app.ui.screens.SourcesScreen
import com.yuko.sources.LoadedSource
import com.yuko.sources.MangaRef
import com.yuko.sources.MangaSources
import com.yuko.sources.toRef

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		requestNotificationPermission()
		setContent {
			YukoTheme {
				if (AppPrefs.onboardingDone) {
					YukoNav()
				} else {
					OnboardingScreen(onDone = {})
				}
			}
		}
	}

	private fun requestNotificationPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
			ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
		) {
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
		}
	}
}

private enum class Tab(val id: String) { HOME("home"), LIBRARY("library"), SOURCES("sources"), DOWNLOADS("downloads"), SETTINGS("settings") }

private sealed interface Screen {
	data class Browse(val source: LoadedSource) : Screen
	data class Details(val source: LoadedSource, val manga: MangaRef) : Screen
}

@Composable
private fun YukoNav() {
	var tab by remember { mutableStateOf(Tab.HOME) }
	var stack by remember { mutableStateOf<List<Screen>>(emptyList()) }
	val current = stack.lastOrNull()
	BackHandler(enabled = stack.isNotEmpty()) { stack = stack.dropLast(1) }

	fun openManga(manga: MangaRef) {
		val source = MangaSources.byId(manga.sourceId) ?: return
		stack = stack + Screen.Details(source, manga)
	}

	when (current) {
		is Screen.Browse -> BrowseScreen(
			source = current.source,
			onOpen = { manga -> stack = stack + Screen.Details(current.source, manga) },
			onBack = { stack = stack.dropLast(1) },
		)
		is Screen.Details -> DetailsScreen(
			source = current.source,
			manga = current.manga,
			onBack = { stack = stack.dropLast(1) },
		)
		null -> {
			val items = listOf(
				KomiNavItem(Tab.HOME.id, stringResource(R.string.home), ImageVector.vectorResource(R.drawable.ic_home)),
				KomiNavItem(Tab.LIBRARY.id, stringResource(R.string.library), ImageVector.vectorResource(R.drawable.ic_library)),
				KomiNavItem(Tab.SOURCES.id, stringResource(R.string.sources), ImageVector.vectorResource(R.drawable.ic_sources)),
				KomiNavItem(Tab.DOWNLOADS.id, stringResource(R.string.downloads), ImageVector.vectorResource(R.drawable.ic_downloads)),
				KomiNavItem(Tab.SETTINGS.id, stringResource(R.string.settings), ImageVector.vectorResource(R.drawable.ic_settings)),
			)
			val kicker = when (tab) {
				Tab.HOME -> "今日 · ${stringResource(R.string.for_you).uppercase()}"
				Tab.LIBRARY -> "本棚 · ${stringResource(R.string.library).uppercase()}"
				Tab.SOURCES -> "配信 · ${stringResource(R.string.sources).uppercase()}"
				Tab.DOWNLOADS -> "保存 · ${stringResource(R.string.downloads).uppercase()}"
				Tab.SETTINGS -> "設定 · ${stringResource(R.string.settings).uppercase()}"
			}
			KomiScaffold(
				topBar = { KomiTopBar(title = stringResource(R.string.app_name), titleAccent = "ko", subtitle = kicker) },
				bottomBar = { KomiBottomBar(items = items, selectedId = tab.id, onSelect = { id -> tab = Tab.entries.first { it.id == id } }) },
			) { padding ->
				when (tab) {
					Tab.HOME -> HomeScreen(
						contentPadding = padding,
						onOpen = { feed -> stack = stack + Screen.Details(feed.source, feed.manga.toRef()) },
						onOpenSource = { src -> stack = stack + Screen.Browse(src) },
					)
					Tab.LIBRARY -> LibraryScreen(contentPadding = padding, onOpen = ::openManga)
					Tab.SOURCES -> SourcesScreen(contentPadding = padding, onOpen = { stack = stack + Screen.Browse(it) })
					Tab.DOWNLOADS -> DownloadsScreen(contentPadding = padding, onOpen = ::openManga)
					Tab.SETTINGS -> SettingsScreen(contentPadding = padding, onOpenDownloads = { tab = Tab.DOWNLOADS })
				}
			}
		}
	}
}
