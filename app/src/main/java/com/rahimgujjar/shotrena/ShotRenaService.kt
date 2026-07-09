package com.rahimgujjar.shotrena

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.core.net.toUri
import java.util.LinkedList
@Suppress("SpellCheckingInspection")

class ShotRenaService : Service() {

    companion object {
        var isRunning = false
        val fileQueue = LinkedList<Pair<Long, Uri>>()
        const val ACTION_STATUS_CHANGED = "com.rahimgujjar.shotrena.STATUS_CHANGED"
        const val CHANNEL_ID = "ShotRenaChannel"
        private const val PREFS_NAME = "ShotRenaQueue"
        private const val KEY_QUEUE = "pending_uris"

        fun removeFirstAndSave(context: Context) {
            if (fileQueue.isNotEmpty()) {
                fileQueue.removeFirst()
                saveQueue(context)
            }
        }

        fun saveQueue(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val set = HashSet<String>()
            fileQueue.forEach { pair ->
                set.add("${pair.first}|${pair.second}")
            }
            prefs.edit { putStringSet(KEY_QUEUE, set) }
        }

        fun loadQueue(context: Context) {
            fileQueue.clear()
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_QUEUE, emptySet()) ?: emptySet()
            val list = ArrayList<Pair<Long, Uri>>()
            set.forEach { str ->
                try {
                    val parts = str.split("|")
                    if (parts.size == 2) {
                        list.add(Pair(parts[0].toLong(), parts[1].toUri()))
                    }
                } catch (_: Exception) { }
            }
            list.sortBy { it.first }
            fileQueue.addAll(list)
        }
    }

    private var contentObserver: ContentObserver? = null
    private lateinit var dbHelper: DatabaseHelper
    private val handler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        dbHelper = DatabaseHelper(this)
        loadQueue(this)

        updateNotification()

        startWatcher()
        broadcastStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != "STOP_SERVICE_ACTION") {
            updateNotification()
        }
        return START_STICKY
    }

    private fun broadcastStatus() {
        sendBroadcast(Intent(ACTION_STATUS_CHANGED))
    }

    fun updateNotification() {
        val count = fileQueue.size
        safeStartForeground(count)
    }

    private fun safeStartForeground(pendingCount: Int) {
        try {
            val notification = buildNotification(pendingCount)
            val manager = getSystemService(NotificationManager::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = if (pendingCount > 0) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_MIN
                val channel = NotificationChannel(CHANNEL_ID, "ShotRena Service", importance)
                channel.setShowBadge(pendingCount > 0)
                manager.createNotificationChannel(channel)
            }

            startForeground(1, notification)

        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun buildNotification(pendingCount: Int): Notification {
        val openIntent = Intent(this, RenameActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ShotRenaTileService::class.java).apply {
            action = "STOP_SERVICE_ACTION"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (pendingCount > 0) "Action Required" else "ShotRena Active"
        val text = if (pendingCount > 0) "$pendingCount Screenshots Pending Rename" else "Monitoring (Silent)..."

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_save_as)
            .setContentIntent(pendingIntent)
            .addAction(Notification.Action.Builder(null, "STOP", stopPendingIntent).build())
            .setOngoing(true)
            .build()
    }

    private fun startWatcher() {
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                debounceRunnable?.let { handler.removeCallbacks(it) }
                debounceRunnable = Runnable { scanForScreenshots() }
                handler.postDelayed(debounceRunnable!!, 800)
            }
        }
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, contentObserver!!)
    }

    private fun scanForScreenshots() {
        val recentTime = (System.currentTimeMillis() / 1000) - 10
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(recentTime.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    if (dbHelper.isRenamed(id)) continue

                    var alreadyInQueue = false
                    for (pair in fileQueue) {
                        if (pair.first == id) { alreadyInQueue = true; break }
                    }
                    if (alreadyInQueue) continue

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    fileQueue.add(Pair(id, uri))
                }
            }

            if (fileQueue.isNotEmpty()) {
                saveQueue(this)
                updateNotification()
                launchRenameUI()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun launchRenameUI() {
        val intent = Intent(this, RenameActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    override fun onDestroy() {
        isRunning = false
        broadcastStatus()
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
