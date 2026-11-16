package dev.drewett.vista.ui.photos

import dev.drewett.vista.data.system.PhotoMotion
import org.junit.Assert.*
import org.junit.Test

class PhotoMotionPolicyTest {
    @Test fun offNeverCropsOrMovesTheFittedImage() {
        for(index in -5..5) {
            val plan=PhotoMotionPolicy.plan(PhotoMotion.OFF,index)
            assertEquals(PhotoTransform(),plan.start)
            assertEquals(PhotoTransform(),plan.end)
        }
    }
    @Test fun subtleMotionHasSmallOverscanAndPansCannotRevealAnEmptyEdge() {
        for(mode in PhotoMotion.entries) for(index in -10..10) {
            val plan=PhotoMotionPolicy.plan(mode,index)
            for(frame in listOf(plan.start,plan.end)) {
                assertTrue(frame.scale>=1f && frame.scale<=1.041f)
                if(mode==PhotoMotion.SUBTLE) assertTrue(frame.scale<=1.021f)
                val allowed=(frame.scale-1f)/2f+0.0001f
                assertTrue(kotlin.math.abs(frame.x)<=allowed)
                assertTrue(kotlin.math.abs(frame.y)<=allowed)
            }
        }
    }
    @Test fun zoomOutRevealsTheCompletePhotoAtTheEnd() {
        val plan=PhotoMotionPolicy.plan(PhotoMotion.SUBTLE,0)
        assertTrue(plan.start.scale>plan.end.scale)
        assertEquals(PhotoTransform(),plan.end)
    }
    @Test fun portraitAndLandscapeVideosPreserveTheirAspectRatioForFitAndCrop() {
        val portrait=PhotoMotionPolicy.videoSize(1920,1080,1080,1920,true)
        assertEquals(607.5f,portrait.first,0.01f)
        assertEquals(1080f,portrait.second,0.01f)
        val crop=PhotoMotionPolicy.videoSize(1920,1080,1080,1920,false)
        assertEquals(1920f,crop.first,0.01f)
        assertTrue(crop.second>1080f)
        val wide=PhotoMotionPolicy.videoSize(1920,1080,3840,2160,true)
        assertEquals(1920f to 1080f,wide)
    }
}
