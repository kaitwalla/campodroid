package dev.kait.pockethomecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPresentationTest {
    @Test fun unpairedShowsGeneratedCodeAndManualInstructions() {
        val ui = pairingPresentation("123-45-679", false)
        assertEquals("123-45-679", ui.code)
        assertEquals("Not paired", ui.status)
        assertTrue(ui.instructions.contains("More Options"))
        assertTrue(ui.instructions.contains("motion sensor only"))
    }

    @Test fun pairedHidesCode() {
        val ui = pairingPresentation("123-45-679", true)
        assertFalse(ui.code.contains("123-45-679"))
        assertEquals("Paired with Apple Home", ui.status)
    }
}
