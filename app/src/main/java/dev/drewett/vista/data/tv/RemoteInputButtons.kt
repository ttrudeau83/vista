package dev.drewett.vista.data.tv

import android.content.Context
import android.view.KeyEvent

/**
 * Which remote button switches the TV to which HDMI input. Defaults are the Onn remote's four app
 * buttons, in the order they sit on the remote (YouTube, Netflix, Disney+, Paramount+).
 */
object RemoteInputButtons {
    const val PORTS = 4
    private val DEFAULTS = mapOf(
        1 to KeyEvent.KEYCODE_BUTTON_3,
        2 to KeyEvent.KEYCODE_BUTTON_4,
        3 to KeyEvent.KEYCODE_BUTTON_9,
        4 to KeyEvent.KEYCODE_BUTTON_10,
    )
    private val ONN_NAMES = mapOf(
        KeyEvent.KEYCODE_BUTTON_3 to "YouTube button",
        KeyEvent.KEYCODE_BUTTON_4 to "Netflix button",
        KeyEvent.KEYCODE_BUTTON_9 to "Disney+ button",
        KeyEvent.KEYCODE_BUTTON_10 to "Paramount+ button",
    )
    /** Keys needed to move around and leave; never reassigned. */
    private val RESERVED = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SETTINGS,
    )

    /** While set, the next assignable key press goes here instead of switching inputs (settings "press a button"). */
    @Volatile var capture: ((Int) -> Unit)? = null

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("remote_inputs", Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("enabled", value).apply()

    fun keyFor(context: Context, port: Int): Int = prefs(context).getInt("port_$port", DEFAULTS.getValue(port))

    fun portFor(context: Context, keyCode: Int): Int? =
        if (keyCode in RESERVED || !enabled(context)) null else (1..PORTS).firstOrNull { keyFor(context, it) == keyCode }

    fun assignable(keyCode: Int) = keyCode !in RESERVED && keyCode != KeyEvent.KEYCODE_UNKNOWN

    /** Gives [keyCode] to [port]; a port that held it before swaps to [port]'s old key. */
    fun assign(context: Context, port: Int, keyCode: Int) {
        val previous = keyFor(context, port)
        val edit = prefs(context).edit()
        (1..PORTS).filter { it != port && keyFor(context, it) == keyCode }.forEach { edit.putInt("port_$it", previous) }
        edit.putInt("port_$port", keyCode).apply()
    }

    fun resetDefaults(context: Context) = prefs(context).edit().apply { (1..PORTS).forEach { remove("port_$it") } }.apply()

    fun name(keyCode: Int): String = ONN_NAMES[keyCode]
        ?: KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
