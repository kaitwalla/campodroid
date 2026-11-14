package dev.kait.pockethomecam

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import dev.kait.media.H264AccessUnit
import dev.kait.media.H264SampleAssembler

/** Native hardware capture and encoding; no network endpoint is exposed. */
class CameraEngine(private val context: Context, private val onMotion: (Boolean) -> Unit,
                   private val onAccessUnit: (H264AccessUnit) -> Unit = {}) {
    private val thread = HandlerThread("pocket-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private val analyzer = MotionAnalyzer()
    private val sampleAssembler = H264SampleAssembler()
    val prebuffer = EncodedRingBuffer()
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var encoder: MediaCodec? = null
    private var encoderSurface: Surface? = null
    private var lastAnalyzedMs = 0L
    @Volatile private var stopping = false

    @SuppressLint("MissingPermission")
    fun start() {
        val manager = context.getSystemService(CameraManager::class.java)
        val cameraId = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: error("No back-facing camera")

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 1_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderSurface = codec.createInputSurface()
        encoder = codec
        codec.start()
        handler.post(object : Runnable {
            override fun run() {
                if (stopping) return
                try { drainEncoder(codec) } catch (e: Exception) { Log.e(TAG, "Encoder failure", e) }
                handler.postDelayed(this, 30)
            }
        })

        reader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 3).apply {
            setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastAnalyzedMs >= 200L) {
                        lastAnalyzedMs = now
                        analyzer.analyze(image, now)?.let(onMotion)
                    }
                } finally { image.close() }
            }, handler)
        }

        val supportedFps = manager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            .orEmpty()
        val selectedFps = supportedFps.firstOrNull { it.lower <= 15 && it.upper >= 15 }

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                if (stopping) { device.close(); return }
                camera = device
                val targets = listOfNotNull(encoderSurface, reader?.surface)
                device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        if (stopping) { configured.close(); return }
                        session = configured
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            targets.forEach(::addTarget)
                            if (selectedFps != null) set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selectedFps)
                        }.build()
                        configured.setRepeatingRequest(request, null, handler)
                    }
                    override fun onConfigureFailed(configured: CameraCaptureSession) {
                        Log.e(TAG, "Camera output combination unsupported")
                        configured.close()
                    }
                }, handler)
            }
            override fun onDisconnected(device: CameraDevice) { device.close(); camera = null }
            override fun onError(device: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error $error")
                device.close()
                camera = null
            }
        }, handler)
    }

    private fun drainEncoder(codec: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 0)
            if (index < 0) break
            try {
                val output = codec.getOutputBuffer(index) ?: continue
                output.position(info.offset)
                output.limit(info.offset + info.size)
                val bytes = ByteArray(info.size)
                output.get(bytes)
                prebuffer.append(EncodedFrame(info.presentationTimeUs, info.flags, bytes))
                // All MediaCodec output is processed on the camera HandlerThread.
                val sample = sampleAssembler.accept(bytes, info.presentationTimeUs,
                    codecConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0,
                    keyframe = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0)
                if (sample != null) onAccessUnit(sample)
            } finally { codec.releaseOutputBuffer(index, false) }
        }
    }

    /** Prompt an IDR frame when a HomeKit viewer joins, rather than waiting for the GOP interval. */
    fun requestKeyframe() {
        handler.post {
            if (!stopping) runCatching {
                encoder?.setParameters(Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                })
            }.onFailure { Log.w(TAG, "Encoder rejected sync-frame request", it) }
        }
    }

    fun stop() {
        stopping = true
        handler.post {
            try { session?.close() } catch (_: Exception) {}
            try { camera?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { encoderSurface?.release() } catch (_: Exception) {}
            prebuffer.clear()
            thread.quitSafely()
        }
    }

    companion object { const val TAG = "PocketHomeCam" }
}
