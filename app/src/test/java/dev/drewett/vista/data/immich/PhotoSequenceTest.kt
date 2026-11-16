package dev.drewett.vista.data.immich

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoSequenceTest {
    private val first = "11111111-1111-4111-8111-111111111111"
    private val second = "22222222-2222-4222-8222-222222222222"
    private val third = "33333333-3333-4333-8333-333333333333"

    @Test
    fun emptyAndInvalidCandidatesHaveNoNextPhoto() {
        assertNull(PhotoSequence.chooseNext(emptyList(), null, 0))
        assertNull(PhotoSequence.chooseNext(listOf("../private", "not-an-id"), null, 0))
    }

    @Test
    fun doesNotImmediatelyRepeatWhenAnotherImageExists() {
        assertEquals(second, PhotoSequence.chooseNext(listOf(first, second), first, 0))
        assertEquals(second, PhotoSequence.chooseNext(listOf(first, first, second), first, 100))
    }

    @Test
    fun singlePhotoAlbumsRemainUsableInsteadOfGoingBlank() {
        assertEquals(first, PhotoSequence.chooseNext(listOf(first, first), first, Int.MIN_VALUE))
    }

    @Test
    fun negativeRandomIndexesStayInRangeWithoutChangingCandidateOrder() {
        assertEquals(third, PhotoSequence.chooseNext(listOf(first, second, third), first, -1))
        assertEquals(second, PhotoSequence.chooseNext(listOf(first, second, third), first, Int.MIN_VALUE))
    }
}
