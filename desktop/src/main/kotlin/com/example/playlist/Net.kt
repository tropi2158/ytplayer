package com.example.playlist

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 가사/구간 정보 같은 작은 요청에 쓰는 공용 HTTP 클라이언트 */
object Net {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun getBytes(url: String): ByteArray? = try {
        val req = Request.Builder().url(url).header("User-Agent", "Playlist/2.0 (personal desktop app)").build()
        client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
    } catch (e: Exception) {
        null
    }

    fun get(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", "Playlist/2.0 (personal Android app)").build()
        client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (e: Exception) {
        null
    }
}

/** 유튜브 링크에서 영상 ID(11자리) 꺼내기 */
fun videoIdOf(url: String): String? =
    Regex("(?:v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
