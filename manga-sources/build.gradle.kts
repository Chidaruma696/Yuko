plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.serialization)
}

/*
 * Manga sources for Yuko. The parsers themselves are the Kotatsu community library
 * (kotatsu-parsers, MIT), compiled into the app as a plain dependency: no extension APKs.
 * This module adds what the library expects from its host: HTTP client with cookies and
 * Cloudflare handling, a WebView-backed JavaScript evaluator, per-source configuration,
 * bitmap helpers, and a registry of the sources Yuko exposes.
 */
android {
	namespace = "com.yuko.sources"
	compileSdk = 36

	defaultConfig {
		minSdk = 26
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
		isCoreLibraryDesugaringEnabled = true
	}

	kotlinOptions {
		jvmTarget = "17"
	}

	sourceSets {
		getByName("main") {
			java.srcDir("src/main/kotlin")
		}
	}
}

dependencies {
	coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

	api(libs.kotatsu.parsers) {
		// Android ships org.json; the JVM artifact would clash with it
		exclude(group = "org.json", module = "json")
	}
	api(libs.okhttp)
	implementation(libs.okhttp.brotli)
	api(libs.jsoup)
	api(libs.coroutines.core)
	api(libs.coroutines.android)
	api(libs.serialization.json)
	implementation(libs.androidx.core)
}
