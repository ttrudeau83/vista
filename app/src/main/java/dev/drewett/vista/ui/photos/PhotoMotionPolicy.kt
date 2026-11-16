package dev.drewett.vista.ui.photos

import dev.drewett.vista.data.system.PhotoMotion

data class PhotoTransform(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f)
data class PhotoMotionPlan(val start: PhotoTransform, val end: PhotoTransform)

/** Normalized translation stays within the overscan; a zoom-out ends at the whole image. */
object PhotoMotionPolicy {
    fun plan(mode: PhotoMotion, index: Int): PhotoMotionPlan {
        val amount = when(mode) { PhotoMotion.OFF -> 0f; PhotoMotion.SUBTLE -> 0.02f; PhotoMotion.GENTLE -> 0.04f }
        if(amount == 0f) return PhotoMotionPlan(PhotoTransform(),PhotoTransform())
        val zoom = PhotoTransform(scale = 1f + amount)
        val pan = amount * 0.4f
        return when(Math.floorMod(index,5)) {
            0, 1 -> PhotoMotionPlan(zoom,PhotoTransform())
            2 -> PhotoMotionPlan(PhotoTransform(),zoom)
            3 -> PhotoMotionPlan(zoom.copy(x = -pan),zoom.copy(x = pan))
            else -> PhotoMotionPlan(zoom.copy(y = pan),zoom.copy(y = -pan))
        }
    }

    /** A TextureView stretches by default; return aspect-preserving display dimensions. */
    fun videoSize(width: Int, height: Int, mediaWidth: Int, mediaHeight: Int, fit: Boolean): Pair<Float,Float> {
        if(width<=0 || height<=0 || mediaWidth<=0 || mediaHeight<=0) return width.toFloat() to height.toFloat()
        val scaleX=width.toFloat()/mediaWidth; val scaleY=height.toFloat()/mediaHeight
        val scale=if(fit) minOf(scaleX,scaleY) else maxOf(scaleX,scaleY)
        return mediaWidth*scale to mediaHeight*scale
    }
}
