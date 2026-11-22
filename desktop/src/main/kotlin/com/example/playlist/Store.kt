package com.example.playlist


import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** '좋아요' 플레이리스트의 고정 id */
const val FAV_ID = "__fav__"

/** 최근 들은 곡 (어느 플레이리스트에서 들었는지 함께 저장) */
data class RecentItem(val pid: String, val track: Track)

/** 플레이리스트 저장/불러오기/백업 (화면과 재생 서비스가 같이 씀) */
object Store {
    fun newId(): String = UUID.randomUUID().toString().take(8)

    fun trackToJson(t: Track): JSONObject =
        JSONObject().put("u", t.url).put("t", t.title).put("a", t.artist)
            .put("i", t.thumb ?: "").put("d", t.durationSec)

    fun parseTracks(arr: JSONArray): List<Track> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val u = o.optString("u")
        if (u.isBlank()) null
        else Track(u, o.optString("t"), o.optString("a"), o.optString("i").ifBlank { null }, o.optLong("d"))
    }

    fun toJson(list: List<Playlist>): JSONArray {
        val arr = JSONArray()
        list.forEach { p ->
            val ta = JSONArray()
            p.tracks.forEach { ta.put(trackToJson(it)) }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("url", p.url).put("tracks", ta).put("tag", p.tag))
        }
        return arr
    }

    fun parsePlaylists(arr: JSONArray): List<Playlist> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        Playlist(
            id = o.optString("id").ifBlank { newId() },
            name = o.optString("name").ifBlank { "플레이리스트" },
            url = o.optString("url"),
            tracks = parseTracks(o.optJSONArray("tracks") ?: JSONArray()),
            tag = o.optString("tag")
        )
    }

    fun load(prefs: Prefs): List<Playlist> {
        val raw = prefs.getString("playlists_v2", null)
        if (raw != null) {
            return runCatching { parsePlaylists(JSONArray(raw)) }.getOrDefault(emptyList())
        }
        // 이전 버전(플레이리스트 1개)에서 옮겨오기
        val oldUrl = prefs.getString("url", "") ?: ""
        if (oldUrl.isBlank()) return emptyList()
        val oldTracks = runCatching { parseTracks(JSONArray(prefs.getString("tracks", "[]"))) }
            .getOrDefault(emptyList())
        val oldName = (prefs.getString("title", "") ?: "").ifBlank { "내 플레이리스트" }
        return listOf(Playlist(newId(), oldName, oldUrl, oldTracks))
    }

    fun save(prefs: Prefs, list: List<Playlist>) {
        prefs.edit().putString("playlists_v2", toJson(list).toString()).apply()
    }

    fun loadRecent(prefs: Prefs): List<RecentItem> = runCatching {
        val arr = JSONArray(prefs.getString("recent", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val t = parseTracks(JSONArray().put(o.optJSONObject("t") ?: return@mapNotNull null)).firstOrNull()
                ?: return@mapNotNull null
            RecentItem(o.optString("p"), t)
        }
    }.getOrDefault(emptyList())

    fun saveRecent(prefs: Prefs, list: List<RecentItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("p", it.pid).put("t", trackToJson(it.track))) }
        prefs.edit().putString("recent", arr.toString()).apply()
    }

    fun exportJson(list: List<Playlist>): String =
        JSONObject().put("app", "ytplaylist").put("version", 1).put("playlists", toJson(list)).toString(2)

    fun parseExport(text: String): List<Playlist> {
        val t = text.trim()
        return if (t.startsWith("[")) parsePlaylists(JSONArray(t))
        else parsePlaylists(JSONObject(t).getJSONArray("playlists"))
    }
}
