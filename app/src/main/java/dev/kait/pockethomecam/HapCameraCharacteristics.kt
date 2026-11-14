package dev.kait.pockethomecam

import dev.kait.hap.CameraStreamProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Strict JSON HAP camera characteristic dispatch; no UDP resources are owned here. */
internal object HapCameraCharacteristics {
    data class Write(val iid: Int, val payload: ByteArray)

    fun parseWrite(body: ByteArray): Write? = runCatching {
        val changes = JSONObject(String(body, Charsets.UTF_8)).getJSONArray("characteristics")
        require(changes.length() == 1)
        val item = changes.getJSONObject(0)
        require(item.getInt("aid") == 1)
        val iid = item.getInt("iid")
        require(iid == 24 || iid == 25)
        Write(iid, Base64.getDecoder().decode(item.getString("value")))
    }.getOrNull()

    fun read(path: String, setupResponse: ByteArray?, streaming: Boolean, motion: Boolean = false): ByteArray? {
        if (!path.startsWith("/characteristics?")) return null
        val id = path.substringAfter('?').split('&').firstOrNull { it.startsWith("id=") }
            ?.substringAfter('=') ?: return null
        val iids = id.split(',').map {
            if (!it.startsWith("1.")) return null
            it.substringAfter('.').toIntOrNull() ?: return null
        }
        if (iids.isEmpty() || iids.size != iids.distinct().size || iids.any { it != 8 && it !in 21..26 }) return null
        val response = JSONArray()
        for (iid in iids) {
            if (iid == 8) {
                response.put(JSONObject().put("aid", 1).put("iid", 8).put("value", motion))
                continue
            }
            val bytes = when (iid) {
                21 -> CameraStreamProtocol.supportedVideo()
                22 -> CameraStreamProtocol.supportedAudio()
                23 -> CameraStreamProtocol.supportedRtp()
                24 -> setupResponse ?: byteArrayOf()
                25 -> byteArrayOf()
                26 -> CameraStreamProtocol.streamingStatus(streaming)
                else -> return null
            }
            response.put(JSONObject().put("aid", 1).put("iid", iid)
                .put("value", Base64.getEncoder().encodeToString(bytes)))
        }
        return JSONObject().put("characteristics", response).toString().toByteArray(Charsets.UTF_8)
    }
}
