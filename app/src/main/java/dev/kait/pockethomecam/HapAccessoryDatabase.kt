package dev.kait.pockethomecam

import org.json.JSONArray
import org.json.JSONObject

/** Small, deliberately static HAP database until a camera service is implemented. */
internal object HapAccessoryDatabase {
    private fun characteristic(type: String, iid: Int, format: String, perms: List<String>,
                               value: Any? = null): JSONObject = JSONObject()
        .put("type", type).put("iid", iid).put("format", format)
        .put("perms", JSONArray(perms)).apply { if (value != null) put("value", value) }

    fun accessories(deviceId: String, motion: Boolean): ByteArray {
        val info = JSONObject().put("iid", 1).put("type", "3E")
            .put("characteristics", JSONArray()
                .put(characteristic("23", 2, "string", listOf("pr"), "CamPodroid"))
                .put(characteristic("20", 3, "string", listOf("pr"), "CamPodroid"))
                .put(characteristic("21", 4, "string", listOf("pr"), "AndroidPhone"))
                .put(characteristic("30", 5, "string", listOf("pr"), deviceId))
                .put(characteristic("52", 6, "string", listOf("pr"), "0.1.0"))
                .put(characteristic("14", 9, "bool", listOf("pw"))))
        val sensor = JSONObject().put("iid", 7).put("type", "85")
            .put("characteristics", JSONArray().put(
                characteristic("22", 8, "bool", listOf("pr", "ev"), motion)))
        return JSONObject().put("accessories", JSONArray().put(JSONObject().put("aid", 1)
            .put("services", JSONArray().put(info).put(sensor))))
            .toString().toByteArray(Charsets.UTF_8)
    }

    /** Handles GET /characteristics?id=1.8; rejects unsupported or ambiguous IDs. */
    fun motionRead(path: String, motion: Boolean): ByteArray? {
        if (!path.startsWith("/characteristics?")) return null
        val parameters = path.substringAfter('?').split('&').associate {
            val pieces = it.split('=', limit = 2)
            pieces.first() to pieces.getOrElse(1) { "" }
        }
        if (parameters["id"] != "1.8") return null
        return JSONObject().put("characteristics", JSONArray().put(JSONObject()
            .put("aid", 1).put("iid", 8).put("value", motion)))
            .toString().toByteArray(Charsets.UTF_8)
    }
}
