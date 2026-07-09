package com.rahimgujjar.shotrena

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Utils {

    // Generates the suffix: _26-01-26_08-42-50-PM
    fun getTimestampSuffix(): String {
        val sdf = SimpleDateFormat("_dd-MM-yy_hh-mm-ss-a", Locale.US)
        return sdf.format(Date())
    }

    // Sanitizes the input name for file system safety
    fun sanitizeFilename(input: String): String {
        // 1. Replace dangerous characters with underscore
        var safe = input.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        // 2. Remove newlines explicitly (safety net)
        safe = safe.replace("\n", "").replace("\r", "")

        // 3. Trim surrounding whitespace/underscores
        safe = safe.trim { it <= ' ' || it == '_' }

        // 4. Limit length (Base name max 200 chars to be safe)
        if (safe.length > 200) {
            safe = safe.substring(0, 200)
        }

        return safe
    }
}