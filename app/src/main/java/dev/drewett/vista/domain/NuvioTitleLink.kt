package dev.drewett.vista.domain

/** Public VIEW scheme verified against NuvioTV 1.1.0's manifest and parser tests. */
object NuvioTitleLink {
    fun create(type: String, imdbId: String?, tmdbId: Long?): String? {
        val media = when (type) { "movie" -> "movie"; "show" -> "series"; else -> return null }
        val imdb = imdbId?.takeIf { it.matches(Regex("tt[0-9]+")) }
        return when {
            imdb != null -> "nuvio://meta?type=$media&id=$imdb"
            tmdbId != null && tmdbId > 0 -> "nuvio://meta?type=$media&tmdb=$tmdbId"
            else -> null
        }
    }
}
