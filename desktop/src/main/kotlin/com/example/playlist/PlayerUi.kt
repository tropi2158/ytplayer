@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package com.example.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

fun speedLabel(v: Float): String = if (v == v.toInt().toFloat()) "${v.toInt()}.0x" else "${v}x"

/** 시크바: 그리기 단계에서만 위치 계산, 끄는 동안 시간 풍선 */
@Composable
fun SeekBar(state: AppState, compact: Boolean = false) {
    val e = state.engine
    var drag by remember { mutableStateOf<Float?>(null) }
    val dragState = rememberUpdatedState(drag)
    var hovered by remember { mutableStateOf(false) }
    val clock = rememberFrameClock(e.isPlaying && drag == null)
    val slow = rememberSlowClock(e.isPlaying)
    val thumb = animateFloatAsState(if (drag != null || hovered) 1f else 0f, spring(0.5f, 480f), label = "thumb")
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val shownPos = drag?.let { (it * e.durationMs).toLong() } ?: run {
        slow.value
        e.livePosition()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(fmtTime(shownPos), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(if (compact) 42.dp else 52.dp), textAlign = TextAlign.End)
        Box(
            Modifier
                .weight(1f)
                .height(28.dp)
                .padding(horizontal = 10.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { o -> e.seek(((o.x / size.width).coerceIn(0f, 1f) * e.durationMs).toLong()) })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            drag?.let { e.seek((it * e.durationMs).toLong()) }
                            drag = null
                        },
                        onDragCancel = { drag = null }
                    ) { change, _ ->
                        change.consume()
                        drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
                .drawBehind {
                    clock.value
                    val d = e.durationMs
                    val f = (dragState.value ?: if (d > 0) e.livePosition().toFloat() / d else 0f).coerceIn(0f, 1f)
                    val t = thumb.value
                    val h = 4.dp.toPx() + 2.dp.toPx() * t
                    val cy = size.height / 2f
                    val r = CornerRadius(h / 2f)
                    drawRoundRect(trackColor, topLeft = Offset(0f, cy - h / 2f), size = Size(size.width, h), cornerRadius = r)
                    drawRoundRect(primary, topLeft = Offset(0f, cy - h / 2f), size = Size(size.width * f, h), cornerRadius = r)
                    drawCircle(primary, radius = 2.dp.toPx() + 5.dp.toPx() * t, center = Offset(size.width * f, cy))
                }
        )
        Text(fmtTime(e.durationMs), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(if (compact) 42.dp else 52.dp))
    }
}

/** 재생 도구 묶음 메뉴 (수면 타이머, 속도, 구간 반복, 이퀄라이저) */
@Composable
fun MoreToolsMenu(state: AppState, expanded: Boolean, onDismiss: () -> Unit) {
    val e = state.engine
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text("재생 속도", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        Row(Modifier.padding(horizontal = 8.dp)) {
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                FilterChip(selected = e.speed == sp, onClick = { e.updateSpeed(sp) }, label = { Text(speedLabel(sp)) }, modifier = Modifier.padding(2.dp))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("수면 타이머", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        Row(Modifier.padding(horizontal = 8.dp)) {
            listOf(15, 30, 60, 90).forEach { m ->
                AssistChip(onClick = { state.startSleepMinutes(m); onDismiss() }, label = { Text("${m}분") }, modifier = Modifier.padding(2.dp))
            }
        }
        DropdownMenuItem(text = { Text("현재 곡이 끝나면 멈추기") }, onClick = { state.startSleepEndOfTrack(); onDismiss() })
        if (state.sleepActive) DropdownMenuItem(text = { Text("수면 타이머 끄기") }, onClick = { state.cancelSleep(); onDismiss() })
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = {
                Text(
                    when {
                        AbLoop.url != state.playingUrl || AbLoop.a < 0 -> "구간 반복: A 지점 정하기"
                        AbLoop.b < 0 -> "구간 반복: B 지점 정하기"
                        else -> "구간 반복 끄기"
                    }
                )
            },
            leadingIcon = { Icon(Icons.Default.Loop, null) },
            onClick = { state.abStep(); onDismiss() }
        )
        DropdownMenuItem(text = { Text("이퀄라이저") }, leadingIcon = { Icon(Icons.Default.Equalizer, null) }, onClick = { state.panel = Panel.EQ; onDismiss() })
    }
}

/** 화면 아래 재생 바 */
@Composable
fun PlayerBar(state: AppState, onExpand: () -> Unit) {
    val e = state.engine
    val cur = e.current ?: return
    var moreOpen by remember { mutableStateOf(false) }
    val fav = state.isFavorite(cur.track.url)
    GlassSurface(modifier = Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(28.dp), strong = true) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // 왼쪽: 곡 정보
            Row(Modifier.weight(0.3f).clip(RoundedCornerShape(16.dp)).clickable { onExpand() }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Crossfade(targetState = cur.track.thumb, animationSpec = tween(350), label = "barArt") { art ->
                    Thumb(art, Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(cur.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        cur.track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { state.toggleFavoriteCurrent() }) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "좋아요",
                        tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 가운데: 버튼 + 시크바
            Column(Modifier.weight(0.45f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = e::toggleShuffle) {
                        Icon(Icons.Default.Shuffle, "셔플", tint = if (e.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = e::previous) { Icon(Icons.Default.SkipPrevious, "이전") }
                    GlassSurface(modifier = Modifier.size(46.dp), shape = CircleShape, tint = MaterialTheme.colorScheme.primary, onClick = e::togglePlay, pressScale = 0.88f) {
                        PlayPauseIcon(e.isPlaying, 26.dp)
                        if (e.isLoading) CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 2.dp)
                    }
                    IconButton(onClick = e::next) { Icon(Icons.Default.SkipNext, "다음") }
                    IconButton(onClick = e::cycleRepeat) {
                        Icon(
                            if (e.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat, "반복",
                            tint = if (e.repeatMode == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                SeekBar(state, compact = true)
            }
            // 오른쪽: 도구 + 볼륨
            Row(Modifier.weight(0.25f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onExpand) { Icon(Icons.Default.FormatQuote, "가사·전체 화면") }
                IconButton(onClick = { state.panel = if (state.panel == Panel.QUEUE) Panel.NONE else Panel.QUEUE }) { Icon(Icons.Default.QueueMusic, "대기열") }
                IconButton(onClick = e::toggleMute) {
                    Icon(if (e.muted || e.volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "음소거")
                }
                Slider(
                    value = if (e.muted) 0f else e.volume,
                    onValueChange = { e.updateVolume(it) },
                    modifier = Modifier.width(110.dp)
                )
                Box {
                    IconButton(onClick = { moreOpen = true }) {
                        Icon(Icons.Default.MoreVert, "더보기", tint = if (state.sleepActive || e.speed != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoreToolsMenu(state, moreOpen) { moreOpen = false }
                }
            }
        }
    }
}

/** 전체 화면 플레이어: 왼쪽 앨범 아트 캐러셀, 오른쪽 가사 */
@Composable
fun PlayerOverlay(state: AppState, expand: Animatable<Float, AnimationVector1D>, heightPx: Float, onSettle: (Float) -> Unit, onCollapse: () -> Unit) {
    val visible by remember { derivedStateOf { expand.value > 0.001f } }
    if (!visible) return
    val e = state.engine
    val cur = e.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var moreOpen by remember { mutableStateOf(false) }
    LaunchedEffect(cur?.track?.url) { state.loadLyrics() }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.45f * expand.value)) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { d -> scope.launch { expand.snapTo((expand.value - d / heightPx).coerceIn(0f, 1f)) } },
                onDragStopped = { v -> onSettle(v) }
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - expand.value) * heightPx
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    clip = true
                }
                .background(cs.surface)
        ) {
            GlassBackground(state.bgColors, deep = true)
            if (cur == null) return@Box
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 18.dp).graphicsLayer {
                    val v = expand.value
                    alpha = ((v - 0.15f) / 0.85f).coerceIn(0f, 1f)
                    val s = 0.95f + 0.05f * v
                    scaleX = s
                    scaleY = s
                }
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowDown, "접기 (Esc)", Modifier.size(32.dp)) }
                    val plName = state.playlist(cur.pid)?.let { if (it.id == FAV_ID) "좋아요" else it.name } ?: if (cur.pid == SEARCH_PID) "검색 결과" else ""
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("지금 재생 중", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        if (plName.isNotEmpty()) Text(plName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { state.panel = Panel.QUEUE }) { Icon(Icons.Default.QueueMusic, "대기열") }
                }
                Row(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
                    ArtPager(state, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        AnimatedContent(
                            targetState = cur.track,
                            transitionSpec = {
                                (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 3 }) togetherWith
                                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 3 })
                            },
                            label = "title"
                        ) { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Row(
                                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { state.findArtist(t.artist) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(t.artist, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Icon(Icons.Default.ChevronRight, "이 가수 다른 곡 찾기", Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                                    }
                                }
                                val fav = state.isFavorite(t.url)
                                IconButton(onClick = { state.toggleFavorite(t) }) {
                                    Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "좋아요", tint = if (fav) cs.primary else cs.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        GlassSurface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(24.dp), strong = true) {
                            LyricsView(state, Modifier.fillMaxSize().padding(horizontal = 20.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        UpNext(state)
                    }
                }
                SeekBar(state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = e::toggleShuffle) {
                        Icon(Icons.Default.Shuffle, "셔플", tint = if (e.shuffle) cs.primary else cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(16.dp))
                    GlassSurface(Modifier.size(56.dp), shape = CircleShape, onClick = e::previous) { Icon(Icons.Default.SkipPrevious, "이전", Modifier.size(30.dp)) }
                    Spacer(Modifier.width(16.dp))
                    GlassSurface(Modifier.size(76.dp), shape = CircleShape, tint = cs.primary, onClick = e::togglePlay, pressScale = 0.9f) {
                        PlayPauseIcon(e.isPlaying, 40.dp)
                        if (e.isLoading) CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    GlassSurface(Modifier.size(56.dp), shape = CircleShape, onClick = e::next) { Icon(Icons.Default.SkipNext, "다음", Modifier.size(30.dp)) }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = e::cycleRepeat) {
                        Icon(if (e.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat, "반복", tint = if (e.repeatMode == 0) cs.onSurfaceVariant else cs.primary)
                    }
                    Spacer(Modifier.width(32.dp))
                    IconButton(onClick = e::toggleMute) { Icon(if (e.muted || e.volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "음소거") }
                    Slider(value = if (e.muted) 0f else e.volume, onValueChange = { e.updateVolume(it) }, modifier = Modifier.width(130.dp))
                    Box {
                        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "더보기") }
                        MoreToolsMenu(state, moreOpen) { moreOpen = false }
                    }
                }
                val status = buildList {
                    if (state.sleepEndOfTrack) add("수면 타이머: 곡 끝에서")
                    else if (state.sleepRemainingMs > 0) add("수면 타이머 ${fmtTime(state.sleepRemainingMs)}")
                    if (e.speed != 1f) add("속도 ${speedLabel(e.speed)}")
                    if (AbLoop.active && AbLoop.url == cur.track.url) add("A-B 반복 ${fmtTime(AbLoop.a)}~${fmtTime(AbLoop.b)}")
                    else if (AbLoop.url == cur.track.url && AbLoop.a >= 0) add("A 지점 ${fmtTime(AbLoop.a)}")
                    if (e.eqOn) add("이퀄라이저 켜짐")
                }
                if (status.isNotEmpty()) {
                    Text(
                        status.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = cs.primary,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtPager(state: AppState, modifier: Modifier) {
    val e = state.engine
    val order = e.order
    val queue = e.queue
    if (order.isEmpty()) {
        Box(modifier)
        return
    }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = e.orderPos.coerceAtLeast(0)) { e.order.size }
    var userMoved by remember { mutableStateOf(false) }
    LaunchedEffect(e.orderPos) {
        val target = e.orderPos
        if (target >= 0 && target != pagerState.currentPage && !pagerState.isScrollInProgress) {
            if (abs(target - pagerState.currentPage) > 3) pagerState.scrollToPage(target)
            else pagerState.animateScrollToPage(target, animationSpec = spring(dampingRatio = 0.72f, stiffness = 220f))
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { p ->
            if (userMoved) {
                userMoved = false
                val qi = e.order.getOrNull(p)
                if (qi != null && p != e.orderPos) e.playAtQueueIndex(qi)
            }
        }
    }
    fun go(delta: Int) {
        val t = (pagerState.currentPage + delta).coerceIn(0, order.lastIndex)
        userMoved = true
        scope.launch { pagerState.animateScrollToPage(t, animationSpec = spring(dampingRatio = 0.72f, stiffness = 220f)) }
    }
    Box(modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().onPointerEvent(PointerEventType.Scroll) { ev ->
                val d = ev.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                val v = if (abs(d.x) > abs(d.y)) d.x else d.y
                if (abs(v) > 0.5f && !pagerState.isScrollInProgress) go(if (v > 0) 1 else -1)
            },
            contentPadding = PaddingValues(horizontal = 40.dp),
            pageSpacing = 8.dp,
            userScrollEnabled = false,
            key = { i -> e.order.getOrNull(i) ?: i }
        ) { page ->
            val item = queue.getOrNull(order.getOrNull(page) ?: -1)
            BoxWithConstraints(
                Modifier.fillMaxSize().graphicsLayer {
                    val raw = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val off = abs(raw).coerceIn(0f, 1f)
                    val s = 1f - 0.16f * off
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.6f * off
                    rotationY = raw.coerceIn(-1f, 1f) * -8f
                },
                contentAlignment = Alignment.Center
            ) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                GlassSurface(
                    modifier = Modifier.size(side).shadow(22.dp, RoundedCornerShape(30.dp)),
                    shape = RoundedCornerShape(30.dp),
                    onClick = { if (page != pagerState.currentPage) go(page - pagerState.currentPage) else e.togglePlay() },
                    pressScale = 0.98f
                ) {
                    AsyncImage(
                        model = item?.track?.thumb, contentDescription = item?.track?.title, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(25.dp))
                    )
                }
            }
        }
        // 좌우 화살표
        GlassSurface(Modifier.align(Alignment.CenterStart).size(44.dp), shape = CircleShape, strong = true, onClick = { go(-1) }) {
            Icon(Icons.Default.ChevronLeft, "이전 곡")
        }
        GlassSurface(Modifier.align(Alignment.CenterEnd).size(44.dp), shape = CircleShape, strong = true, onClick = { go(1) }) {
            Icon(Icons.Default.ChevronRight, "다음 곡")
        }
        Text(
            "마우스 휠이나 화살표로 넘기기",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun LyricsView(state: AppState, modifier: Modifier) {
    val st = state.lyrics
    Box(modifier, contentAlignment = Alignment.Center) {
        when (st) {
            LyricsState.Idle, LyricsState.Loading -> CircularProgressIndicator()
            LyricsState.NotFound -> Text("이 곡의 가사를 찾지 못했어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LyricsState.Plain -> Text(
                st.text.ifBlank { "가사가 비어 있어요" },
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)
            )
            is LyricsState.Synced -> SyncedLyrics(state, st.lines)
        }
    }
}

@Composable
private fun SyncedLyrics(state: AppState, lines: List<LyricLine>) {
    val e = state.engine
    val slow = rememberSlowClock(e.isPlaying)
    val pos = run {
        slow.value
        e.positionMs
        e.livePosition()
    }
    val cur = lines.indexOfLast { it.ms <= pos + 250 }
    val listState = rememberLazyListState()
    LaunchedEffect(cur) { if (cur >= 0) listState.animateScrollToItem((cur - 3).coerceAtLeast(0)) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
            val active = i == cur
            val w by animateFloatAsState(if (active) 1f else 0f, spring(0.7f, 300f), label = "ly")
            Text(
                line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer {
                        alpha = 0.45f + 0.55f * w
                        val sc = 0.94f + 0.06f * w
                        scaleX = sc
                        scaleY = sc
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { e.seek(line.ms) }
            )
        }
    }
}

@Composable
private fun UpNext(state: AppState) {
    val e = state.engine
    val nextQi = when {
        e.repeatMode == 2 -> null
        e.orderPos + 1 < e.order.size -> e.order[e.orderPos + 1]
        e.repeatMode == 1 && e.order.isNotEmpty() -> e.order[0]
        else -> null
    }
    val next = nextQi?.let { e.queue.getOrNull(it) }
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), strong = true, onClick = if (next != null) e::next else null, pressScale = 0.98f) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = next,
                transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }) togetherWith (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 }) },
                label = "upNext",
                modifier = Modifier.weight(1f)
            ) { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (n != null) {
                        Thumb(n.track.thumb, Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Text(if (e.repeatMode == 2) "한 곡 반복 중" else "다음 곡", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(n?.track?.title ?: "대기열의 마지막 곡이에요", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (next != null) Icon(Icons.Default.SkipNext, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
