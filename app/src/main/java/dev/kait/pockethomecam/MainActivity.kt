package dev.kait.pockethomecam

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var pairingStatus: TextView
    private lateinit var pairingCode: TextView
    private lateinit var pairingInstructions: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        pairingStatus = findViewById(R.id.pairing_status)
        pairingCode = findViewById(R.id.pairing_code)
        pairingInstructions = findViewById(R.id.pairing_instructions)
        findViewById<Button>(R.id.start).setOnClickListener { startIfAllowed() }
        findViewById<Button>(R.id.stop).setOnClickListener {
            stopService(Intent(this, CameraService::class.java))
            status.text = "Camera stopped"
        }
    }

    override fun onResume() {
        super.onResume()
        val repository = HapIdentityRepository(this)
        val presentation = pairingPresentation(repository.setupIdentity().code, repository.isPaired())
        pairingStatus.text = presentation.status
        pairingCode.text = presentation.code
        pairingInstructions.text = presentation.instructions
    }

    private fun startIfAllowed() {
        val required = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 100)
            return
        }
        startForegroundService(Intent(this, CameraService::class.java))
        status.text = "Starting camera; motion results appear in Logcat (tag PocketHomeCam)"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startIfAllowed()
    }
}
