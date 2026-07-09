package com.rahimgujjar.shotrena

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "ShotRena.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        // We store the MediaStore ID (Long) to uniquely identify files
        db.execSQL("CREATE TABLE renamed_files (media_id INTEGER PRIMARY KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS renamed_files")
        onCreate(db)
    }

    fun markAsRenamed(mediaId: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("media_id", mediaId)
        }
        // Ignore conflict ensures we don't crash if ID exists
        db.insertWithOnConflict("renamed_files", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun isRenamed(mediaId: Long): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM renamed_files WHERE media_id = ?", arrayOf(mediaId.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }
}