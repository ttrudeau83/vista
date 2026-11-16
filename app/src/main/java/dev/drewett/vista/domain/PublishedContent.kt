package dev.drewett.vista.domain

import java.util.Locale

/** Classification relies on publisher identity and explicit channel metadata, never titles/guesses. */
object PublishedContent {
    private val smartTubePackages = setOf("org.smarttube.stable", "org.smarttube.beta",
        "com.teamsmart.videomanager.tv", "com.teamsmart.videomanager.tv.beta")

    fun isSmartTube(packageName: String?) = packageName != null && packageName in smartTubePackages

    fun isContinueWatchingChannel(name: String?): Boolean = name?.lowercase(Locale.ROOT)?.let {
        it.contains("continue watching") || it.contains("watch next") || it == "resume"
    } == true

    fun available(cards: List<ContentCard>, enabledPackages: Set<String>) =
        cards.filter { it.packageName != null && it.packageName in enabledPackages }

    fun distinct(cards: List<ContentCard>): List<ContentCard> = cards.distinctBy {
        it.packageName to (it.internalProviderId?.takeIf(String::isNotBlank) ?: it.intentUri ?: it.stableKey)
    }
}
