package jp.essential.app.feature.notificationlog

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val LOG_RETENTION_MILLIS = 3L * 24 * 60 * 60 * 1000

internal data class NotificationLogEntry(
    val id: Long,
    val packageName: String,
    val appName: String,
    val title: String,
    val body: String,
    val receivedAt: Long,
    val kept: Boolean,
)

/** 通知の本文をバックアップ対象外の端末内DBに保存する。処理はIOスレッドから呼ぶ。 */
internal class NotificationLogStore(context: Context, databaseName: String = "notification-log.db") :
    SQLiteOpenHelper(context, java.io.File(context.noBackupFilesDir, databaseName).absolutePath, null, 1) {
    private val updates = MutableStateFlow(0L)
    val revision = updates.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE logs (id INTEGER PRIMARY KEY AUTOINCREMENT, package_name TEXT NOT NULL, app_name TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, received_at INTEGER NOT NULL, kept INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX logs_expiry ON logs(kept, received_at)")
        db.execSQL("CREATE INDEX logs_order ON logs(received_at DESC, id DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun record(packageName: String, appName: String, title: String, body: String, now: Long = System.currentTimeMillis()): Long {
        val db = writableDatabase
        db.beginTransaction()
        val id: Long
        try {
            pruneRows(db, now)
            id = db.insertOrThrow("logs", null, ContentValues().apply {
                put("package_name", packageName.take(512))
                put("app_name", appName.take(256))
                put("title", title.take(4096))
                put("body", body.take(16384))
                put("received_at", now)
                put("kept", 0)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        updates.value += 1
        return id
    }

    @Synchronized
    fun list(keptOnly: Boolean, limit: Int, now: Long = System.currentTimeMillis()): List<NotificationLogEntry> {
        prune(now)
        return readableDatabase.query("logs", null, if (keptOnly) "kept=1" else null, null,
            null, null, "received_at DESC, id DESC", (limit.coerceAtLeast(1) + 1).toString()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(NotificationLogEntry(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getLong(5), cursor.getInt(6) != 0))
                }
            }
        }
    }

    @Synchronized
    fun keep(id: Long, kept: Boolean, now: Long = System.currentTimeMillis()) {
        writableDatabase.update("logs", ContentValues().apply { put("kept", if (kept) 1 else 0) }, "id=?", arrayOf(id.toString()))
        pruneRows(writableDatabase, now)
        updates.value += 1
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete("logs", "id=?", arrayOf(id.toString()))
        updates.value += 1
    }

    @Synchronized
    fun prune(now: Long = System.currentTimeMillis()) {
        if (pruneRows(writableDatabase, now) > 0) updates.value += 1
    }

    private fun pruneRows(db: SQLiteDatabase, now: Long): Int =
        db.delete("logs", "kept=0 AND received_at<=?", arrayOf((now - LOG_RETENTION_MILLIS).toString()))

    companion object {
        @Volatile private var instance: NotificationLogStore? = null
        fun get(context: Context): NotificationLogStore = instance ?: synchronized(this) {
            instance ?: NotificationLogStore(context.applicationContext).also { instance = it }
        }
    }
}
