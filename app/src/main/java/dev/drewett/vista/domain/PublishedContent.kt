package dev.drewett.vista.domain

import java.util.Locale

/** Classification relies on publisher identity and explicit channel metadata, never titles/guesses. */
object PublishedContent {
    private val smartTubePackages = setOf("org.smarttube.stable", "org.smarttube.beta",
        "com.teamsmart.videomanager.tv", "com.teamsmart.videomanager.tv.beta")

    fun isSmartTube(packageName: String?) = packageName != null && packageName in smartTubePackages

    /** Our Vista-shell fork of NuvioTV (shares this rail). */
    const val NUVIO_SHELL_PACKAGE = "dev.drewett.vista.nuvio"
    /** Extra asking the Nuvio shell to open "search" or its "profiles" picker; nothing else. */
    const val EXTRA_NUVIO_ROUTE = "dev.drewett.vista.extra.NUVIO_ROUTE"

    /** The Vista-shell fork wins, then the official GitHub release, then the Play Store app. */
    fun preferredNuvioPackage(packageNames: List<String>): String? =
        listOf(NUVIO_SHELL_PACKAGE, "com.nuvio.tv", "com.nuvio.app").firstOrNull { it in packageNames }
            ?: packageNames.firstOrNull { it.contains("nuvio", ignoreCase = true) }

    fun isContinueWatchingChannel(name: String?): Boolean = name?.lowercase(Locale.ROOT)?.let {
        it.contains("continue watching") || it.contains("watch next") || it == "resume"
    } == true

    fun available(cards: List<ContentCard>, enabledPackages: Set<String>) =
        cards.filter { it.packageName != null && it.packageName in enabledPackages }

    fun distinct(cards: List<ContentCard>): List<ContentCard> = cards.distinctBy {
        it.packageName to (it.internalProviderId?.takeIf(String::isNotBlank) ?: it.intentUri ?: it.stableKey)
    }
}
