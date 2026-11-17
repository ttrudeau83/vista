package dev.drewett.vista.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Lightweight cross-cutting UI signals. `reset` ticks when HOME is pressed while already home. */
object VistaSignals {
    val reset = MutableStateFlow(0)
    val refresh = MutableStateFlow(0)
    fun refresh() {
        refresh.value++
    }
    fun reset() {
        reset.value++
    }

    /** Tab name requested by the Nuvio shell fork when the user picks a Vista rail item there. */
    val requestedTab = MutableStateFlow<String?>(null)
    /** Set by HOME / a cold start; HomeScreen consumes it by opening Nuvio's home. */
    val goNuvioHome = MutableStateFlow(false)
    const val EXTRA_TAB = "dev.drewett.vista.extra.TAB"
}
