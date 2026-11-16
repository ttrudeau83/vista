package dev.drewett.vista.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningSelectGateTest {
    @Test fun openingHoldRepeatsAndReleaseCannotActivateTheFirstMenuItem() {
        val gate = OpeningSelectGate(OpeningSelectPress(23, 100))
        assertTrue(gate.consume(23, 100, isUp = false))
        assertTrue(gate.consume(23, 100, isUp = false))
        assertTrue(gate.consume(23, 100, isUp = true))
        assertFalse(gate.consume(23, 200, isUp = false))
        assertFalse(gate.consume(23, 200, isUp = true))
    }

    @Test fun missingOriginalReleaseDoesNotSwallowTheNextDeliberatePress() {
        val gate = OpeningSelectGate(OpeningSelectPress(23, 100))
        assertFalse(gate.consume(23, 200, isUp = false))
        assertFalse(gate.consume(23, 200, isUp = true))
    }

    @Test fun directionalNavigationAndBackAreNeverBlockedBySelectGate() {
        val gate = OpeningSelectGate(OpeningSelectPress(23, 100))
        assertFalse(gate.consume(20, 101, isUp = false))
        assertFalse(gate.consume(4, 102, isUp = true))
        assertTrue(gate.consume(23, 100, isUp = true))
    }

    @Test fun pointerOpenedMenusNeedNoKeyboardRelease() {
        assertFalse(OpeningSelectGate(null).consume(23, 200, isUp = false))
    }
}
