package com.yuko.app.ui

import java.text.Normalizer

/**
 * A genre as Yuko understands it, mapped onto the free-form tags each source uses.
 * [main] genres are offered first in the pickers; the rest are reachable by search.
 */
data class Genre(
	val id: String,
	val name: String,
	val kicker: String,
	val synonyms: List<String>,
	val main: Boolean = false,
	val adult: Boolean = false,
) {
	fun matches(tag: String): Boolean {
		val t = Genres.normalize(tag)
		return synonyms.any { s -> t == s || (s.length >= 4 && t.split(' ', '/', ',', '-').any { it == s }) }
	}
}

object Genres {

	fun normalize(s: String): String =
		Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

	private fun g(id: String, name: String, kicker: String, vararg synonyms: String, main: Boolean = false, adult: Boolean = false) =
		Genre(id, name, kicker, (listOf(id, name) + synonyms).map(::normalize).distinct(), main, adult)

	val all: List<Genre> = listOf(
		g("accion", "Acción", "格闘", "action", main = true),
		g("aventura", "Aventura", "冒険", "adventure", main = true),
		g("comedia", "Comedia", "笑い", "comedy", "humor", main = true),
		g("drama", "Drama", "劇", main = true),
		g("fantasia", "Fantasía", "幻想", "fantasy", "fantasia", main = true),
		g("romance", "Romance", "恋愛", "romantico", "romántico", "romantic", main = true),
		g("terror", "Terror", "恐怖", "horror", main = true),
		g("gore", "Gore", "血", "gore", "sangriento", "splatter", main = true),
		g("misterio", "Misterio", "謎", "mystery", "suspenso", "suspense", main = true),
		g("psicologico", "Psicológico", "心理", "psychological", "psicologico", main = true),
		g("ciencia-ficcion", "Ciencia ficción", "SF", "sci-fi", "scifi", "science fiction", "ciencia ficcion", main = true),
		g("sobrenatural", "Sobrenatural", "超常", "supernatural", main = true),
		g("recuentos", "Recuentos de la vida", "日常", "slice of life", "vida cotidiana", "cotidiano", main = true),
		g("escolar", "Escolar", "学園", "school", "school life", "vida escolar", "colegio", main = true),
		g("isekai", "Isekai", "異世界", main = true),
		g("shounen", "Shounen", "少年", "shonen", main = true),
		g("shoujo", "Shoujo", "少女", "shojo", main = true),
		g("seinen", "Seinen", "青年", main = true),
		g("josei", "Josei", "女性"),
		g("deportes", "Deportes", "運動", "sports", "deporte"),
		g("historico", "Histórico", "歴史", "historical", "historico", "historia"),
		g("mecha", "Mecha", "機械", "robots"),
		g("musica", "Música", "音楽", "music", "musica"),
		g("harem", "Harem", "ハーレム", "harén"),
		g("artes-marciales", "Artes marciales", "武術", "martial arts"),
		g("superpoderes", "Superpoderes", "超能力", "super power", "superpowers", "super poderes"),
		g("vampiros", "Vampiros", "吸血鬼", "vampire", "vampires"),
		g("demonios", "Demonios", "悪魔", "demons", "demonio"),
		g("magia", "Magia", "魔法", "magic", "magico"),
		g("militar", "Militar", "軍", "military"),
		g("policiaco", "Policiaco", "警察", "police", "crimen", "crime"),
		g("thriller", "Thriller", "緊迫", "suspenso"),
		g("tragedia", "Tragedia", "悲劇", "tragedy"),
		g("supervivencia", "Supervivencia", "生存", "survival"),
		g("juegos", "Juegos", "遊戯", "game", "games", "videojuegos"),
		g("cocina", "Cocina", "料理", "cooking", "gourmet"),
		g("apocaliptico", "Apocalíptico", "終末", "apocalyptic", "post-apocalyptic", "postapocaliptico", "zombies", "zombie"),
		g("reencarnacion", "Reencarnación", "転生", "reincarnation", "reencarnacion"),
		g("cultivacion", "Cultivación", "修煉", "cultivation", "murim", "wuxia", "xianxia"),
		g("webtoon", "Webtoon", "縦", "manhwa", "manhua", "webcomic"),
		g("oneshot", "One shot", "読切", "one-shot", "one shot"),
		g("yuri", "Yuri", "百合", "shoujo ai", "girls love"),
		g("yaoi", "Yaoi", "薔薇", "shounen ai", "boys love", "bl"),
		g("ecchi", "Ecchi", "エッチ", adult = true),
		g("hentai", "Hentai", "変態", "+18", "18+", "adulto", "adult", "smut", "erotico", "erótico", "porno", adult = true),
	)

	val byId: Map<String, Genre> = all.associateBy { it.id }

	/** Ids of the genres a list of source tags maps onto. */
	fun of(tags: Collection<String>): Set<String> {
		if (tags.isEmpty()) return emptySet()
		val out = HashSet<String>()
		for (tag in tags) for (genre in all) if (genre.matches(tag)) out += genre.id
		return out
	}

	fun search(query: String): List<Genre> {
		val q = normalize(query)
		if (q.isBlank()) return all.filter { it.main }
		return all.filter { g -> g.synonyms.any { it.contains(q) } }
	}
}
