package dev.drewett.vista.ui.components

/** Only the hold that opened a menu is swallowed; the next deliberate press remains usable. */
internal data class OpeningSelectPress(val keyCode: Int, val downTime: Long)

internal class OpeningSelectGate(private var opening: OpeningSelectPress?) {
    fun consume(keyCode: Int, downTime: Long, isUp: Boolean): Boolean {
        val press = opening ?: return false
        if (press.keyCode != keyCode) return false
        if (press.downTime != downTime) { opening = null; return false }
        if (isUp) opening = null
        return true
    }
}

internal object OpeningSelectHandoff {
    var press: OpeningSelectPress? = null
    fun take(): OpeningSelectPress? = press.also { press = null }
}
