package dev.kait.pockethomecam

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HapAccessoryDatabaseTest {
    @Test fun publishesValidReadOnlyMotionAndWriteOnlyIdentifyMetadata() {
        val document = JSONObject(String(HapAccessoryDatabase.accessories("01:02:03:04:05:06", true)))
        val services = document.getJSONArray("accessories").getJSONObject(0).getJSONArray("services")
        val info = services.getJSONObject(0).getJSONArray("characteristics")
        val identify = (0 until info.length()).map { info.getJSONObject(it) }.first { it.getInt("iid") == 9 }
        assertEquals("14", identify.getString("type"))
        assertEquals("bool", identify.getString("format"))
        assertFalse(identify.has("value"))
        val motion = services.getJSONObject(1).getJSONArray("characteristics").getJSONObject(0)
        assertEquals("bool", motion.getString("format"))
        assertEquals(true, motion.getBoolean("value"))
        assertEquals("ev", motion.getJSONArray("perms").getString(1))
    }

    @Test fun characteristicReadReturnsCurrentMotionAndRejectsOtherIds() {
        val response = JSONObject(String(requireNotNull(HapAccessoryDatabase.motionRead("/characteristics?id=1.8", false))))
        val entry = response.getJSONArray("characteristics").getJSONObject(0)
        assertEquals(8, entry.getInt("iid"))
        assertFalse(entry.getBoolean("value"))
        assertNull(HapAccessoryDatabase.motionRead("/characteristics?id=1.9", true))
        assertNull(HapAccessoryDatabase.motionRead("/characteristics?id=1.8,1.9", true))
    }
}
