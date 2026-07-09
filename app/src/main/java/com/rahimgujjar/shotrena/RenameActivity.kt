package com.rahimgujjar.shotrena

import android.content.ContentValues
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class RenameActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var imageView: ZoomImageView
    private lateinit var inputName: EditText
    private lateinit var btnConfirm: Button
    private lateinit var lblQueue: TextView
    private lateinit var dbHelper: DatabaseHelper

    private var currentId: Long = -1
    private var currentUri: Uri? = null

    private var lastSpacePressTime: Long = 0

    companion object {
        private const val DOUBLE_SPACE_MARKER = '\uFFFF'
        private const val DOUBLE_SPACE_THRESHOLD_MS = 600L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rename)

        dbHelper = DatabaseHelper(this)
        setFinishOnTouchOutside(false)

        scrollView = findViewById(R.id.main_scroll_view)
        imageView = findViewById(R.id.img_preview)
        inputName = findViewById(R.id.input_name)
        btnConfirm = findViewById(R.id.btn_confirm)
        lblQueue = findViewById(R.id.lbl_queue)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            inputName.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }

        // FIX 1: Auto-Scroll Sensor (Layout Listener)
        // This detects when the keyboard opens/closes by checking the view height
        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom

            // If keyboard is open (height diff > 15% of screen)
            if (keypadHeight > screenHeight * 0.15) {
                // Keyboard is OPEN. Force scroll to bottom to show Button.
                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_DOWN)
                    inputName.requestFocus()
                }
            }
        }

        // Input Filter (Keyboard Fix)
        val filenameFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val out = StringBuilder()
            var changed = false

            for (i in start until end) {
                val ch = source[i]
                when (ch) {
                    '\n' -> { out.append('_'); changed = true }
                    ' ' -> {
                        val now = System.currentTimeMillis()
                        val isDoubleTap = (now - lastSpacePressTime) < DOUBLE_SPACE_THRESHOLD_MS
                        lastSpacePressTime = now

                        val prevIndex = dstart - 1
                        val prevIsUnderscore = prevIndex >= 0 && dest.length > prevIndex && dest[prevIndex] == '_'

                        if (isDoubleTap && prevIsUnderscore) {
                            out.append(DOUBLE_SPACE_MARKER)
                            changed = true
                        } else {
                            out.append('_')
                            changed = true
                        }
                    }

                    else -> out.append(ch)
                }
            }
            if (changed) out.toString() else null
        }

        inputName.filters = arrayOf(filenameFilter)

        // Text Watcher
        inputName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                val markerIndex = s.toString().indexOf(DOUBLE_SPACE_MARKER)

                if (markerIndex >= 0) {
                    inputName.post {
                        try {
                            val currentText = inputName.text
                            val currentMarkerIndex = currentText.toString().indexOf(DOUBLE_SPACE_MARKER)

                            if (currentMarkerIndex >= 0) {
                                val prevIdx = currentMarkerIndex - 1
                                if (prevIdx >= 0 && currentText[prevIdx] == '_') {
                                    currentText.replace(prevIdx, currentMarkerIndex + 1, " ")
                                    inputName.setSelection((prevIdx + 1).coerceAtMost(currentText.length))
                                } else {
                                    currentText.replace(currentMarkerIndex, currentMarkerIndex + 1, " ")
                                    inputName.setSelection((currentMarkerIndex + 1).coerceAtMost(currentText.length))
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        })

        inputName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnConfirm.performClick()
                true
            } else {
                false
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (ShotRenaService.fileQueue.isNotEmpty()) {
                    Toast.makeText(this@RenameActivity, getString(R.string.must_rename), Toast.LENGTH_SHORT).show()
                } else {
                    finishAndRemoveTask()
                }
            }
        })

        btnConfirm.setOnClickListener {
            val rawName = inputName.text.toString()
            if (rawName.isBlank()) {
                Toast.makeText(this, getString(R.string.enter_valid_name), Toast.LENGTH_SHORT).show()
            } else {
                performRename(rawName)
            }
        }

        loadNextImage()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        loadNextImage()
    }

    private fun loadNextImage() {
        val nextItem = ShotRenaService.fileQueue.peek()
        if (nextItem == null) { finishAndRemoveTask(); return }
        if (dbHelper.isRenamed(nextItem.first)) { ShotRenaService.removeFirstAndSave(this); loadNextImage(); return }

        currentId = nextItem.first
        currentUri = nextItem.second
        val count = ShotRenaService.fileQueue.size
        lblQueue.text = if (count > 1) getString(R.string.queue_format, count) else getString(R.string.rename_screenshot)

        try {
            imageView.setImageURI(currentUri)
            imageView.resetZoom()
            inputName.text.clear()

            // Focus Input
            inputName.requestFocus()

            // Also trigger scroll immediately just in case keyboard was already open
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }

        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.error_image), Toast.LENGTH_SHORT).show()
            ShotRenaService.removeFirstAndSave(this)
            updateService()
            loadNextImage()
        }
    }

    private fun performRename(userText: String) {
        if (currentUri == null) return
        try {
            val sanitizedBase = Utils.sanitizeFilename(userText)
            val suffix = Utils.getTimestampSuffix()

            // FIX 2: No Extension. "MyFile_Time". OS adds .png/.jpg automatically.
            val finalName = "$sanitizedBase$suffix"

            val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, finalName) }
            contentResolver.update(currentUri!!, values, null, null)
            dbHelper.markAsRenamed(currentId)

            Toast.makeText(this, getString(R.string.rename_success), Toast.LENGTH_SHORT).show()
            ShotRenaService.removeFirstAndSave(this)
            updateService()
            loadNextImage()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.rename_failed, e.message), Toast.LENGTH_LONG).show()
            ShotRenaService.removeFirstAndSave(this)
            updateService()
            loadNextImage()
        }
    }

    private fun updateService() {
        val serviceIntent = Intent(this, ShotRenaService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}