package dev.drewett.vista.ui.music

import org.junit.Assert.*
import org.junit.Test

class MusicQueuePolicyTest {
    @Test fun movesByPositionWithoutWrappingAtQueueBoundaries() {
        assertEquals(1,MusicQueuePolicy.move(3,0,1))
        assertEquals(0,MusicQueuePolicy.move(3,1,-1))
        assertNull(MusicQueuePolicy.move(3,2,1));assertNull(MusicQueuePolicy.move(3,0,-1))
    }
    @Test fun emptyQueuesAndInvalidPositionsCannotSkipIntoPlayback() {
        assertNull(MusicQueuePolicy.move(0,0,1));assertNull(MusicQueuePolicy.move(3,-1,1))
    }
}
