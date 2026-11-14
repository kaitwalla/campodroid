package dev.kait.hap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SetupIdentityTest {
    @Test fun rejectsWellKnownWeakCodes() {
        for (code in listOf("000-00-000", "123-45-678", "876-54-321", "111-11-111", "999-99-999")) {
            assertFalse(SetupIdentityFactory.isValidCode(code))
        }
    }

    @Test fun generatesValidCodesAndIds() {
        repeat(100) {
            val setup = SetupIdentityFactory.generate()
            assertTrue(SetupIdentityFactory.isValidCode(setup.code))
            assertTrue(setup.setupId.matches(Regex("[A-Z0-9]{4}")))
        }
    }

    @Test fun encodesHomeKitSetupUri() {
        val setup = SetupIdentity("249-52-118", "A1B2")
        val uri = setup.setupUri(category = 10)
        assertTrue(uri.startsWith("X-HM://"))
        assertTrue(uri.endsWith("A1B2"))
        val payload = uri.removePrefix("X-HM://").dropLast(4).toLong(36)
        assertEquals(24952118L, payload and ((1L shl 27) - 1))
        assertEquals(10L, (payload shr 27) and 15)
        assertEquals(2L, (payload shr 31) and 15)
    }

    @Test fun rejectsUnrepresentableCategory() {
        val setup = SetupIdentity("249-52-118", "A1B2")
        assertFailsWith<IllegalArgumentException> { setup.setupUri(17) }
    }
}
