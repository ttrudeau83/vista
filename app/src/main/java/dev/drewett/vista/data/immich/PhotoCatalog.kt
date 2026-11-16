package dev.drewett.vista.data.immich

import java.io.File
import java.time.LocalDate

enum class SlideshowMode { RANDOM, ON_THIS_DAY }
enum class PhotoQuality { PREVIEW, FULL_SIZE }
enum class MediaKind { PHOTO, VIDEO }

data class AmbientMedia(val id: String, val file: File, val kind: MediaKind) {
    val isVideo: Boolean get() = kind == MediaKind.VIDEO
}

data class PhotoAsset(
    val id: String,
    val date: String? = null,
    val cachedFile: File? = null,
    val hidden: Boolean = false,
    val thumbFile: File? = null,
    val quality: PhotoQuality = PhotoQuality.FULL_SIZE,
    val kind: MediaKind = MediaKind.PHOTO,
) {
    val isVideo: Boolean get() = kind == MediaKind.VIDEO
}

internal data class PhotoPage(val assets: List<PhotoAsset>, val total: Int, val nextPage: Int? = null)

object PhotoPolicy {
    const val DAILY_POOL_SIZE = 60
    fun dailyPool(assets: List<PhotoAsset>, today: LocalDate = LocalDate.now()): List<PhotoAsset> =
        assets.distinctBy { it.id }.sortedBy { it.id }
            .shuffled(kotlin.random.Random(today.toEpochDay())).take(DAILY_POOL_SIZE)

    fun eligible(assets: List<PhotoAsset>, hiddenIds: Set<String>, mode: SlideshowMode,
        today: LocalDate = LocalDate.now()): List<PhotoAsset> {
        val visible = assets.filter { it.id !in hiddenIds && !it.hidden }.distinctBy { it.id }
        if (mode != SlideshowMode.ON_THIS_DAY) return visible
        val matching = visible.filter { asset ->
            val date = try { LocalDate.parse(asset.date) } catch (_: Exception) { null }
            date?.monthValue == today.monthValue && date.dayOfMonth == today.dayOfMonth && date.year < today.year
        }
        return matching.ifEmpty { visible }
    }

    /** Cached navigation must not depend on completing any pending network request. */
    fun cachedNext(assets: List<PhotoAsset>, previous: String?, randomIndex: Int): PhotoAsset? {
        val cached = assets.filter { it.cachedFile?.isFile == true }
        val choices = cached.filter { it.id != previous }.ifEmpty { cached }
        return choices.takeIf { it.isNotEmpty() }?.get(Math.floorMod(randomIndex, choices.size))
    }

    /** A manually selected preview still upgrades after the normal warm-cache target is reached. */
    fun prefetchCandidates(assets: List<PhotoAsset>, hiddenIds: Set<String>, mode: SlideshowMode,
        currentId: String?): List<PhotoAsset> {
        val visible = eligible(assets, hiddenIds, SlideshowMode.RANDOM)
        val candidates = eligible(assets, hiddenIds, mode)
        val originals = candidates.filter { it.quality == PhotoQuality.FULL_SIZE }.mapNotNull { it.cachedFile }
        val currentUpgrade = visible.firstOrNull { it.id == currentId && (it.cachedFile == null || it.quality == PhotoQuality.PREVIEW) }
        val full = originals.size >= DAILY_POOL_SIZE || originals.sumOf { it.length() } >= 450L * 1024 * 1024
        if (full && currentUpgrade == null) return emptyList()
        val missing = candidates.filter { it.cachedFile == null || it.quality == PhotoQuality.PREVIEW }
        val preferred = currentUpgrade ?: if (originals.isEmpty()) candidates.firstOrNull() else null
        return (listOfNotNull(preferred) + missing.shuffled()).distinctBy { it.id }.take(if (full) 1 else 3)
    }
}

/** A rolling window is prepared from the unviewed library, never a repeated daily sample. */
object AmbientRotationPolicy {
    const val PHOTO_WINDOW = 60
    const val VIDEO_WINDOW = 2
    const val MAX_CATALOG = 10_000

    fun unseen(assets: List<PhotoAsset>, shown: Set<String>): List<PhotoAsset> =
        assets.filter { it.id !in shown && !it.hidden }.distinctBy { it.id }

    fun ready(assets: List<PhotoAsset>, shown: Set<String>, previous: String?, randomIndex: Int,preferVideo:Boolean=false): PhotoAsset? {
        val ready = unseen(assets, shown).filter { it.cachedFile?.isFile == true && it.id != previous }
        val choices=if(preferVideo) ready.filter { it.isVideo }.ifEmpty { ready } else ready
        return choices.takeIf { it.isNotEmpty() }?.get(Math.floorMod(randomIndex, choices.size))
    }

    fun cycleComplete(assets: List<PhotoAsset>, shown: Set<String>, catalogComplete: Boolean): Boolean =
        catalogComplete && assets.isNotEmpty() && unseen(assets, shown).isEmpty()

    fun window(assets: List<PhotoAsset>, shown: Set<String>): List<PhotoAsset> {
        val fresh = unseen(assets, shown)
        // Stable per cycle/order: request coalescing cannot keep changing download targets.
        return fresh.filter { it.kind == MediaKind.PHOTO }.take(PHOTO_WINDOW) +
            fresh.filter { it.kind == MediaKind.VIDEO }.take(VIDEO_WINDOW)
    }
}

data class CachedPhotoEntry(val key: String, val bytes: Long, val lastUsed: Long)

/** Shared deterministic LRU policy; protected current/downloaded photos are never evicted. */
object PhotoCachePolicy {
    /** Leave headroom for a clip; still fetch a replacement when all cached media were shown. */
    fun canWarm(bytes:Long,hasReadyUnseen:Boolean):Boolean = bytes < 384L*1024*1024 || !hasReadyUnseen
    fun evictionKeys(entries: List<CachedPhotoEntry>, protected: Set<String>, maxBytes: Long = 512L * 1024 * 1024,
        maxFiles: Int = 120): Set<String> {
        val remaining = entries.distinctBy { it.key }.sortedBy { it.lastUsed }.toMutableList()
        var bytes = remaining.sumOf { it.bytes.coerceAtLeast(0) }
        return buildSet {
            for (entry in remaining.toList()) {
                if (remaining.size <= maxFiles && bytes <= maxBytes) break
                if (entry.key in protected) continue
                add(entry.key); remaining.remove(entry); bytes -= entry.bytes.coerceAtLeast(0)
            }
        }
    }
}
