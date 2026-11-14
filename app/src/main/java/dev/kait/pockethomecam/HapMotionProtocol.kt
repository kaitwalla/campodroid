package dev.kait.pockethomecam

import org.json.JSONArray
import org.json.JSONObject

/** HAP characteristic ID 1.8 is MotionDetected (service MotionSensor, iid=7). */
internal object HapMotionProtocol {
    fun subscriptionValue(body: ByteArray): Boolean? {
        val changes = JSONObject(String(body, Charsets.UTF_8)).getJSONArray("characteristics")
        if (changes.length() != 1) return null
        val entry = changes.getJSONObject(0)
        if (entry.optInt("aid", -1) != 1 || entry.optInt("iid", -1) != 8) return null
        if (!entry.has("ev") || entry.get("ev") !is Boolean || entry.has("value")) return null
        return entry.getBoolean("ev")
    }

    fun eventBody(motion: Boolean): ByteArray = JSONObject()
        .put("characteristics", JSONArray().put(JSONObject()
            .put("aid", 1).put("iid", 8).put("value", motion)))
        .toString().toByteArray(Charsets.UTF_8)

    fun eventMessage(motion: Boolean): ByteArray {
        val body = eventBody(motion)
        return ("EVENT/1.0 200 OK\r\nContent-Type: application/hap+json\r\n" +
            "Content-Length: ${body.size}\r\n\r\n").toByteArray(Charsets.US_ASCII) + body
    }
}
