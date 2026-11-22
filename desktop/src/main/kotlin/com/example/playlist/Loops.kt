package com.example.playlist


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** A-B 구간 반복 상태 (화면과 재생 서비스가 같이 씀) */
object AbLoop {
    var url by mutableStateOf("")
    var a by mutableLongStateOf(-1L)
    var b by mutableLongStateOf(-1L)

    val active: Boolean get() = url.isNotEmpty() && a >= 0 && b > a

    /** 누를 때마다: A 지정 -> B 지정(반복 시작) -> 해제 */
    fun step(currentUrl: String, pos: Long): String {
        return when {
            url != currentUrl || a < 0 -> {
                url = currentUrl; a = pos; b = -1L
                "A 지점을 정했어요. 끝낼 곳에서 한 번 더 누르세요"
            }
            b < 0 -> {
                if (pos <= a + 1000) {
                    "B 지점은 A보다 1초 이상 뒤여야 해요"
                } else {
                    b = pos
                    "A-B 구간을 반복해요"
                }
            }
            else -> {
                clear()
                "구간 반복을 껐어요"
            }
        }
    }

    fun clear() {
        url = ""; a = -1L; b = -1L
    }
}

/** 10분이 넘는 긴 곡(믹스, 라이브 등)은 들은 위치를 기억해서 다음에 이어서 재생 */
object LongPositions {
    private val map = HashMap<String, Long>()
    private var loaded = false

    @Synchronized
    private fun ensure(prefs: Prefs) {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(prefs.getString("long_pos", "{}") ?: "{}")
            o.keys().forEach { map[it] = o.optLong(it) }
        }
    }

    @Synchronized
    fun get(prefs: Prefs, url: String): Long {
        ensure(prefs)
        return map[url] ?: 0L
    }

    @Synchronized
    fun put(prefs: Prefs, url: String, pos: Long) {
        ensure(prefs)
        if (pos <= 0L) map.remove(url) else map[url] = pos
        while (map.size > 200) map.remove(map.keys.first())
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("long_pos", o.toString()).apply()
    }
}
