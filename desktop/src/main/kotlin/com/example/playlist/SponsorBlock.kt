package com.example.playlist

import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * SponsorBlock 공개 데이터에서 뮤직비디오의 '음악이 아닌 구간'(인트로 대사, 아웃트로 등)을 가져와요.
 * 결과는 밀리초 구간 목록이고, 데이터가 없으면 빈 목록이에요.
 */
object SponsorBlock {
    private val cache = ConcurrentHashMap<String, List<LongRange>>()

    fun fetch(videoId: String): List<LongRange> {
        cache[videoId]?.let { return it }
        val cats = URLEncoder.encode("[\"music_offtopic\"]", "UTF-8")
        val body = Net.get("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$cats")
        val list = if (body == null) emptyList() else runCatching {
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val seg = arr.optJSONObject(i)?.optJSONArray("segment") ?: return@mapNotNull null
                val s = (seg.getDouble(0) * 1000).toLong()
                val e = (seg.getDouble(1) * 1000).toLong()
                if (e - s >= 1500) s..e else null
            }
        }.getOrDefault(emptyList())
        cache[videoId] = list
        return list
    }
}
