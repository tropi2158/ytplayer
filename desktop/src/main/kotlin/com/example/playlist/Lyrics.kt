package com.example.playlist

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class LyricLine(val ms: Long, val text: String)

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data object NotFound : LyricsState
    data class Synced(val lines: List<LyricLine>) : LyricsState
    data class Plain(val text: String) : LyricsState
}

/** 무료 가사 데이터베이스 LRCLIB에서 가사 찾기 */
object Lyrics {
    private val cache = ConcurrentHashMap<String, LyricsState>()

    private val noise = Regex(
        "(?i)\\b(official|music video|m/?v|lyrics?|lyric video|audio|visualizer|performance video|4k|hd|hq|special clip|teaser)\\b"
    )

    /** 유튜브 제목을 (가수, 곡명)으로 정리 */
    fun clean(title: String, uploader: String): Pair<String, String> {
        var t = title
            .replace(Regex("[\\(\\[【「『].*?[\\)\\]】」』]"), " ")
            .replace(Regex("(?i)\\s(feat|ft)\\.?\\s.*$"), " ")
            .replace(noise, " ")
            .replace(Regex("[|•/]+.*$"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '_', '"', '\'')
        var artist = uploader.replace(Regex("(?i)\\s*-\\s*topic$|vevo$|official$|\\bofficial\\b"), "").trim()
        val dash = Regex("\\s[-–—]\\s").find(t)
        if (dash != null) {
            artist = t.substring(0, dash.range.first).trim()
            t = t.substring(dash.range.last + 1).trim()
        }
        return artist to t
    }

    fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        val stamp = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)]")
        lrc.lines().forEach { line ->
            val stamps = stamp.findAll(line).toList()
            if (stamps.isEmpty()) return@forEach
            val text = line.substring(stamps.last().range.last + 1).trim()
            stamps.forEach { m ->
                val ms = m.groupValues[1].toLong() * 60_000 + (m.groupValues[2].toDouble() * 1000).toLong()
                out.add(LyricLine(ms, text))
            }
        }
        return out.sortedBy { it.ms }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun pick(arr: JSONArray, durSec: Long): JSONObject? {
        val items = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        fun close(o: JSONObject) = durSec <= 0 || abs(o.optDouble("duration", 0.0) - durSec) <= 8
        fun synced(o: JSONObject) = o.optString("syncedLyrics").let { it.isNotBlank() && it != "null" }
        fun plain(o: JSONObject) = o.optString("plainLyrics").let { it.isNotBlank() && it != "null" }
        return items.firstOrNull { synced(it) && close(it) }
            ?: items.firstOrNull { plain(it) && close(it) }
            ?: items.firstOrNull { synced(it) }
            ?: items.firstOrNull { plain(it) }
    }

    fun fetch(url: String, title: String, uploader: String, durSec: Long): LyricsState {
        cache[url]?.let { return it }
        val (artist, name) = clean(title, uploader)
        val queries = buildList {
            if (artist.isNotBlank()) add("https://lrclib.net/api/search?track_name=${enc(name)}&artist_name=${enc(artist)}")
            add("https://lrclib.net/api/search?q=${enc("$artist $name".trim())}")
            add("https://lrclib.net/api/search?q=${enc(name)}")
        }
        var result: LyricsState = LyricsState.NotFound
        for (q in queries) {
            val body = Net.get(q) ?: continue
            val o = runCatching { pick(JSONArray(body), durSec) }.getOrNull() ?: continue
            val syncedText = o.optString("syncedLyrics")
            val plainText = o.optString("plainLyrics")
            result = if (syncedText.isNotBlank() && syncedText != "null") {
                val lines = parseLrc(syncedText)
                if (lines.isNotEmpty()) LyricsState.Synced(lines) else LyricsState.Plain(plainText)
            } else LyricsState.Plain(plainText)
            break
        }
        cache[url] = result
        return result
    }
}
