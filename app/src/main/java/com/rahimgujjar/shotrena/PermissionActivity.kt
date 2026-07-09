package com.rahimgujjar.shotrena

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri

class PermissionActivity : Activity() {

    companion object {
        fun hasAllPermissions(context: Context): Boolean {
            val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else { true }

            val hasOverlay = Settings.canDrawOverlays(context)

            val pm = context.getSystemService(PowerManager::class.java)
            val hasBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true

            return hasStorage && hasOverlay && hasBattery
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processPermissions()
    }

    override fun onResume() {
        super.onResume()
        processPermissions()
    }

    private fun processPermissions() {
        val pm = getSystemService(PowerManager::class.java)

        // 1. Battery (Critical)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Step 1: Allow Unrestricted Battery", Toast.LENGTH_SHORT).show()
            @Suppress("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                return
            }
        }

        // 2. Storage (Critical)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Step 2: Allow File Access", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
                return
            }
        }

        // 3. Overlay (Critical)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Step 3: Allow 'Display Over Apps'", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
            return
        }

        // 4. Notification (Optional - Step 4)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return
            }
        }

        // All Done - Launch
        launchApp()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            launchApp()
        }
    }

    private fun launchApp() {
        val serviceIntent = Intent(this, ShotRenaService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        finishAndRemoveTask()
    }
}
