package com.rahimgujjar.shotrena

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class ShotRenaTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateTileState()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()

        val filter = IntentFilter(ShotRenaService.ACTION_STATUS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }

        startPolling()
    }

    override fun onStopListening() {
        super.onStopListening()
        stopPolling()
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE_ACTION") {
            val serviceIntent = Intent(this, ShotRenaService::class.java)
            stopService(serviceIntent)
            updateTileState()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startPolling() {
        pollRunnable = object : Runnable {
            override fun run() {
                if (!PermissionActivity.hasAllPermissions(this@ShotRenaTileService)) {
                    if (ShotRenaService.isRunning) {
                        val serviceIntent = Intent(this@ShotRenaTileService, ShotRenaService::class.java)
                        stopService(serviceIntent)
                        Toast.makeText(this@ShotRenaTileService, "Permissions revoked. Stopping.", Toast.LENGTH_SHORT).show()
                    }
                    updateTileState()
                } else {
                    updateTileState()
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(pollRunnable!!)
    }

    private fun stopPolling() {
        pollRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = ShotRenaService.isRunning
        val hasPerms = PermissionActivity.hasAllPermissions(this)

        val newState = if (isRunning && hasPerms) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val newLabel = if (isRunning && hasPerms) getString(R.string.tile_on) else getString(R.string.tile_off)

        // FIX: Force the Tile to use our new 'ic_save_as' icon
        tile.icon = Icon.createWithResource(this, R.drawable.ic_save_as)

        // Optimization: Only update if State OR Label changed to prevent flickering/battery drain
        if (tile.state != newState || tile.label != newLabel) {
            tile.state = newState
            tile.label = newLabel
            tile.updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        if (!PermissionActivity.hasAllPermissions(this)) {
            val intent = Intent(this, PermissionActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val serviceIntent = Intent(this, ShotRenaService::class.java)
        if (ShotRenaService.isRunning) {
            stopService(serviceIntent)
            Toast.makeText(this, getString(R.string.engine_stopping), Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, getString(R.string.engine_starting), Toast.LENGTH_SHORT).show()
        }
        updateTileState()
    }
}