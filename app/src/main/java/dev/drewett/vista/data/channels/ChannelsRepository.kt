package dev.drewett.vista.data.channels

import android.content.Context
import android.content.Intent
import android.database.Cursor
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import dev.drewett.vista.domain.ContentCard

/**
 * Reads only published TV metadata. Firmware may restrict cross-app access even with
 * READ_TV_LISTINGS; unavailable tables produce empty results rather than invented content.
 */
class ChannelsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver
    private val iconCache = HashMap<String, android.graphics.drawable.Drawable?>()

    fun invalidateIcons() = iconCache.clear()

    private data class PublishedChannel(val packageName: String, val name: String?)

    private fun loadChannels(): Map<Long, PublishedChannel> = buildMap {
        runCatching {
            resolver.query(TvContractCompat.Channels.CONTENT_URI,
                arrayOf(TvContractCompat.Channels._ID, TvContractCompat.Channels.COLUMN_PACKAGE_NAME,
                    TvContractCompat.Channels.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    put(cursor.getLong(0), PublishedChannel(cursor.getString(1).orEmpty(),
                        cursor.getString(2)?.takeIf { it.isNotBlank() }))
                }
            }
        }
    }

    private fun iconFor(packageName: String?): android.graphics.drawable.Drawable? {
        packageName ?: return null
        return iconCache.getOrPut(packageName) {
            val pm = context.packageManager
            runCatching {
                val ai = pm.getApplicationInfo(packageName, 0)
                if (ai.icon != 0) {
                    pm.getResourcesForApplication(ai)
                        .getDrawableForDensity(ai.icon, android.util.DisplayMetrics.DENSITY_XXXHIGH, null)
                } else {
                    null
                }
            }.getOrNull() ?: runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
        }
    }

    fun loadContinueWatching(): List<ContentCard> {
        val cards = query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION) { c ->
            val p = WatchNextProgram.fromCursor(c)
            ContentCard(
                id = p.id,
                title = p.title.orEmpty(),
                description = p.description ?: p.longDescription,
                posterArtUri = p.posterArtUri?.toString(),
                intentUri = p.intentUri?.toString(),
                packageName = p.packageName,
                providerIcon = iconFor(p.packageName),
                seasonNumber = p.seasonNumber,
                episodeNumber = p.episodeNumber,
                episodeTitle = p.episodeTitle,
                reviewRating = runCatching { p.reviewRating }.getOrNull(),
                reviewRatingStyle = runCatching { p.reviewRatingStyle }.getOrDefault(-1),
                releaseDate = p.releaseDate,
                type = runCatching { p.type }.getOrDefault(-1),
                aspectRatioCode = runCatching { p.posterArtAspectRatio }.getOrDefault(-1),
                durationMillis = p.durationMillis,
                positionMillis = p.lastPlaybackPositionMillis,
                lastEngagement = p.lastEngagementTimeUtcMillis,
                source = "watchnext",
                internalProviderId = c.stringOrNull(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
            )
        }.sortedByDescending { it.lastEngagement }
        return cards
    }

    fun loadPreviewPrograms(): List<ContentCard> {
        val channels = loadChannels()
        val cards = query(TvContractCompat.PreviewPrograms.CONTENT_URI, PreviewProgram.PROJECTION) { c ->
            val p = PreviewProgram.fromCursor(c)
            ContentCard(
                id = p.id,
                title = p.title.orEmpty(),
                description = p.description ?: p.longDescription,
                posterArtUri = p.posterArtUri?.toString(),
                intentUri = p.intentUri?.toString(),
                packageName = p.packageName,
                providerIcon = iconFor(p.packageName),
                seasonNumber = p.seasonNumber,
                episodeNumber = p.episodeNumber,
                episodeTitle = p.episodeTitle,
                reviewRating = runCatching { p.reviewRating }.getOrNull(),
                reviewRatingStyle = runCatching { p.reviewRatingStyle }.getOrDefault(-1),
                releaseDate = p.releaseDate,
                type = runCatching { p.type }.getOrDefault(-1),
                aspectRatioCode = runCatching { p.posterArtAspectRatio }.getOrDefault(-1),
                durationMillis = p.durationMillis,
                positionMillis = p.lastPlaybackPositionMillis,
                lastEngagement = 0L,
                channelId = p.channelId.takeIf { it >= 0 },
                channelName = channels[p.channelId]?.takeIf { it.packageName == p.packageName }?.name,
                internalProviderId = c.stringOrNull(TvContractCompat.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID),
            )
        }
        return cards
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    /** Fire the publisher's own deep-link to resume/open the content. */
    fun launchContent(card: ContentCard): Boolean {
        val uri = card.intentUri ?: return false
        return runCatching {
            val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private inline fun query(
        uri: android.net.Uri,
        projection: Array<String>,
        map: (Cursor) -> ContentCard,
    ): List<ContentCard> {
        val out = ArrayList<ContentCard>()
        runCatching {
            resolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    runCatching { map(c) }.getOrNull()
                        ?.takeIf { it.title.isNotBlank() }
                        ?.let(out::add)
                }
            }
        }.onFailure { android.util.Log.w("VistaDiag", "Published TV metadata is unavailable for $uri") }
        return out
    }
}
