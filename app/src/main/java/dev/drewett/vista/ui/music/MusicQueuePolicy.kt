package dev.drewett.vista.ui.music

/** Queue positions, rather than IDs, preserve duplicate tracks and never wrap unexpectedly. */
object MusicQueuePolicy {
    fun move(size:Int,current:Int,delta:Int):Int? =
        (current+delta).takeIf { current in 0 until size && it in 0 until size }
}
