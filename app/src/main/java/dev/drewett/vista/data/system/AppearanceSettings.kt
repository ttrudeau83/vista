package dev.drewett.vista.data.system

enum class AppearanceColor { FOCUS, GRADIENT_START, GRADIENT_END }

data class AppearanceSettings(
    val focus:String="#A7C8FF",
    val gradientStart:String="#14232F",
    val gradientEnd:String="#092329",
    val customGradient:Boolean=false,
)

object AppearanceColors {
    fun normalize(raw:String):String? {
        val value=raw.trim().removePrefix("#")
        return value.takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.uppercase(java.util.Locale.ROOT)?.let { "#$it" }
    }
    fun argb(value:String):Long = 0xFF000000L or requireNotNull(normalize(value)).drop(1).toLong(16)
}
