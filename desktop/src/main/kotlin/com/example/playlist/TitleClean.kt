package com.example.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 유튜브식 긴 제목을 음악 앱처럼 깔끔하게 보여주기.
 * 저장된 원래 제목은 그대로 두고 '보여줄 때만' 정리해요. (곡 정보에서는 원래 제목)
 */
object TitleClean {
    var on by mutableStateOf(true)

    private val noise = Regex(
        "(official|m/v|\\bmv\\b|music video|lyric|lyrics|visuali[sz]er|\\baudio\\b|\\bhd\\b|\\b4k\\b|가사|공식|뮤직비디오|歌詞|\\bpv\\b)",
        RegexOption.IGNORE_CASE
    )
    private val bracket = Regex("""[\[【「『(（][^\]】」』)）]*[\]】」』)）]""")
    private val artistNoise = Regex("""\s*(-\s*Topic|VEVO|Official|official|公式|공식)\s*$""")

    fun title(raw: String, artist: String = ""): String {
        if (!on || raw.isBlank()) return raw
        var t = raw
        // 맨 앞의 [TAB] [MV] 같은 꼬리표는 항상 떼기
        t = t.replace(Regex("""^\s*[\[【][^\]】]{1,20}[\]】]\s*"""), "")
        // 괄호 중 official/mv/lyrics 같은 군더더기만 떼기 (feat., remix, cover 등 의미 있는 건 남김)
        t = bracket.replace(t) { m -> if (noise.containsMatchIn(m.value)) "" else m.value }
        // " | " 뒤는 보통 게임/앨범/채널 설명
        val pipe = t.split(Regex("""\s[|｜]\s""")).first()
        if (pipe.trim().length >= 3) t = pipe
        // "가수 - 곡" 에서 가수가 채널 이름과 같으면 곡만
        val a = artist(artist)
        if (a.isNotBlank()) {
            val m = Regex("""^\s*${Regex.escape(a)}\s*[-–—:]\s*""", RegexOption.IGNORE_CASE).find(t)
            if (m != null && t.length - m.value.length >= 2) t = t.substring(m.value.length)
        }
        t = t.replace(Regex("""\s{2,}"""), " ").trim().trim('-', '|', '·', ' ')
        return t.ifBlank { raw }
    }

    fun artist(raw: String): String {
        if (!on || raw.isBlank()) return raw
        return artistNoise.replace(raw, "").trim().ifBlank { raw }
    }
}

/** 화면에 보여줄 제목/가수 */
val Track.shownTitle: String get() = TitleClean.title(title, artist)
val Track.shownArtist: String get() = TitleClean.artist(artist)
