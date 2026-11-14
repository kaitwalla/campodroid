package dev.kait.pockethomecam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class CameraService : Service() {
    private var cameraEngine: CameraEngine? = null
    private var hapServer: HapLanServer? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("capture", "Camera capture", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, "capture")
            .setContentTitle("Pocket Home Cam")
            .setContentText("Camera and local motion analysis running")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        startForeground(20, notification)
        try {
            val server = HapLanServer(this, HapIdentityRepository(this))
            hapServer = server
            cameraEngine = CameraEngine(this) { motion ->
                Log.i(CameraEngine.TAG, "Motion active=$motion")
                server.motionChanged(motion)
            }
                .also { it.start() }
            server.start()
        } catch (e: Exception) {
            Log.e(CameraEngine.TAG, "Failed to start camera", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onDestroy() { hapServer?.stop(); cameraEngine?.stop(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
