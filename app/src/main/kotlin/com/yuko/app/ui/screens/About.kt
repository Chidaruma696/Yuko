package com.yuko.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.yuko.app.R
import com.yuko.app.ui.AppPrefs
import com.yuko.app.ui.komi.KomiButton
import com.yuko.app.ui.komi.KomiButtonSize
import com.yuko.app.ui.komi.KomiButtonVariant
import com.yuko.app.ui.komi.KomiCheckbox
import com.yuko.app.ui.komi.KomiListContainer
import com.yuko.app.ui.komi.KomiListRow
import com.yuko.app.ui.komi.KomiScreentone
import com.yuko.app.ui.komi.KomiSurface
import com.yuko.app.ui.komi.KomiSurfaceElevation
import com.yuko.app.ui.komi.KomiText
import com.yuko.app.ui.komi.KomiTextRole
import com.yuko.app.ui.komi.LocalPersonality

const val KEEP_ANDROID_OPEN_URL = "https://keepandroidopen.org/es/"
const val REPO_URL = "https://github.com/Chidaruma696/Yuko"
private const val NOTICES_URL = "$REPO_URL/blob/main/THIRD_PARTY_NOTICES.md"
private const val KOMI_URL = "https://github.com/komi-store/komi-store"
private const val PARSERS_URL = "https://github.com/kotatsu-redo/kotatsu-parsers-redo"

fun Context.openUrl(url: String) {
	runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Keep Android Open notice. Google's developer verification (announced 2025, enforced from 2027)
 * blocks apps from unregistered developers on every certified device, Play Store or not;
 * Yuko cannot exist under that rule.
 */
@Composable
fun KeepAndroidOpenBanner(modifier: Modifier = Modifier) {
	val colors = LocalPersonality.current.colors
	val context = LocalContext.current
	KomiSurface(
		modifier = modifier.fillMaxWidth(),
		elevation = KomiSurfaceElevation.Card,
		screentone = KomiScreentone.Corner,
		borderColor = colors.primary,
		contentPadding = PaddingValues(14.dp),
	) {
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			KomiText(text = stringResource(R.string.kao_kicker), role = KomiTextRole.Stamp, color = colors.primary, fontSize = 11.sp)
			KomiText(text = stringResource(R.string.kao_title), role = KomiTextRole.Title)
			KomiText(text = stringResource(R.string.kao_body), role = KomiTextRole.Body, color = colors.onSurfaceVariant, fontSize = 13.sp, uppercase = false)
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
				KomiButton(onClick = { context.openUrl(KEEP_ANDROID_OPEN_URL) }, label = stringResource(R.string.kao_learn), size = KomiButtonSize.Sm, emphasized = true)
				KomiButton(onClick = { AppPrefs.updateKaoBanner(false) }, label = stringResource(R.string.kao_hide), size = KomiButtonSize.Sm, variant = KomiButtonVariant.Text)
			}
		}
	}
}

/** Settings > About: Keep Android Open first, then credits (Komi Store design, Kotatsu parsers) and licenses. */
@Composable
fun AboutSection() {
	val context = LocalContext.current
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		KomiListContainer {
			KomiListRow(
				title = "Keep Android Open",
				subtitle = stringResource(R.string.kao_row_summary),
				strong = true,
				onClick = { context.openUrl(KEEP_ANDROID_OPEN_URL) },
				showDivider = true,
			)
			KomiListRow(
				title = stringResource(R.string.kao_show_banner),
				onClick = { AppPrefs.updateKaoBanner(!AppPrefs.kaoBannerVisible) },
				trailing = { KomiCheckbox(checked = AppPrefs.kaoBannerVisible, onCheckedChange = { AppPrefs.updateKaoBanner(it) }) },
			)
		}
		KomiListContainer {
			KomiListRow(title = stringResource(R.string.credit_design), subtitle = stringResource(R.string.credit_design_summary), onClick = { context.openUrl(KOMI_URL) }, showDivider = true)
			KomiListRow(title = stringResource(R.string.credit_sources), subtitle = stringResource(R.string.credit_sources_summary), onClick = { context.openUrl(PARSERS_URL) }, showDivider = true)
			KomiListRow(title = stringResource(R.string.credit_licenses), subtitle = stringResource(R.string.credit_licenses_summary), onClick = { context.openUrl(NOTICES_URL) }, showDivider = true)
			KomiListRow(title = stringResource(R.string.credit_source_code), subtitle = stringResource(R.string.credit_source_code_summary), onClick = { context.openUrl(REPO_URL) })
		}
	}
}
