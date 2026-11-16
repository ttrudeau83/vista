package dev.drewett.vista.data.immich

import java.io.File
import java.time.LocalDate

enum class SlideshowMode { RANDOM, ON_THIS_DAY }
enum class PhotoQuality { PREVIEW, FULL_SIZE }

data class PhotoAsset(
    val id: String,
    val date: String? = null,
    val cachedFile: File? = null,
    val hidden: Boolean = false,
    val thumbFile: File? = null,
    val quality: PhotoQuality = PhotoQuality.FULL_SIZE,
)

internal data class PhotoPage(val assets: List<PhotoAsset>, val total: Int, val nextPage: Int? = null)

object PhotoPolicy {
    fun dailyPool(assets: List<PhotoAsset>, today: LocalDate = LocalDate.now()): List<PhotoAsset> =
        assets.distinctBy { it.id }.sortedBy { it.id }
            .shuffled(kotlin.random.Random(today.toEpochDay())).take(12)

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
        val full = originals.size >= 12 || originals.sumOf { it.length() } >= 180L * 1024 * 1024
        if (full && currentUpgrade == null) return emptyList()
        val missing = candidates.filter { it.cachedFile == null || it.quality == PhotoQuality.PREVIEW }
        val preferred = currentUpgrade ?: if (originals.isEmpty()) candidates.firstOrNull() else null
        return (listOfNotNull(preferred) + missing.shuffled()).distinctBy { it.id }.take(if (full) 1 else 3)
    }
}

data class CachedPhotoEntry(val key: String, val bytes: Long, val lastUsed: Long)

/** Shared deterministic LRU policy; protected current/downloaded photos are never evicted. */
object PhotoCachePolicy {
    fun evictionKeys(entries: List<CachedPhotoEntry>, protected: Set<String>, maxBytes: Long = 200L * 1024 * 1024,
        maxFiles: Int = 24): Set<String> {
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
