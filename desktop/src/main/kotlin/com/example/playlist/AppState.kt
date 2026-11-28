package com.example.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.io.File
import java.util.Calendar
import kotlin.random.Random

enum class Panel { NONE, SETTINGS, STATS, SEARCH, QUEUE, EQ }

/** 앱 전체 상태 (안드로이드 ViewModel을 PC용으로 옮긴 것) */
class AppState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val prefs = Prefs(File(AppDirs.root, "settings.json"))

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toasts = _toasts.asSharedFlow()
    fun toast(m: String) {
        _toasts.tryEmit(m)
    }

    val engine = Engine(prefs, scope) { toast(it) }

    // ================= 화면 =================
    var panel by mutableStateOf(Panel.NONE)
    var openId by mutableStateOf<String?>(null)
    var showLyrics by mutableStateOf(true)
    /** 트레이에 숨어 있다가 알람 등으로 창을 보여달라는 요청 */
    var showWindowRequest by mutableIntStateOf(0)
    /** 단축키로 전체 화면 플레이어 열기/닫기 요청 */
    var expandRequest by mutableIntStateOf(0)
    var collapseRequest by mutableIntStateOf(0)
    var expanded by mutableStateOf(false)

    // ================= 플레이리스트 =================
    var playlists by mutableStateOf(withFav(Store.load(prefs)))
        private set
    var favUrls by mutableStateOf<Set<String>>(emptySet())
        private set
    var recent by mutableStateOf(Store.loadRecent(prefs))
        private set
    var newUrls by mutableStateOf<Set<String>>(emptySet())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var retry by mutableStateOf<(() -> Unit)?>(null)
        private set
    val tags: List<String> get() = playlists.map { it.tag }.filter { it.isNotBlank() }.distinct()
    val hasUserPlaylists: Boolean get() = playlists.any { it.id != FAV_ID }

    // 오프라인 저장 진행 (id -> 받은 수/전체)
    val offlineProgress = mutableStateMapOf<String, Pair<Int, Int>>()
    private val offlineJobs = HashMap<String, Job>()

    // ================= 재생 표시용 =================
    val current: QItem? get() = engine.current
    val playingUrl: String get() = engine.current?.track?.url ?: ""
    val playingPid: String get() = engine.current?.pid ?: ""
    var artColors by mutableStateOf<List<Color>>(emptyList())
        private set
    var artColor by mutableStateOf<Color?>(null)
        private set
    private var colorFor = ""

    // ================= 가사 / 검색 =================
    var lyrics by mutableStateOf<LyricsState>(LyricsState.Idle)
        private set
    private var lyricsUrl = ""
    var searchResults by mutableStateOf<List<Track>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var lastQuery by mutableStateOf("")
        private set

    // ================= 캐시 정보 =================
    var cacheBytes by mutableLongStateOf(0L)
        private set
    var offlineBytes by mutableLongStateOf(0L)
        private set
    var cacheVersion by mutableIntStateOf(0)
        private set

    var online by mutableStateOf(true)
        private set

    // ================= 설정 =================
    var themeMode by mutableIntStateOf(prefs.getInt("theme_mode", 2))
        private set
    var nightStart by mutableIntStateOf(prefs.getInt("night_start", 21))
        private set
    var nightEnd by mutableIntStateOf(prefs.getInt("night_end", 7))
        private set
    var accent by mutableIntStateOf(prefs.getInt("accent", 0))
        private set
    var glass by mutableStateOf(prefs.getBoolean("glass", true))
        private set
    var useArtColor by mutableStateOf(prefs.getBoolean("art_color", true))
        private set
    var fontScale by mutableFloatStateOf(prefs.getFloat("font_scale", 1f))
        private set
    var resumeEnabled by mutableStateOf(prefs.getBoolean("resume", true))
        private set
    var prefetch by mutableStateOf(prefs.getBoolean("prefetch", true))
        private set
    var lowQuality by mutableStateOf(prefs.getBoolean("low_quality", false))
        private set
    var skipOfftopic by mutableStateOf(prefs.getBoolean("skip_offtopic", true))
        private set
    var fadeMs by mutableIntStateOf(prefs.getInt("fade_ms", 0))
        private set
    var longResume by mutableStateOf(prefs.getBoolean("long_resume", true))
        private set
    var checkUpdates by mutableStateOf(prefs.getBoolean("check_updates", true))
        private set
    var cacheMb by mutableIntStateOf(prefs.getInt("cache_mb", 2048))
        private set
    var closeToTray by mutableStateOf(prefs.getBoolean("close_to_tray", true))
        private set
    var alarmOn by mutableStateOf(prefs.getBoolean("alarm_on", false))
        private set
    var alarmHour by mutableIntStateOf(prefs.getInt("alarm_h", 7))
        private set
    var alarmMinute by mutableIntStateOf(prefs.getInt("alarm_m", 0))
        private set
    var alarmPid by mutableStateOf(prefs.getString("alarm_pid", "") ?: "")
        private set
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded_pc", false))
        private set

    // ================= 수면 타이머 =================
    var sleepRemainingMs by mutableLongStateOf(0L)
        private set
    var sleepEndOfTrack by mutableStateOf(false)
        private set
    private var sleepJob: Job? = null
    val sleepActive: Boolean get() = sleepRemainingMs > 0L || sleepEndOfTrack

    init {
        favUrls = playlists.first { it.id == FAV_ID }.tracks.map { it.url }.toSet()
        AudioCache.limitBytes = cacheMb * 1024L * 1024L
        engine.onTrackChanged = { item -> onTrackChanged(item) }
        if (resumeEnabled) restoreSession()
        scope.launch {
            online = withContext(Dispatchers.IO) { Net.get("https://www.youtube.com/robots.txt") != null }
            if (online && checkUpdates) checkAllPlaylists()
        }
        // 1분마다: 알람 확인, 온라인 확인, 캐시 크기
        scope.launch {
            while (isActive) {
                checkAlarm()
                refreshCacheInfo()
                delay(20_000)
                online = withContext(Dispatchers.IO) { Net.get("https://www.youtube.com/robots.txt") != null }
            }
        }
        // 이어듣기 위치 저장
        scope.launch {
            while (isActive) {
                delay(10_000)
                saveResume()
            }
        }
    }

    private fun withFav(list: List<Playlist>): List<Playlist> =
        if (list.any { it.id == FAV_ID }) list
        else listOf(Playlist(FAV_ID, "좋아요", "", emptyList())) + list

    private fun persist() {
        Store.save(prefs, playlists)
    }

    private fun updatePlaylists(list: List<Playlist>) {
        playlists = withFav(list)
        favUrls = playlists.first { it.id == FAV_ID }.tracks.map { it.url }.toSet()
        persist()
    }

    fun playlist(id: String?): Playlist? = playlists.firstOrNull { it.id == id }

    // ================= 재생 기록/색 =================
    private fun onTrackChanged(item: QItem?) {
        if (item == null) return
        val t = item.track
        recent = (listOf(RecentItem(item.pid, t)) + recent.filterNot { it.track.url == t.url }).take(20)
        Store.saveRecent(prefs, recent)
        extractColors(t.thumb)
        if (sleepEndOfTrack && lastTrackUrl.isNotEmpty() && lastTrackUrl != t.url) {
            sleepEndOfTrack = false
            engine.pause()
            toast("수면 타이머: 곡이 끝나서 멈췄어요")
        }
        lastTrackUrl = t.url
        if (showLyrics) loadLyrics()
        saveResume()
    }

    private var lastTrackUrl = ""

    private fun extractColors(url: String?) {
        if (url == null || url == colorFor) return
        colorFor = url
        scope.launch {
            val cols = withContext(Dispatchers.IO) { runCatching { paletteOf(url) }.getOrNull() }
            if (colorFor == url && cols != null) {
                artColors = cols
                AlbumAccent.update(cols)
                artColor = cols.firstOrNull()
            }
        }
    }

    /** 썸네일에서 대표색/어두운 색/밝은 색 뽑기 */
    private fun paletteOf(url: String): List<Color>? {
        val bytes = Net.getBytes(url) ?: return null
        val img = Image.makeFromEncoded(bytes)
        val bmp = Bitmap.makeFromImage(img)
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null
        var vr = 0.0; var vg = 0.0; var vb = 0.0; var vw = 0.0
        var dr = 0.0; var dg = 0.0; var db = 0.0; var dn = 0.0
        var ar = 0.0; var ag = 0.0; var ab = 0.0; var an = 0.0
        val step = maxOf(1, minOf(w, h) / 32)
        var y = h / 8
        while (y < h - h / 8) {
            var x = 0
            while (x < w) {
                val c = bmp.getColor(x, y)
                val r = (c shr 16 and 0xFF) / 255.0
                val g = (c shr 8 and 0xFF) / 255.0
                val b = (c and 0xFF) / 255.0
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                val s = if (mx == 0.0) 0.0 else (mx - mn) / mx
                if (s > 0.3 && mx in 0.25..0.97) {
                    val wgt = s * s
                    vr += r * wgt; vg += g * wgt; vb += b * wgt; vw += wgt
                }
                if (mx < 0.45) { dr += r; dg += g; db += b; dn += 1.0 }
                ar += r; ag += g; ab += b; an += 1.0
                x += step
            }
            y += step
        }
        if (an == 0.0) return null
        val avg = Color((ar / an).toFloat(), (ag / an).toFloat(), (ab / an).toFloat())
        val main = if (vw > 0) Color((vr / vw).toFloat(), (vg / vw).toFloat(), (vb / vw).toFloat()) else avg
        val deep = if (dn > 0) Color((dr / dn).toFloat(), (dg / dn).toFloat(), (db / dn).toFloat()) else main
        val soft = Color(
            ((main.red + 1f) / 2f).coerceAtMost(1f),
            ((main.green + 1f) / 2f).coerceAtMost(1f),
            ((main.blue + 1f) / 2f).coerceAtMost(1f)
        )
        return listOf(main, deep, soft)
    }

    val bgColors: List<Color>
        get() {
            val mono = ACCENTS.getOrNull(accent)?.second == MONO
            val gray = listOf(Color(0xFF8C8C8C), Color(0xFF3A3A3A), Color(0xFFCFCFCF))
            return when {
                useArtColor && artColors.isNotEmpty() -> artColors
                mono -> gray
                else -> emptyList()
            }
        }

    // ================= 이어듣기 =================
    private fun saveResume() {
        val item = engine.current ?: return
        prefs.edit()
            .putString("resume_pid", item.pid)
            .putString("resume_url", item.track.url)
            .putLong("resume_pos", engine.livePosition())
            .apply()
    }

    private fun restoreSession() {
        val pid = prefs.getString("resume_pid", null) ?: return
        val url = prefs.getString("resume_url", null)
        val pos = prefs.getLong("resume_pos", 0L)
        val pl = playlists.firstOrNull { it.id == pid } ?: return
        if (pl.tracks.isEmpty()) return
        val idx = pl.tracks.indexOfFirst { it.url == url }
        engine.setQueue(pl.tracks.map { QItem(pl.id, it) }, idx.coerceAtLeast(0), if (idx >= 0) pos else 0L, play = false)
    }

    // ================= 오류 =================
    private fun showError(msg: String, r: (() -> Unit)? = null) {
        error = msg
        retry = r
    }

    fun clearError() {
        error = null
        retry = null
    }

    // ================= 플레이리스트 관리 =================
    fun isFavorite(url: String): Boolean = url.isNotEmpty() && url in favUrls

    fun handleText(text: String) {
        val u = extractUrl(text)
        if (u != null && listIdOf(u) != null) addPlaylist(u)
        else toast("유튜브 플레이리스트 링크만 추가할 수 있어요")
    }

    fun addPlaylist(raw: String) {
        val found = extractUrl(raw) ?: raw.trim()
        if (found.isEmpty()) return
        val lid = listIdOf(found)
        // 영상 하나의 링크면 (폰에서 넘긴 '이어 듣기' 링크 포함) 그 위치부터 바로 재생
        if (lid == null && videoIdOf(found) != null) { openVideo(found); return }
        val u = if (lid != null) "https://www.youtube.com/playlist?list=$lid" else found
        playlists.firstOrNull { it.url.isNotBlank() && (it.url == u || (lid != null && listIdOf(it.url) == lid)) }?.let {
            openId = it.id
            toast("이미 추가된 플레이리스트예요")
            return
        }
        loading = true
        clearError()
        scope.launch {
            try {
                val (name, list) = withContext(Dispatchers.IO) { YtRepo.loadPlaylist(u) }
                val p = Playlist(Store.newId(), name.ifBlank { "플레이리스트 ${playlists.size}" }, u, list)
                updatePlaylists(playlists + p)
                toast("'${p.name}'을(를) 추가했어요")
            } catch (e: Exception) {
                showError("추가하지 못했어요. " + friendlyError(e)) { addPlaylist(u) }
            }
            loading = false
        }
    }

    fun createLocalPlaylist(name: String, first: Track? = null): String {
        val p = Playlist(Store.newId(), name.trim().ifBlank { "새 플레이리스트" }, "", listOfNotNull(first))
        updatePlaylists(playlists + p)
        return p.id
    }

    fun refreshPlaylist(id: String, quiet: Boolean = false) {
        val p = playlist(id) ?: return
        if (p.url.isBlank()) return
        loading = true
        if (!quiet) clearError()
        scope.launch {
            try {
                val (_, list) = withContext(Dispatchers.IO) { YtRepo.loadPlaylist(p.url) }
                updatePlaylists(playlists.map { if (it.id == id) it.copy(tracks = merged(it, list)) else it })
                if (!quiet) toast("목록을 새로 불러왔어요")
            } catch (e: Exception) {
                if (!(quiet && p.tracks.isNotEmpty())) showError(friendlyError(e)) { refreshPlaylist(id) }
            }
            loading = false
        }
    }

    private suspend fun checkAllPlaylists() {
        val msgs = mutableListOf<String>()
        val added = mutableSetOf<String>()
        for (p in playlists.filter { it.url.isNotBlank() }) {
            try {
                val (_, list) = withContext(Dispatchers.IO) { YtRepo.loadPlaylist(p.url) }
                val old = playlist(p.id)?.tracks?.map { it.url }?.toSet() ?: continue
                val fresh = list.filter { it.url !in old }
                if (old.isNotEmpty() && fresh.isNotEmpty()) {
                    msgs += "'${p.name}' ${fresh.size}곡"
                    added += fresh.map { it.url }
                }
                updatePlaylists(playlists.map { if (it.id == p.id) it.copy(tracks = merged(it, list)) else it })
            } catch (e: Exception) {
            }
        }
        if (added.isNotEmpty()) {
            newUrls = newUrls + added
            toast("새 곡이 추가됐어요: " + msgs.joinToString(", "))
        }
    }

    fun renamePlaylist(id: String, name: String) {
        updatePlaylists(playlists.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })
    }

    fun deletePlaylist(id: String) {
        if (id == FAV_ID) return
        cancelOffline(id)
        if (openId == id) openId = null
        updatePlaylists(playlists.filterNot { it.id == id })
    }

    fun movePlaylistToFront(id: String) {
        val p = playlist(id) ?: return
        if (id == FAV_ID) return
        val others = playlists.filterNot { it.id == id || it.id == FAV_ID }
        updatePlaylists(listOf(playlists.first { it.id == FAV_ID }, p) + others)
        toast("'${p.name}'을(를) 맨 앞으로 옮겼어요")
    }

    fun updateTag(id: String, tag: String) {
        updatePlaylists(playlists.map { if (it.id == id) it.copy(tag = tag.trim()) else it })
    }

    private fun findTrack(url: String): Track? {
        for (p in playlists) p.tracks.firstOrNull { it.url == url }?.let { return it }
        return null
    }

    fun toggleFavorite(t: Track) {
        val fav = playlists.first { it.id == FAV_ID }
        val on = fav.tracks.any { it.url == t.url }
        val newTracks = if (on) fav.tracks.filterNot { it.url == t.url } else listOf(t) + fav.tracks
        updatePlaylists(playlists.map { if (it.id == FAV_ID) it.copy(tracks = newTracks) else it })
        toast(if (on) "좋아요를 취소했어요" else "좋아요에 추가했어요")
    }

    fun toggleFavoriteCurrent() {
        engine.current?.track?.let { toggleFavorite(findTrack(it.url) ?: it) }
    }

    fun copyTo(t: Track, targetId: String) {
        val target = playlist(targetId) ?: return
        if (target.tracks.any { it.url == t.url }) {
            toast("'${target.name}'에 이미 있는 곡이에요")
            return
        }
        updatePlaylists(playlists.map {
            if (it.id != targetId) it
            else if (it.url.isBlank()) it.copy(tracks = it.tracks + t)
            else it.copy(tracks = it.tracks + t, added = (it.added + t).distinctBy { a -> a.url }, hidden = it.hidden - t.url)
        })
        toast("'${target.name}'에 추가했어요")
    }

    fun removeFromPlaylist(pid: String, index: Int) {
        updatePlaylists(playlists.map {
            if (it.id == pid && index in it.tracks.indices) {
                val gone = it.tracks[index].url
                val rest = it.tracks.filterIndexed { i, _ -> i != index }
                if (it.url.isBlank()) it.copy(tracks = rest)
                else it.copy(tracks = rest, added = it.added.filter { a -> a.url != gone }, hidden = it.hidden + gone)
            } else it
        })
    }

    // ================= 재생 =================
    fun playIn(pid: String, index: Int) {
        radioMode = false
        val pl = playlist(pid) ?: return
        if (index !in pl.tracks.indices) return
        engine.setQueue(pl.tracks.map { QItem(pl.id, it) }, index)
    }

    fun playAllIn(pid: String) {
        engine.setShuffleMode(false)
        playIn(pid, 0)
    }

    fun shuffleIn(pid: String) {
        val pl = playlist(pid) ?: return
        if (pl.tracks.isEmpty()) return
        engine.setShuffleMode(true)
        playIn(pid, pl.tracks.indices.random())
    }

    fun playList(list: List<Track>, pid: String, index: Int) {
        radioMode = false
        engine.setQueue(list.map { QItem(pid, it) }, index)
    }

    fun playNext(t: Track, pid: String) {
        engine.playNextItem(QItem(pid, t))
        toast("다음 곡으로 넣었어요")
    }

    fun addToQueue(t: Track, pid: String) {
        engine.addToQueue(QItem(pid, t))
        toast("대기열 맨 뒤에 추가했어요")
    }

    fun playTrackIn(pid: String, url: String) {
        val pl = playlist(pid)
        val idx = pl?.tracks?.indexOfFirst { it.url == url } ?: -1
        if (pl != null && idx >= 0) playIn(pid, idx)
        else {
            val t = recent.firstOrNull { it.track.url == url }?.track ?: findTrack(url) ?: return
            engine.setQueue(listOf(QItem(pid, t)), 0)
        }
    }

    fun playMix(ids: Collection<String>) {
        radioMode = false
        val items = playlists.filter { it.id in ids }
            .flatMap { p -> p.tracks.map { QItem(p.id, it) } }
            .distinctBy { it.track.url }
            .shuffled()
        if (items.isEmpty()) return
        engine.setShuffleMode(false)
        engine.setQueue(items, 0)
        toast("${ids.size}개 플레이리스트, ${items.size}곡을 섞어서 재생해요")
    }

    fun playTopTracks() {
        val top = Stats.snapshot(prefs).tracks.filter { it.count > 0 }
            .sortedWith(compareByDescending<Stats.TrackStat> { it.count }.thenByDescending { it.ms })
            .take(25)
        if (top.isEmpty()) {
            toast("아직 들은 기록이 없어요")
            return
        }
        engine.setShuffleMode(false)
        engine.setQueue(top.map { QItem(it.pid, Track(it.url, it.title, it.artist, it.thumb, 0)) }, 0)
        toast("많이 들은 곡 ${top.size}개를 재생해요")
    }

    fun abStep() {
        toast(AbLoop.step(playingUrl, engine.livePosition()))
    }

    // ================= 가사 =================
    fun loadLyrics() {
        val item = engine.current ?: return
        val url = item.track.url
        if (url == lyricsUrl) return
        lyricsUrl = url
        lyrics = LyricsState.Loading
        val t = item.track
        scope.launch {
            val r = withContext(Dispatchers.IO) { Lyrics.fetch(url, t.title, t.artist, t.durationSec) }
            if (lyricsUrl == url) lyrics = r
        }
    }

    // ================= 검색 =================
    fun search(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        lastQuery = query
        searching = true
        searchError = null
        panel = Panel.SEARCH
        scope.launch {
            try {
                searchResults = withContext(Dispatchers.IO) { YtRepo.search(query) }
                if (searchResults.isEmpty()) searchError = "검색 결과가 없어요"
            } catch (e: Exception) {
                searchError = friendlyError(e)
            }
            searching = false
        }
    }

    fun findArtist(artist: String) {
        val a = artist.replace(Regex("(?i)\\s*-\\s*topic$|vevo$"), "").trim()
        if (a.isNotEmpty()) search(a)
    }

    // ================= 추천 / 통계 =================
    data class Pick(val pid: String, val track: Track, val reason: String)

    fun todayPick(): Pick? {
        val snap = Stats.snapshot(prefs)
        val now = System.currentTimeMillis()
        val rnd = Random(now / 86_400_000L)
        val pool = playlists.flatMap { p -> p.tracks.map { p.id to it } }.distinctBy { it.second.url }
        if (pool.isEmpty()) return null
        val stat = snap.tracks.associateBy { it.url }
        val weekAgo = now - 7L * 86_400_000L
        val forgotten = pool.filter { (_, t) -> stat[t.url]?.let { it.last < weekAgo } == true }
        val loved = snap.tracks.sortedByDescending { it.count }.take(10)
            .mapNotNull { s -> pool.firstOrNull { it.second.url == s.url } }
        val choice = when (rnd.nextInt(3)) {
            0 -> forgotten.randomOrNull(rnd)?.let { Pick(it.first, it.second, "한동안 안 들은 곡") }
            1 -> loved.randomOrNull(rnd)?.let { Pick(it.first, it.second, "요즘 자주 듣는 곡") }
            else -> null
        }
        return choice ?: pool.random(rnd).let { Pick(it.first, it.second, "오늘의 랜덤 곡") }
    }

    fun statsSnapshot(): Stats.Snapshot = Stats.snapshot(prefs)

    fun clearStats() {
        Stats.clear(prefs)
        toast("청취 기록을 지웠어요")
    }

    fun clearRecent() {
        recent = emptyList()
        Store.saveRecent(prefs, recent)
    }

    // ================= 오프라인 저장 / 캐시 =================
    fun startOffline(id: String) {
        val p = playlist(id) ?: return
        if (offlineProgress.containsKey(id) || p.tracks.isEmpty()) return
        val low = lowQuality
        offlineProgress[id] = 0 to p.tracks.size
        toast("'${p.name}' 오프라인 저장을 시작해요")
        offlineJobs[id] = scope.launch {
            var done = 0
            var bad = 0
            for (t in p.tracks) {
                if (!isActive) break
                val ok = withContext(Dispatchers.IO) { runCatching { AudioCache.ensure(t, low, pinned = true) }.isSuccess }
                if (!ok) bad++
                done++
                offlineProgress[id] = done to p.tracks.size
            }
            offlineProgress.remove(id)
            offlineJobs.remove(id)
            cacheVersion++
            refreshCacheInfo()
            toast(if (bad == 0) "'${p.name}' 저장 완료" else "'${p.name}' 저장 완료 (${bad}곡 실패)")
        }
    }

    fun cancelOffline(id: String) {
        offlineJobs.remove(id)?.cancel()
        offlineProgress.remove(id)
    }

    fun removeOffline(id: String) {
        val p = playlist(id) ?: return
        cancelOffline(id)
        AudioCache.removeOffline(p.tracks.map { it.url })
        cacheVersion++
        refreshCacheInfo()
        toast("저장한 곡을 지웠어요")
    }

    fun refreshCacheInfo() {
        scope.launch {
            val (a, b) = withContext(Dispatchers.IO) { AudioCache.sizeOf(AudioCache.cacheDir) to AudioCache.sizeOf(AudioCache.offlineDir) }
            cacheBytes = a
            offlineBytes = b
        }
    }

    fun clearCache() {
        scope.launch {
            withContext(Dispatchers.IO) { AudioCache.clearCache() }
            cacheVersion++
            refreshCacheInfo()
            toast("자동 캐시를 비웠어요")
        }
    }

    fun clearOffline() {
        offlineJobs.keys.toList().forEach { cancelOffline(it) }
        scope.launch {
            withContext(Dispatchers.IO) { AudioCache.clearOffline() }
            cacheVersion++
            refreshCacheInfo()
            toast("오프라인 저장 곡을 모두 지웠어요")
        }
    }

    // ================= 백업 =================
    fun exportTo(file: File) {
        val text = Store.exportJson(playlists)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { file.writeText(text, Charsets.UTF_8) }
                toast("백업 파일을 저장했어요")
            } catch (e: Exception) {
                toast("백업하지 못했어요")
            }
        }
    }

    fun importFrom(file: File) {
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) }
                val incoming = Store.parseExport(text)
                var list = playlists
                var added = 0
                var merged = 0
                for (p in incoming) {
                    if (p.id == FAV_ID) {
                        val fav = list.first { it.id == FAV_ID }
                        val merged = fav.tracks + p.tracks.filter { t -> fav.tracks.none { it.url == t.url } }
                        list = list.map { if (it.id == FAV_ID) it.copy(tracks = merged) else it }
                        continue
                    }
                    // 같은 목록(같은 유튜브 목록, 또는 같은 이름의 직접 만든 목록)이 있으면 합치기
                    val lid = listIdOf(p.url)
                    val same = list.firstOrNull {
                        it.id != FAV_ID && ((lid != null && listIdOf(it.url) == lid) || (p.url.isBlank() && it.url.isBlank() && it.name == p.name))
                    }
                    if (same != null) {
                        val have = same.tracks.map { it.url }.toSet()
                        val extra = p.tracks.filter { it.url !in have && it.url !in same.hidden }
                        list = list.map {
                            if (it.id != same.id) it
                            else it.copy(
                                tracks = it.tracks + extra,
                                added = (it.added + p.added).distinctBy { a -> a.url },
                                hidden = it.hidden + p.hidden
                            )
                        }
                        if (extra.isNotEmpty()) merged++
                        continue
                    }
                    list = list + p.copy(id = Store.newId())
                    added++
                }
                updatePlaylists(list)
                toast("새 플레이리스트 ${added}개, 곡이 더해진 목록 ${merged}개")
            } catch (e: Exception) {
                toast("백업 파일을 읽지 못했어요")
            }
        }
    }

    // ================= 수면 타이머 =================
    fun startSleepMinutes(min: Int) {
        cancelSleep()
        sleepRemainingMs = min * 60_000L
        sleepJob = scope.launch {
            while (sleepRemainingMs > 0) {
                delay(1000)
                sleepRemainingMs -= 1000
            }
            sleepRemainingMs = 0
            // 3초에 걸쳐 서서히 줄이고 멈춤
            for (i in 15 downTo 0) {
                engine.sleepFactor = i / 15f
                engine.applyVolume()
                delay(200)
            }
            engine.pause()
            delay(300)
            engine.sleepFactor = 1f
            engine.applyVolume(force = true)
            toast("수면 타이머로 재생을 멈췄어요")
        }
    }

    fun startSleepEndOfTrack() {
        cancelSleep()
        sleepEndOfTrack = true
        lastTrackUrl = playingUrl
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        sleepJob = null
        sleepRemainingMs = 0
        sleepEndOfTrack = false
        engine.sleepFactor = 1f
    }

    // ================= 알람 (앱이 켜져 있을 때) =================
    private var lastAlarmDay = ""

    private fun checkAlarm() {
        if (!alarmOn) return
        val c = Calendar.getInstance()
        val today = Stats.dayKey(System.currentTimeMillis())
        if (today == lastAlarmDay) return
        if (c.get(Calendar.HOUR_OF_DAY) == alarmHour && c.get(Calendar.MINUTE) == alarmMinute) {
            lastAlarmDay = today
            val pl = playlists.firstOrNull { it.id == alarmPid && it.tracks.isNotEmpty() }
                ?: playlists.firstOrNull { it.tracks.isNotEmpty() } ?: return
            engine.setShuffleMode(true)
            engine.alarmRampStart = nowMs()
            engine.setQueue(pl.tracks.map { QItem(pl.id, it) }, pl.tracks.indices.random())
            showWindowRequest++
            toast("알람: '${if (pl.id == FAV_ID) "좋아요" else pl.name}'을(를) 틀어요")
        }
    }

    fun updateAlarm(on: Boolean = alarmOn, h: Int = alarmHour, m: Int = alarmMinute, pid: String = alarmPid) {
        alarmOn = on
        alarmHour = (h + 24) % 24
        alarmMinute = (m + 60) % 60
        alarmPid = pid
        lastAlarmDay = ""
        prefs.edit().putBoolean("alarm_on", on).putInt("alarm_h", alarmHour).putInt("alarm_m", alarmMinute)
            .putString("alarm_pid", pid).apply()
    }

    // ================= 설정 =================
    private fun putBool(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()

    fun updateThemeMode(v: Int) { themeMode = v; prefs.edit().putInt("theme_mode", v).apply() }
    fun updateNight(start: Int, end: Int) {
        nightStart = (start + 24) % 24
        nightEnd = (end + 24) % 24
        prefs.edit().putInt("night_start", nightStart).putInt("night_end", nightEnd).apply()
    }
    fun updateAccent(v: Int) { accent = v; prefs.edit().putInt("accent", v).apply() }
    fun updateGlass(v: Boolean) { glass = v; putBool("glass", v) }
    fun updateArtColor(v: Boolean) { useArtColor = v; putBool("art_color", v) }
    fun updateFontScale(v: Float) { fontScale = v; prefs.edit().putFloat("font_scale", v).apply() }
    fun updateResume(v: Boolean) { resumeEnabled = v; putBool("resume", v) }
    fun updatePrefetch(v: Boolean) { prefetch = v; putBool("prefetch", v) }
    fun updateLowQuality(v: Boolean) { lowQuality = v; putBool("low_quality", v) }
    fun updateSkipOfftopic(v: Boolean) { skipOfftopic = v; putBool("skip_offtopic", v) }
    fun updateLongResume(v: Boolean) { longResume = v; putBool("long_resume", v) }
    fun updateCheckUpdates(v: Boolean) { checkUpdates = v; putBool("check_updates", v) }
    fun updateCloseToTray(v: Boolean) { closeToTray = v; putBool("close_to_tray", v) }
    fun updateFadeMs(v: Int) { fadeMs = v; prefs.edit().putInt("fade_ms", v).apply() }
    fun updateCacheMb(v: Int) {
        cacheMb = v
        AudioCache.limitBytes = v * 1024L * 1024L
        prefs.edit().putInt("cache_mb", v).apply()
    }
    fun finishOnboarding() { onboarded = true; putBool("onboarded_pc", true) }
    fun showOnboardingAgain() { onboarded = false; putBool("onboarded_pc", false) }

    // ================= 유튜브 목록 + 내가 넣고 뺀 곡 =================
    /** 유튜브 원본 + 내가 넣은 곡 - 내가 뺀 곡 */
    private fun merged(p: Playlist, list: List<Track>): List<Track> {
        val urls = list.map { it.url }.toSet()
        return list.filter { it.url !in p.hidden } + p.added.filter { it.url !in urls }
    }

    fun duplicateCount(id: String): Int {
        val p = playlist(id) ?: return 0
        return p.tracks.size - p.tracks.distinctBy { it.url }.size
    }

    fun removeDuplicates(id: String) {
        val n = duplicateCount(id)
        if (n == 0) { toast("중복된 곡이 없어요"); return }
        updatePlaylists(playlists.map { if (it.id == id) it.copy(tracks = it.tracks.distinctBy { t -> t.url }) else it })
        toast("중복된 곡 ${n}개를 정리했어요")
    }

    // ================= 비슷한 곡: 추천 / 라디오 / 이어 재생 =================
    var autoplayRelated by mutableStateOf(prefs.getBoolean("autoplay_related", true))
        private set
    var cleanTitles by mutableStateOf(prefs.getBoolean("clean_titles", true).also { TitleClean.on = it })
        private set
    fun updateAutoplayRelated(v: Boolean) { autoplayRelated = v; putBool("autoplay_related", v) }
    fun updateCleanTitles(v: Boolean) { cleanTitles = v; TitleClean.on = v; putBool("clean_titles", v) }

    var recs by mutableStateOf<List<Track>>(emptyList())
        private set
    var recsLoading by mutableStateOf(false)
        private set
    private var recsFor = ""

    fun loadRecs(force: Boolean = false) {
        val seed = engine.current?.track?.url ?: recent.firstOrNull()?.track?.url ?: return
        if (!online || recsLoading) return
        if (!force && seed == recsFor && recs.isNotEmpty()) return
        recsFor = seed
        recsLoading = true
        scope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { YtRepo.related(seed) }.getOrNull() }
            if (list != null) {
                val mine = playlists.flatMap { it.tracks }.map { it.url }.toSet()
                recs = (list.filter { it.url !in mine } + list.filter { it.url in mine })
                    .distinctBy { it.url }.filter { it.url != seed }.take(16)
            }
            recsLoading = false
        }
    }

    private var radioMode = false
    private var extending = false

    /** 영상 링크를 그 위치(t=)부터 재생 */
    fun openVideo(link: String) {
        val id = videoIdOf(link) ?: return
        val startMs = startTimeOf(link)
        toast("링크의 곡을 불러오는 중…")
        scope.launch {
            val t = withContext(Dispatchers.IO) { runCatching { YtRepo.trackOf("https://www.youtube.com/watch?v=$id") }.getOrNull() }
            if (t == null) { toast("영상을 불러오지 못했어요"); return@launch }
            radioMode = false
            engine.setQueue(listOf(QItem(SEARCH_PID, t)), 0, startMs)
            if (startMs > 0) toast("${fmtTime(startMs)}부터 이어서 들어요")
        }
    }

    /** 지금 곡을 지금 위치부터 여는 링크 (폰에서 이어 듣기) */
    fun handoffLink(): String? {
        val id = videoIdOf(engine.current?.track?.url ?: return null) ?: return null
        return "https://www.youtube.com/watch?v=$id&t=${engine.livePosition() / 1000}s"
    }

    fun copyHandoff() {
        val l = handoffLink() ?: run { toast("재생 중인 곡이 없어요"); return }
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(l), null)
        }
        toast("이어 듣기 링크를 복사했어요. 폰의 도구 모음 → PC와 맞추기 → '붙여 넣어 열기'")
    }

    fun startRadio(t: Track) {
        radioMode = true
        engine.setShuffleMode(false)
        engine.setQueue(listOf(QItem(RADIO_PID, t)), 0)
        toast("'${TitleClean.title(t.title, t.artist).take(24)}' 라디오를 시작해요")
    }

    /** 대기열이 끝나면 마지막 곡과 비슷한 곡을 찾아 이어서 재생 */
    private fun extendQueue(last: QItem?) {
        if (last == null || extending) return
        if (!autoplayRelated && !radioMode) return
        extending = true
        scope.launch {
            val have = engine.queue.map { it.track.url }.toSet()
            val more = withContext(Dispatchers.IO) { runCatching { YtRepo.related(last.track.url) }.getOrNull() }
                ?.filter { it.url !in have }?.take(12).orEmpty()
            if (more.isNotEmpty()) {
                engine.appendItems(more.map { QItem(RADIO_PID, it) }, playFirst = true)
                toast("비슷한 곡 ${more.size}개를 이어서 틀어요")
            }
            extending = false
        }
    }

    init {
        engine.onQueueEnd = { extendQueue(it) }
    }

    fun shutdown() {
        saveResume()
        Stats.save(prefs)
        engine.stopAll()
        prefs.flush()
    }
}
