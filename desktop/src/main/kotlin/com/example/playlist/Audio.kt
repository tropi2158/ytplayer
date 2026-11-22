package com.example.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.mp4parser.muxer.builder.DefaultMp4Builder
import org.mp4parser.muxer.container.mp4.MovieCreator
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

fun nowMs(): Long = System.nanoTime() / 1_000_000L

/** JavaFX 오디오 엔진 시작 (창은 띄우지 않음) */
object Fx {
    @Volatile private var started = false

    fun init() {
        if (started) return
        started = true
        try {
            Platform.startup {}
        } catch (e: IllegalStateException) {
            // 이미 시작됨
        }
        Platform.setImplicitExit(false)
    }

    fun run(block: () -> Unit) = Platform.runLater(block)

    fun exit() = runCatching { Platform.exit() }
}

/**
 * 오디오 파일 저장소
 * - cache: 들은 곡 (용량 넘으면 오래된 것부터 삭제)
 * - offline: 직접 저장한 곡 (지워지지 않음)
 */
object AudioCache {
    private val root by lazy { File(AppDirs.local, "audio").apply { mkdirs() } }
    val cacheDir by lazy { File(root, "cache").apply { mkdirs() } }
    val offlineDir by lazy { File(root, "offline").apply { mkdirs() } }
    private val locks = ConcurrentHashMap<String, Any>()
    @Volatile var limitBytes: Long = 1024L * 1024 * 1024
    private const val CHUNK = 2L * 1024 * 1024

    fun key(url: String): String = videoIdOf(url) ?: Integer.toHexString(url.hashCode())

    private fun offFile(url: String) = File(offlineDir, key(url) + ".m4a")
    private fun cacheFile(url: String) = File(cacheDir, key(url) + ".m4a")

    fun isPinned(url: String) = offFile(url).length() > 0
    fun isCached(url: String) = cacheFile(url).length() > 0
    fun has(url: String) = isPinned(url) || isCached(url)

    fun sizeOf(dir: File): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** 재생 가능한 로컬 파일을 돌려줘요 (없으면 받아요). IO 스레드에서 호출 */
    fun ensure(t: Track, low: Boolean, pinned: Boolean = false): File {
        val lock = locks.getOrPut(key(t.url)) { Any() }
        synchronized(lock) {
            val off = offFile(t.url)
            val c = cacheFile(t.url)
            if (off.length() > 0) return off
            if (c.length() > 0) {
                if (pinned) {
                    c.copyTo(off, overwrite = true)
                    c.delete()
                    return off
                }
                c.setLastModified(System.currentTimeMillis())
                return c
            }
            val target = if (pinned) off else c
            val stream = YtRepo.resolveAudioUrl(t.url, low)
            val tmp = File(target.parentFile, target.name + ".part")
            download(stream, tmp)
            remux(tmp, target)
            if (!pinned) trim()
            return target
        }
    }

    private fun download(url: String, out: File) {
        out.delete()
        var start = 0L
        FileOutputStream(out).use { fos ->
            while (true) {
                val req = Request.Builder().url(url)
                    .header("User-Agent", NewPipeDownloader.USER_AGENT)
                    .header("Range", "bytes=$start-${start + CHUNK - 1}")
                    .build()
                Net.client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("다운로드 실패 (${r.code})")
                    val body = r.body ?: throw IOException("빈 응답")
                    val bytes = body.byteStream().use { it.copyTo(fos) }
                    if (r.code == 200) return
                    val total = r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                    start += bytes
                    if (bytes == 0L || (total in 1..start)) return
                }
            }
        }
    }

    /** 조각난 mp4를 일반 m4a로 다시 묶기 (실패하면 원본 그대로 사용) */
    private fun remux(src: File, dst: File) {
        try {
            val movie = MovieCreator.build(src.absolutePath)
            val container = DefaultMp4Builder().build(movie)
            val tmp = File(dst.parentFile, dst.name + ".mux")
            FileOutputStream(tmp).use { container.writeContainer(it.channel) }
            if (tmp.length() > 0) {
                dst.delete()
                tmp.renameTo(dst)
                src.delete()
                return
            }
            tmp.delete()
        } catch (e: Throwable) {
            // 무시하고 원본 사용
        }
        dst.delete()
        src.renameTo(dst)
    }

    private fun trim() {
        val files = cacheDir.listFiles { f -> f.name.endsWith(".m4a") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= limitBytes) break
            total -= f.length()
            f.delete()
        }
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun clearOffline() {
        offlineDir.listFiles()?.forEach { it.delete() }
    }

    fun removeOffline(urls: Collection<String>) {
        urls.forEach { offFile(it).delete() }
    }
}

/** 대기열 한 곡 (pid = 어느 플레이리스트에서 왔는지) */
data class QItem(val pid: String, val track: Track)

/**
 * 재생 엔진: 대기열, 셔플/반복, JavaFX 재생, 페이드, 음악 아닌 구간 건너뛰기, A-B 반복, 긴 곡 이어듣기, 통계
 * 모든 상태 변경은 메인(UI) 스레드에서 해요.
 */
class Engine(
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val onEvent: (String) -> Unit
) {
    var queue by mutableStateOf<List<QItem>>(emptyList())
        private set
    var order by mutableStateOf<List<Int>>(emptyList())
        private set
    var orderPos by mutableIntStateOf(-1)
        private set
    val currentIndex: Int get() = order.getOrNull(orderPos) ?: -1
    val current: QItem? get() = queue.getOrNull(currentIndex)

    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var positionAt by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(prefs.getBoolean("shuffle", false))
        private set
    /** 0 반복 끔, 1 전체 반복, 2 한 곡 반복 */
    var repeatMode by mutableIntStateOf(prefs.getInt("repeat", 1))
        private set
    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1f))
        private set
    var volume by mutableFloatStateOf(prefs.getFloat("volume", 0.8f))
        private set
    var muted by mutableStateOf(false)
        private set
    var eqOn by mutableStateOf(prefs.getBoolean("eq_on", false))
        private set
    val eqGains = mutableStateListOf<Float>().apply {
        val saved = (prefs.getString("eq_gains", "") ?: "").split(",").mapNotNull { it.toFloatOrNull() }
        addAll(if (saved.size == 10) saved else List(10) { 0f })
    }

    /** 수면 타이머가 줄이는 볼륨 배율 */
    var sleepFactor = 1f
    /** 알람 볼륨 서서히 키우기 시작 시각 */
    var alarmRampStart = 0L

    var onTrackChanged: ((QItem?) -> Unit)? = null

    @Volatile private var mp: MediaPlayer? = null
    @Volatile private var rawPos = 0L
    private var loadJob: Job? = null
    private var errorStreak = 0
    private var appliedVolume = -1f
    private var segments: List<LongRange> = emptyList()
    private var segFor = ""
    private var listened = 0L
    private var counted = false
    private var lastTick = 0L
    private var lastLongSave = 0L
    private var lastStatsSave = 0L

    init {
        scope.launch {
            while (true) {
                delay(100)
                runCatching { tick() }
            }
        }
    }

    // ---------------- 위치 ----------------
    fun livePosition(): Long {
        val p = if (isPlaying) positionMs + ((nowMs() - positionAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0L, durationMs) else p.coerceAtLeast(0L)
    }

    private fun setPos(ms: Long) {
        positionMs = ms
        positionAt = nowMs()
        rawPos = ms
    }

    // ---------------- 대기열 ----------------
    private fun buildOrder(n: Int, first: Int): List<Int> =
        if (shuffle) listOf(first) + (0 until n).filter { it != first }.shuffled() else (0 until n).toList()

    fun setQueue(items: List<QItem>, start: Int, startPos: Long = 0L, play: Boolean = true) {
        if (items.isEmpty()) return
        val s = start.coerceIn(0, items.lastIndex)
        queue = items
        order = buildOrder(items.size, s)
        orderPos = order.indexOf(s).coerceAtLeast(0)
        load(startPos, play)
    }

    fun playAtQueueIndex(qi: Int) {
        val p = order.indexOf(qi)
        if (p < 0) return
        orderPos = p
        load(0L, true)
    }

    fun playNextItem(item: QItem) {
        if (queue.isEmpty()) {
            setQueue(listOf(item), 0)
            return
        }
        val ci = currentIndex
        val ins = ci + 1
        val q = queue.toMutableList().apply { add(ins, item) }
        val o = order.map { if (it >= ins) it + 1 else it }.toMutableList()
        o.add(orderPos + 1, ins)
        queue = q
        order = o
    }

    fun addToQueue(item: QItem) {
        if (queue.isEmpty()) {
            setQueue(listOf(item), 0)
            return
        }
        queue = queue + item
        order = order + queue.lastIndex
    }

    fun removeAt(qi: Int) {
        val ci = currentIndex
        if (qi == ci || qi !in queue.indices) return
        queue = queue.filterIndexed { i, _ -> i != qi }
        order = order.filter { it != qi }.map { if (it > qi) it - 1 else it }
        orderPos = order.indexOf(if (ci > qi) ci - 1 else ci)
    }

    fun move(from: Int, to: Int) {
        if (shuffle || from !in queue.indices || to !in queue.indices) return
        val cur = current
        val q = queue.toMutableList()
        val x = q.removeAt(from)
        q.add(to, x)
        queue = q
        order = q.indices.toList()
        orderPos = q.indexOfFirst { it === cur }.coerceAtLeast(0)
    }

    // ---------------- 재생 제어 ----------------
    fun togglePlay() {
        val p = mp
        if (p == null) {
            if (current != null) load(positionMs, true)
            return
        }
        if (isPlaying) Fx.run { p.pause() } else Fx.run { p.play() }
    }

    fun play() {
        if (!isPlaying) togglePlay()
    }

    fun pause() {
        val p = mp ?: return
        Fx.run { p.pause() }
    }

    fun next() {
        if (queue.isEmpty()) return
        if (orderPos + 1 < order.size) {
            orderPos++
        } else {
            if (shuffle) order = buildOrder(queue.size, order.firstOrNull() ?: 0)
            orderPos = 0
        }
        load(0L, true)
    }

    fun previous() {
        if (queue.isEmpty()) return
        if (livePosition() > 3000 || orderPos <= 0) {
            seek(0L)
            return
        }
        orderPos--
        load(0L, true)
    }

    fun seek(ms: Long) {
        val p = mp
        setPos(ms)
        if (p != null) Fx.run { p.seek(Duration.millis(ms.toDouble())) }
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        prefs.edit().putBoolean("shuffle", shuffle).apply()
        val ci = currentIndex
        if (queue.isEmpty() || ci < 0) return
        order = buildOrder(queue.size, ci)
        orderPos = order.indexOf(ci)
    }

    fun setShuffleMode(v: Boolean) {
        if (shuffle != v) toggleShuffle()
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            0 -> 1
            1 -> 2
            else -> 0
        }
        prefs.edit().putInt("repeat", repeatMode).apply()
    }

    fun updateSpeed(v: Float) {
        speed = v
        prefs.edit().putFloat("speed", v).apply()
        val p = mp ?: return
        Fx.run { p.rate = v.toDouble() }
        setPos(livePosition())
    }

    fun updateVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        if (volume > 0f) muted = false
        prefs.edit().putFloat("volume", volume).apply()
        applyVolume(force = true)
    }

    fun toggleMute() {
        muted = !muted
        applyVolume(force = true)
    }

    // ---------------- 이퀄라이저 (JavaFX 10밴드) ----------------
    val eqFreqs = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    fun updateEqOn(v: Boolean) {
        eqOn = v
        prefs.edit().putBoolean("eq_on", v).apply()
        mp?.let { p -> Fx.run { applyEq(p) } }
    }

    fun updateEqGain(band: Int, db: Float) {
        if (band !in eqGains.indices) return
        eqGains[band] = db.coerceIn(-12f, 12f)
        prefs.edit().putString("eq_gains", eqGains.joinToString(",")).apply()
        mp?.let { p -> Fx.run { applyEq(p) } }
    }

    fun setEqPreset(values: List<Float>) {
        values.forEachIndexed { i, v -> if (i in eqGains.indices) eqGains[i] = v }
        prefs.edit().putString("eq_gains", eqGains.joinToString(",")).apply()
        mp?.let { p -> Fx.run { applyEq(p) } }
    }

    private fun applyEq(p: MediaPlayer) {
        val eq = p.audioEqualizer ?: return
        eq.isEnabled = eqOn
        val bands = eq.bands
        for (i in 0 until min(bands.size, eqGains.size)) bands[i].gain = eqGains[i].toDouble()
    }

    // ---------------- 불러오기 ----------------
    private fun disposePlayer() {
        val old = mp ?: return
        mp = null
        Fx.run {
            runCatching { old.stop() }
            runCatching { old.dispose() }
        }
    }

    fun stopAll() {
        loadJob?.cancel()
        disposePlayer()
        isPlaying = false
    }

    private fun load(startPos: Long, play: Boolean) {
        val item = current ?: return
        loadJob?.cancel()
        disposePlayer()
        isLoading = true
        isPlaying = false
        durationMs = item.track.durationSec * 1000
        setPos(startPos)
        listened = 0L
        counted = false
        if (AbLoop.url.isNotEmpty() && AbLoop.url != item.track.url) AbLoop.clear()
        onTrackChanged?.invoke(item)
        loadSegments(item.track.url)
        loadJob = scope.launch {
            val file = try {
                withContext(Dispatchers.IO) { AudioCache.ensure(item.track, prefs.getBoolean("low_quality", false)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (current !== item) return@launch
            if (file == null) {
                isLoading = false
                onError("이 곡을 불러오지 못했어요 (${item.track.title.take(20)})")
                return@launch
            }
            var begin = startPos
            if (startPos == 0L && prefs.getBoolean("long_resume", true)) {
                val saved = LongPositions.get(prefs, item.track.url)
                if (saved > 30_000L) {
                    begin = saved
                    setPos(saved)
                    onEvent("지난번 들은 ${fmtTime(saved)}부터 이어서 재생해요")
                }
            }
            val b = begin
            Fx.run { createPlayer(file, b, play, item) }
        }
    }

    /** JavaFX 스레드에서 실행 */
    private fun createPlayer(file: File, begin: Long, play: Boolean, item: QItem) {
        try {
            val media = Media(file.toURI().toString())
            val p = MediaPlayer(media)
            p.rate = speed.toDouble()
            p.volume = targetVolume().toDouble()
            applyEq(p)
            p.currentTimeProperty().addListener { _, _, t -> rawPos = t.toMillis().toLong() }
            p.setOnReady {
                val d = media.duration.toMillis()
                if (begin > 0) p.seek(Duration.millis(begin.toDouble()))
                if (play) p.play()
                ui {
                    if (!d.isNaN() && d > 0) durationMs = d.toLong()
                    isLoading = false
                    errorStreak = 0
                    prefetchNext()
                }
            }
            p.setOnPlaying { ui { isPlaying = true; setPos(rawPos) } }
            p.setOnPaused { ui { isPlaying = false; setPos(rawPos) } }
            p.setOnStopped { ui { isPlaying = false } }
            p.setOnEndOfMedia { ui { if (mp === p) onEnded() } }
            p.setOnError { ui { if (mp === p) onError("재생 오류: ${p.error?.message ?: "알 수 없음"}") } }
            media.setOnError { ui { if (mp === p) onError("파일 오류: ${media.error?.message ?: "알 수 없음"}") } }
            ui {
                if (current === item) {
                    mp = p
                    appliedVolume = -1f
                } else {
                    Fx.run { p.dispose() }
                }
            }
        } catch (e: Exception) {
            ui {
                isLoading = false
                onError("재생할 수 없는 파일이에요")
            }
        }
    }

    private fun ui(block: () -> Unit) {
        scope.launch { block() }
    }

    private fun onEnded() {
        val item = current
        if (item != null) LongPositions.put(prefs, item.track.url, 0L)
        if (repeatMode == 2) {
            seek(0L)
            mp?.let { p -> Fx.run { p.play() } }
            return
        }
        if (orderPos + 1 < order.size) {
            orderPos++
            load(0L, true)
        } else if (repeatMode == 1) {
            if (shuffle) order = buildOrder(queue.size, order.firstOrNull() ?: 0)
            orderPos = 0
            load(0L, true)
        } else {
            isPlaying = false
            seek(0L)
        }
    }

    private fun onError(msg: String) {
        errorStreak++
        isLoading = false
        onEvent(msg)
        if (errorStreak <= 5 && orderPos + 1 < order.size) {
            orderPos++
            load(0L, true)
        } else {
            isPlaying = false
        }
    }

    private fun prefetchNext() {
        if (!prefs.getBoolean("prefetch", true)) return
        val nextIdx = order.getOrNull(orderPos + 1) ?: return
        val t = queue.getOrNull(nextIdx)?.track ?: return
        if (AudioCache.has(t.url)) return
        val low = prefs.getBoolean("low_quality", false)
        scope.launch(Dispatchers.IO) { runCatching { AudioCache.ensure(t, low) } }
    }

    private fun loadSegments(url: String) {
        val id = videoIdOf(url)
        if (id == null) {
            segments = emptyList()
            segFor = ""
            return
        }
        if (segFor == id) return
        segFor = id
        segments = emptyList()
        if (!prefs.getBoolean("skip_offtopic", true)) return
        scope.launch {
            val s = withContext(Dispatchers.IO) { SponsorBlock.fetch(id) }
            if (segFor == id) segments = s
        }
    }

    // ---------------- 볼륨 ----------------
    private var fadeFactor = 1f

    private fun targetVolume(): Float {
        if (muted) return 0f
        var v = volume * fadeFactor * sleepFactor
        if (alarmRampStart > 0L) {
            val r = (nowMs() - alarmRampStart) / 40_000f
            if (r >= 1f) alarmRampStart = 0L else v *= (0.08f + 0.92f * r)
        }
        return v.coerceIn(0f, 1f)
    }

    fun applyVolume(force: Boolean = false) {
        val p = mp ?: return
        val v = targetVolume()
        if (force || abs(v - appliedVolume) > 0.005f) {
            appliedVolume = v
            Fx.run { p.volume = v.toDouble() }
        }
    }

    // ---------------- 0.1초 타이머 ----------------
    private fun tick() {
        val now = nowMs()
        val dt = if (lastTick == 0L) 0L else (now - lastTick).coerceAtMost(1000L)
        lastTick = now
        val item = current ?: return
        if (!isPlaying || mp == null) {
            applyVolume()
            return
        }
        val pos = rawPos
        if (abs(livePosition() - pos) > 300) setPos(pos)
        val dur = durationMs

        // 음악 아닌 구간 건너뛰기
        if (prefs.getBoolean("skip_offtopic", true)) {
            val seg = segments.firstOrNull { pos >= it.first && pos < it.last - 500 }
            if (seg != null) {
                seek(seg.last)
                onEvent("음악이 아닌 구간을 건너뛰었어요")
                return
            }
        }
        // A-B 구간 반복
        if (AbLoop.active && AbLoop.url == item.track.url && pos >= AbLoop.b) {
            seek(AbLoop.a)
            return
        }
        // 긴 곡 위치 기억
        if (dur > 10 * 60_000L && now - lastLongSave > 5_000L) {
            lastLongSave = now
            LongPositions.put(prefs, item.track.url, if (pos > 30_000L && dur - pos > 60_000L) pos else 0L)
        }
        // 청취 통계
        if (dt > 0) {
            listened += dt
            val count = !counted && listened >= 30_000L
            if (count) counted = true
            val t = item.track
            Stats.addListen(prefs, t.url, item.pid, t.title, t.artist, t.thumb, dt, count)
            if (now - lastStatsSave > 30_000L) {
                Stats.save(prefs)
                lastStatsSave = now
            }
        }
        // 페이드
        val fade = prefs.getInt("fade_ms", 0)
        var f = 1f
        if (fade > 0 && dur > 0) {
            if (pos < fade) f = min(f, pos / fade.toFloat())
            if (dur - pos < fade) f = min(f, (dur - pos) / fade.toFloat())
        }
        fadeFactor = f.coerceIn(0f, 1f)
        applyVolume()
    }
}
