@file:OptIn(ExperimentalLayoutApi::class)

package com.example.playlist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.io.File
import java.util.Calendar

val LocalWindow = staticCompositionLocalOf<ComposeWindow?> { null }

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun PanelScaffold(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "닫기 (Esc)") }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun SidePanelContent(state: AppState, panel: Panel, trackMenu: (Track, String, Int) -> List<ContextMenuItem>) {
    val close = { state.panel = Panel.NONE }
    when (panel) {
        Panel.SETTINGS -> PanelScaffold("설정", close) { SettingsBody(state) }
        Panel.STATS -> PanelScaffold("청취 통계", close) { StatsBody(state) }
        Panel.SEARCH -> PanelScaffold("유튜브에서 찾기", close) { SearchBody(state, trackMenu) }
        Panel.QUEUE -> PanelScaffold("재생 대기열", close) { QueueBody(state) }
        Panel.EQ -> PanelScaffold("이퀄라이저", close) { EqBody(state) }
        Panel.NONE -> {}
    }
}

// ======================= 검색 =======================

@Composable
private fun ColumnScope.SearchBody(state: AppState, trackMenu: (Track, String, Int) -> List<ContextMenuItem>) {
    var text by remember { mutableStateOf(state.lastQuery) }
    LaunchedEffect(state.lastQuery) { if (state.lastQuery.isNotEmpty()) text = state.lastQuery }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("곡 제목이나 가수 이름 (Enter)") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { state.search(text) }),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
    )
    Spacer(Modifier.height(6.dp))
    Text("누르면 바로 재생 · 오른쪽 클릭으로 좋아요·담기", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    when {
        state.searching -> SkeletonList(Modifier.weight(1f))
        state.searchError != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(state.searchError ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        else -> {
            val results = state.searchResults
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(results, key = { i, t -> "$i${t.url}" }) { i, t ->
                    val isCurrent = state.playingPid == SEARCH_PID && t.url == state.playingUrl
                    TrackRow(
                        number = i + 1, t = t, selected = isCurrent, playing = isCurrent && state.engine.isPlaying,
                        saved = false, favorite = state.isFavorite(t.url), isNew = false,
                        onClick = { state.playList(results, SEARCH_PID, i) },
                        onPlayNext = { state.playNext(t, SEARCH_PID) },
                        onQueue = { state.addToQueue(t, SEARCH_PID) },
                        onFav = { state.toggleFavorite(t) },
                        menu = { trackMenu(t, SEARCH_PID, i) }
                    )
                }
            }
        }
    }
}

// ======================= 대기열 =======================

@Composable
private fun ColumnScope.QueueBody(state: AppState) {
    val e = state.engine
    val order = e.order
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (e.orderPos - 2).coerceAtLeast(0))
    Text(
        if (e.shuffle) "셔플 순서로 보여줘요 (셔플 중에는 순서를 바꿀 수 없어요)" else "화살표로 순서를 바꾸고, X로 빼요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    if (order.isEmpty()) {
        Text("대기열이 비어 있어요", modifier = Modifier.padding(24.dp))
        return
    }
    Box(Modifier.weight(1f)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
            itemsIndexed(order, key = { _, qi -> qi }) { pos, qi ->
                val item = e.queue.getOrNull(qi) ?: return@itemsIndexed
                val isCur = pos == e.orderPos
                Row(
                    Modifier.animateItem().fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (isCur) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { e.playAtQueueIndex(qi) }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${pos + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
                    Thumb(item.track.thumb, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCur) FontWeight.SemiBold else FontWeight.Normal)
                        Text(item.track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!e.shuffle) {
                        IconButton(onClick = { e.move(qi, qi - 1) }, enabled = qi > 0) { Icon(Icons.Default.KeyboardArrowUp, "위로") }
                        IconButton(onClick = { e.move(qi, qi + 1) }, enabled = qi < e.queue.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "아래로") }
                    }
                    if (!isCur) IconButton(onClick = { e.removeAt(qi) }) { Icon(Icons.Default.Close, "빼기", Modifier.size(18.dp)) }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

// ======================= 이퀄라이저 =======================

private val EQ_PRESETS = listOf(
    "평탄" to List(10) { 0f },
    "저음 강조" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "고음 강조" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f),
    "보컬" to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f),
    "록" to listOf(5f, 3f, 1f, -1f, -2f, -1f, 1f, 3f, 4f, 5f),
    "팝" to listOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 0f, 1f),
    "클래식" to listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)
)

@Composable
private fun ColumnScope.EqBody(state: AppState) {
    val e = state.engine
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        SwitchRow("이퀄라이저 사용", "PC 재생기(JavaFX)의 10밴드 이퀄라이저예요", e.eqOn, e::updateEqOn)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EQ_PRESETS.forEach { (n, v) -> AssistChip(onClick = { e.setEqPreset(v) }, label = { Text(n) }, enabled = e.eqOn) }
        }
        Spacer(Modifier.height(10.dp))
        e.eqFreqs.forEachIndexed { i, f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (f >= 1000) "${f / 1000}kHz" else "${f}Hz", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
                Slider(
                    value = e.eqGains.getOrElse(i) { 0f },
                    onValueChange = { e.updateEqGain(i, it) },
                    valueRange = -12f..12f,
                    enabled = e.eqOn,
                    modifier = Modifier.weight(1f)
                )
                Text("%+.0f dB".format(e.eqGains.getOrElse(i) { 0f }), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(54.dp))
            }
        }
    }
}

// ======================= 통계 =======================

private fun minutesLabel(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분"
}

@Composable
private fun Bars(values: List<Long>, labels: List<String>, highlight: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurface
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = values.size
            val gap = size.width / n * 0.28f
            val bw = size.width / n - gap
            values.forEachIndexed { i, v ->
                val h = ((v.toFloat() / max) * size.height).coerceAtLeast(3f)
                drawRoundRect(
                    if (i == highlight) primary else dim.copy(alpha = if (v > 0) 0.3f else 0.1f),
                    topLeft = Offset(i * (bw + gap) + gap / 2, size.height - h),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(bw / 3)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ColumnScope.StatsBody(state: AppState) {
    var version by remember { mutableIntStateOf(0) }
    val snap = remember(version) { state.statsSnapshot() }
    val now = System.currentTimeMillis()
    val days = (6 downTo 0).map { now - it * 86_400_000L }
    val dayValues = days.map { snap.daily[Stats.dayKey(it)] ?: 0L }
    val names = listOf("일", "월", "화", "수", "목", "금", "토")
    val dayLabels = days.map { val c = Calendar.getInstance(); c.timeInMillis = it; names[c.get(Calendar.DAY_OF_WEEK) - 1] }
    val top = snap.tracks.filter { it.count > 0 }.sortedWith(compareByDescending<Stats.TrackStat> { it.count }.thenByDescending { it.ms }).take(15)
    val peak = snap.hourly.withIndex().maxByOrNull { it.value }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("오늘", minutesLabel(dayValues.last()), Modifier.weight(1f))
            StatTile("최근 7일", minutesLabel(dayValues.sum()), Modifier.weight(1f))
            StatTile("들은 곡", "${snap.tracks.size}곡", Modifier.weight(1f))
        }
        Text("최근 7일", style = MaterialTheme.typography.titleSmall)
        Bars(dayValues, dayLabels, dayValues.lastIndex)
        Text("자주 듣는 시간대", style = MaterialTheme.typography.titleSmall)
        Bars(snap.hourly, (0 until 24).map { if (it % 6 == 0) "${it}시" else "" }, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        if (peak != null && peak.value > 0) Text("주로 ${peak.index}시쯤 가장 많이 들어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("많이 들은 곡", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (top.isNotEmpty()) GlassChip("모아 듣기", icon = Icons.Default.PlayArrow, selected = true) { state.playTopTracks() }
        }
        if (top.isEmpty()) Text("아직 기록이 없어요. 한 곡을 30초 이상 들으면 1회로 기록돼요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        top.forEachIndexed { i, t ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { state.playTrackIn(t.pid, t.url) }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(26.dp))
                Thumb(t.thumb, Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${t.count}회 · ${minutesLabel(t.ms)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = { state.clearStats(); version++ }) { Text("기록 초기화") }
    }
}

// ======================= 설정 =======================

@Composable
private fun HourStepper(label: String, value: Int, step: Int = 1, mod: Int = 24, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step + mod) % mod) }) { Icon(Icons.Default.Remove, "빼기") }
        Text("%02d".format(value), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + step) % mod) }) { Icon(Icons.Default.Add, "더하기") }
    }
}

private fun chooseFile(window: ComposeWindow?, title: String, save: Boolean, defaultName: String = ""): File? {
    val d = FileDialog(window, title, if (save) FileDialog.SAVE else FileDialog.LOAD)
    if (defaultName.isNotEmpty()) d.file = defaultName
    d.isVisible = true
    val f = d.file ?: return null
    return File(d.directory, f)
}

@Composable
private fun ColumnScope.SettingsBody(state: AppState) {
    val window = LocalWindow.current
    val clipboard = LocalClipboardManager.current
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("화면")
        Text("테마", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("시스템", "라이트", "다크", "시간 예약").forEachIndexed { i, l ->
                FilterChip(selected = state.themeMode == i, onClick = { state.updateThemeMode(i) }, label = { Text(l) })
            }
        }
        if (state.themeMode == 3) {
            HourStepper("다크 모드 시작 (시)", state.nightStart) { state.updateNight(it, state.nightEnd) }
            HourStepper("다크 모드 끝 (시)", state.nightEnd) { state.updateNight(state.nightStart, it) }
        }
        Text("강조 색상", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ACCENTS.forEachIndexed { i, (name, color) ->
                val selected = state.accent == i
                val bg: Modifier = if (color == MONO) Modifier.background(Brush.horizontalGradient(0f to Color.Black, 0.5f to Color.Black, 0.5f to Color.White, 1f to Color.White))
                else Modifier.background(color)
                Box(
                    Modifier.size(36.dp).clip(CircleShape).then(bg)
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                        .clickable { state.updateAccent(i) },
                    contentAlignment = Alignment.Center
                ) { if (selected) Icon(Icons.Default.Check, name, tint = if (color == MONO) Color.Gray else Color.White) }
            }
        }
        SwitchRow("앨범 아트 색 따라가기", "재생 중인 곡의 커버 색으로 배경이 물들어요 (흑백 테마는 배경만)", state.useArtColor, state::updateArtColor)
        SwitchRow("리퀴드 글래스", "반투명 유리 질감과 움직이는 색 안개 배경", state.glass, state::updateGlass)
        Text("글자 크기", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.9f to "작게", 1f to "보통", 1.15f to "크게", 1.3f to "아주 크게").forEach { (v, l) ->
                FilterChip(selected = state.fontScale == v, onClick = { state.updateFontScale(v) }, label = { Text(l) })
            }
        }
        SwitchRow("창을 닫으면 트레이로", "X를 눌러도 꺼지지 않고 작업 표시줄 오른쪽 아이콘으로 숨어 계속 재생해요", state.closeToTray, state::updateCloseToTray)

        SectionTitle("재생")
        SwitchRow("이어듣기", "앱을 다시 열면 마지막 곡과 위치부터", state.resumeEnabled, state::updateResume)
        SwitchRow("다음 곡 미리 받기", "곡 사이 끊김 없이 넘어가요", state.prefetch, state::updatePrefetch)
        SwitchRow("음악 아닌 구간 건너뛰기", "뮤직비디오의 인트로·아웃트로를 SponsorBlock 데이터로 넘겨요", state.skipOfftopic, state::updateSkipOfftopic)
        Text("곡 전환 페이드", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "끄기", 2000 to "2초", 4000 to "4초", 6000 to "6초").forEach { (v, l) ->
                FilterChip(selected = state.fadeMs == v, onClick = { state.updateFadeMs(v) }, label = { Text(l) })
            }
        }
        SwitchRow("긴 곡 이어듣기", "10분이 넘는 곡은 들은 위치를 기억해요", state.longResume, state::updateLongResume)
        SwitchRow("열 때 새 곡 확인", "유튜브 플레이리스트에 새로 추가된 곡을 알려줘요", state.checkUpdates, state::updateCheckUpdates)
        SwitchRow("데이터 절약 음질", "낮은 음질로 받아요 (새로 받는 곡부터)", state.lowQuality, state::updateLowQuality)

        SectionTitle("음악 알람")
        SwitchRow(
            "음악으로 깨우기",
            if (state.alarmOn) "매일 %02d:%02d · 앱이 켜져 있을 때(트레이 포함) 울려요".format(state.alarmHour, state.alarmMinute) else "정한 시간에 플레이리스트를 서서히 커지는 소리로 틀어요",
            state.alarmOn
        ) { state.updateAlarm(on = it) }
        if (state.alarmOn) {
            HourStepper("시", state.alarmHour) { state.updateAlarm(h = it) }
            HourStepper("분", state.alarmMinute, step = 5, mod = 60) { state.updateAlarm(m = it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.playlists.filter { it.tracks.isNotEmpty() }.forEach { p ->
                    FilterChip(selected = state.alarmPid == p.id, onClick = { state.updateAlarm(pid = p.id) }, label = { Text(if (p.id == FAV_ID) "좋아요" else p.name, maxLines = 1) })
                }
            }
        }

        SectionTitle("저장 공간")
        Text("자동 캐시 크기", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1024, 2048, 5120, 10240).forEach { m ->
                FilterChip(selected = state.cacheMb == m, onClick = { state.updateCacheMb(m) }, label = { Text("${m / 1024}GB") })
            }
        }
        Text("자동 캐시 ${mb(state.cacheBytes)} · 오프라인 저장 ${mb(state.offlineBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = state::clearCache) { Text("자동 캐시 비우기") }
            OutlinedButton(onClick = state::clearOffline) { Text("저장 곡 모두 삭제") }
        }

        SectionTitle("백업 (폰과 호환)")
        Text("폰 앱의 설정 → 백업 → 내보내기 파일을 여기서 가져오면 플레이리스트와 좋아요가 그대로 옮겨져요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { chooseFile(window, "백업 저장", true, "ytplaylist_backup.json")?.let { state.exportTo(it) } }) {
                Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("내보내기")
            }
            OutlinedButton(onClick = { chooseFile(window, "백업 가져오기", false)?.let { state.importFrom(it) } }) {
                Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("가져오기")
            }
        }

        SectionTitle("단축키")
        Text(
            "Space 재생/일시정지 · Ctrl+→/← 다음/이전 곡 · →/← 5초 이동 · ↑/↓ 볼륨 · M 음소거 · L 전체 화면/가사 · S 셔플 · R 반복 · Ctrl+F 검색 · Esc 닫기",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionTitle("앱 정보")
        GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Playlist for PC 1.0.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "이 앱은 유튜브 공식 API가 아니라 공개 라이브러리(NewPipeExtractor)로 유튜브 정보를 읽어요. 유튜브가 구조를 바꾸면 갑자기 플레이리스트를 못 불러오거나 재생이 안 될 수 있어요. " +
                        "그때도 이미 받아둔 곡, 플레이리스트 목록, 좋아요, 통계는 그대로이고, 대부분 새 버전을 설치하면 해결돼요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassSurface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), tint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        clipboard.setText(AnnotatedString("tropicalgd2158"))
                        state.toast("디스코드 아이디를 복사했어요")
                    }
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Contact / discord : tropicalgd2158", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ContentCopy, "아이디 복사", Modifier.size(18.dp))
                    }
                }
            }
        }
        SectionTitle("기타")
        TextButton(onClick = { state.clearRecent() }) { Text("최근 들은 곡 기록 지우기") }
        TextButton(onClick = { state.showOnboardingAgain() }) { Text("사용법 안내 다시 보기") }
        Spacer(Modifier.height(24.dp))
    }
}

// ======================= 대화상자 =======================

@Composable
fun AddPlaylistDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit, onCreateEmpty: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("플레이리스트 추가") },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    label = { Text("유튜브 플레이리스트 링크") },
                    trailingIcon = { IconButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Icon(Icons.Default.ContentPaste, "붙여넣기") } },
                    modifier = Modifier.width(460.dp)
                )
                TextButton(onClick = { onCreateEmpty(); onDismiss() }) { Text("링크 없이 빈 플레이리스트 만들기") }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text); onDismiss() }, enabled = text.isNotBlank()) { Text("추가") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("이름") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
fun TagDialog(state: AppState, pid: String, onDismiss: () -> Unit) {
    val p = state.playlist(pid) ?: return
    var text by remember { mutableStateOf(p.tag) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("태그 정하기") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it.take(12) }, singleLine = true, label = { Text("태그") })
                if (state.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { state.tags.forEach { t -> AssistChip(onClick = { text = t }, label = { Text("#$t") }) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { state.updateTag(pid, text); onDismiss() }) { Text("저장") } },
        dismissButton = { TextButton(onClick = { state.updateTag(pid, ""); onDismiss() }) { Text("태그 없애기") } }
    )
}

@Composable
fun MixDialog(state: AppState, onDismiss: () -> Unit) {
    val candidates = state.playlists.filter { it.tracks.isNotEmpty() }
    var picked by remember { mutableStateOf(candidates.filter { it.id != FAV_ID }.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("섞어 듣기") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                candidates.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { picked = if (p.id in picked) picked - p.id else picked + p.id }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = p.id in picked, onCheckedChange = { picked = if (it) picked + p.id else picked - p.id })
                        Text("${if (p.id == FAV_ID) "좋아요" else p.name} (${p.tracks.size})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { state.playMix(picked); onDismiss() }, enabled = picked.isNotEmpty()) { Text("섞어서 재생") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
fun OnboardingDialog(onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Playlist for PC") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(480.dp)) {
                Text("유튜브 플레이리스트를 음악 앱처럼 들어요.")
                Text("• 폰에서 쓰던 목록은 설정 → 백업 → 가져오기로 옮길 수 있어요 (폰 앱에서 먼저 내보내기)")
                Text("• 곡에 마우스를 올리면 다음에 재생·대기열·좋아요 버튼이, 오른쪽 클릭하면 메뉴가 나와요")
                Text("• 아래 재생 바의 곡을 누르거나 L 키를 누르면 가사와 함께 크게 보여요")
                Text("• Space 재생/일시정지, Ctrl+→ 다음 곡 같은 단축키를 쓸 수 있어요")
                Text("• 창을 닫아도 오른쪽 아래 트레이 아이콘에서 계속 재생돼요")
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("시작하기") } }
    )
}
