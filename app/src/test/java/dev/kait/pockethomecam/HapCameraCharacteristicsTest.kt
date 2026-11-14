package dev.kait.pockethomecam

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class HapCameraCharacteristicsTest {
    @Test fun acceptsOnlyOneValidCameraWrite() {
        fun body(iid: Int, value: String) = """{"characteristics":[{"aid":1,"iid":$iid,"value":"$value"}]}""".toByteArray()
        val encoded = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), HapCameraCharacteristics.parseWrite(body(24, encoded))?.payload)
        assertEquals(25, HapCameraCharacteristics.parseWrite(body(25, encoded))?.iid)
        assertNull(HapCameraCharacteristics.parseWrite(body(8, encoded)))
        assertNull(HapCameraCharacteristics.parseWrite(body(24, "!")))
        assertNull(HapCameraCharacteristics.parseWrite("""{"characteristics":[]}""".toByteArray()))
    }

    @Test fun returnsCapabilitiesAndCurrentSessionStatus() {
        val result = JSONObject(String(requireNotNull(HapCameraCharacteristics.read(
            "/characteristics?id=1.21,1.24,1.26", byteArrayOf(7), true))))
        val chars = result.getJSONArray("characteristics")
        assertEquals(3, chars.length())
        assertTrue(Base64.getDecoder().decode(chars.getJSONObject(0).getString("value")).isNotEmpty())
        assertArrayEquals(byteArrayOf(7), Base64.getDecoder().decode(chars.getJSONObject(1).getString("value")))
        assertArrayEquals(byteArrayOf(1, 1, 1), Base64.getDecoder().decode(chars.getJSONObject(2).getString("value")))
        val mixed = JSONObject(String(requireNotNull(HapCameraCharacteristics.read(
            "/characteristics?id=1.21,1.8", null, false, motion = true))))
        assertTrue(mixed.getJSONArray("characteristics").getJSONObject(1).getBoolean("value"))
        assertNull(HapCameraCharacteristics.read("/characteristics?id=1.21,1.9", null, false))
        assertNull(HapCameraCharacteristics.read("/characteristics?id=1.24,1.24", null, false))
    }
}
