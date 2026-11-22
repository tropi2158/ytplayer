package com.example.playlist

import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** 앱 데이터 폴더 (%APPDATA%\Playlist) */
object AppDirs {
    val root: File by lazy {
        val base = System.getenv("APPDATA")?.let { File(it) } ?: File(System.getProperty("user.home"), ".config")
        File(base, "Playlist").apply { mkdirs() }
    }
    val local: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let { File(it) } ?: root
        File(base, "Playlist").apply { mkdirs() }
    }
}

/**
 * 안드로이드 SharedPreferences와 같은 방식으로 쓰는 설정 저장소 (settings.json 하나에 저장)
 */
class Prefs(private val file: File) {
    private val map = ConcurrentHashMap<String, Any>()
    private val saver = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "prefs").apply { isDaemon = true } }
    private var pending: ScheduledFuture<*>? = null

    init {
        runCatching {
            if (file.exists()) {
                val o = JSONObject(file.readText(Charsets.UTF_8))
                o.keys().forEach { k -> o.opt(k)?.let { v -> if (v != JSONObject.NULL) map[k] = v } }
            }
        }
    }

    fun getString(k: String, d: String?): String? = (map[k] as? String) ?: d
    fun getBoolean(k: String, d: Boolean): Boolean = (map[k] as? Boolean) ?: d
    fun getInt(k: String, d: Int): Int = (map[k] as? Number)?.toInt() ?: d
    fun getLong(k: String, d: Long): Long = (map[k] as? Number)?.toLong() ?: d
    fun getFloat(k: String, d: Float): Float = (map[k] as? Number)?.toFloat() ?: d

    fun edit(): Editor = Editor()

    inner class Editor {
        private val changes = HashMap<String, Any?>()
        fun putString(k: String, v: String?) = apply { changes[k] = v }
        fun putBoolean(k: String, v: Boolean) = apply { changes[k] = v }
        fun putInt(k: String, v: Int) = apply { changes[k] = v }
        fun putLong(k: String, v: Long) = apply { changes[k] = v }
        fun putFloat(k: String, v: Float) = apply { changes[k] = v.toDouble() }
        fun remove(k: String) = apply { changes[k] = null }
        fun apply() {
            commit()
        }

        fun commit(): Boolean {
            changes.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            scheduleSave()
            return true
        }
    }

    @Synchronized
    private fun scheduleSave() {
        pending?.cancel(false)
        pending = saver.schedule({ flush() }, 400, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun flush() {
        runCatching {
            val o = JSONObject()
            map.forEach { (k, v) -> o.put(k, v) }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString(), Charsets.UTF_8)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }
}
