package com.example.playlist


import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 청취 기록: 곡별 재생 횟수/시간, 날짜별, 시간대별 들은 시간 (재생 서비스가 기록) */
object Stats {
    data class TrackStat(
        val url: String, val pid: String, val title: String, val artist: String, val thumb: String?,
        val count: Int, val ms: Long, val last: Long
    )

    data class Snapshot(val tracks: List<TrackStat>, val daily: Map<String, Long>, val hourly: List<Long>)

    private val tracks = HashMap<String, TrackStat>()
    private val daily = HashMap<String, Long>()
    private val hourly = LongArray(24)
    private var loaded = false

    fun dayKey(ms: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

    @Synchronized
    private fun ensure(prefs: Prefs) {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(prefs.getString("stats", "{}") ?: "{}")
            o.optJSONArray("t")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i) ?: continue
                    val u = t.optString("u")
                    if (u.isBlank()) continue
                    tracks[u] = TrackStat(
                        u, t.optString("p"), t.optString("ti"), t.optString("a"),
                        t.optString("i").ifBlank { null }, t.optInt("c"), t.optLong("m"), t.optLong("l")
                    )
                }
            }
            o.optJSONObject("d")?.let { d -> d.keys().forEach { k -> daily[k] = d.optLong(k) } }
            o.optJSONArray("h")?.let { h -> for (i in 0 until minOf(24, h.length())) hourly[i] = h.optLong(i) }
        }
    }

    @Synchronized
    fun addListen(
        prefs: Prefs, url: String, pid: String, title: String, artist: String, thumb: String?,
        dtMs: Long, countPlay: Boolean
    ) {
        ensure(prefs)
        if (url.isBlank()) return
        val now = System.currentTimeMillis()
        val old = tracks[url]
        tracks[url] = TrackStat(
            url,
            pid.ifEmpty { old?.pid ?: "" },
            title.ifEmpty { old?.title ?: "" },
            artist.ifEmpty { old?.artist ?: "" },
            thumb ?: old?.thumb,
            (old?.count ?: 0) + if (countPlay) 1 else 0,
            (old?.ms ?: 0L) + dtMs,
            now
        )
        val day = dayKey(now)
        daily[day] = (daily[day] ?: 0L) + dtMs
        hourly[Calendar.getInstance().get(Calendar.HOUR_OF_DAY)] += dtMs
    }

    @Synchronized
    fun save(prefs: Prefs) {
        ensure(prefs)
        val keep = tracks.values.sortedByDescending { it.last }.take(600)
        val t = JSONArray()
        keep.forEach {
            t.put(
                JSONObject().put("u", it.url).put("p", it.pid).put("ti", it.title).put("a", it.artist)
                    .put("i", it.thumb ?: "").put("c", it.count).put("m", it.ms).put("l", it.last)
            )
        }
        val d = JSONObject()
        daily.keys.sorted().takeLast(90).forEach { d.put(it, daily[it]) }
        val h = JSONArray()
        hourly.forEach { h.put(it) }
        prefs.edit().putString("stats", JSONObject().put("t", t).put("d", d).put("h", h).toString()).apply()
    }

    @Synchronized
    fun snapshot(prefs: Prefs): Snapshot {
        ensure(prefs)
        return Snapshot(tracks.values.toList(), HashMap(daily), hourly.toList())
    }

    @Synchronized
    fun clear(prefs: Prefs) {
        tracks.clear()
        daily.clear()
        hourly.fill(0L)
        loaded = true
        prefs.edit().remove("stats").apply()
    }
}
