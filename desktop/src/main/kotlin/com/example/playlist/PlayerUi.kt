@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package com.example.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
    val primary by androidx.compose.animation.animateColorAsState(AlbumAccent.color, tween(600), label = "seekAccent")
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
                    Text(cur.track.shownTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        cur.track.shownArtist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { state.toggleFavoriteCurrent() }) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "좋아요",
                        tint = if (fav) AlbumAccent.color else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 가운데: 버튼 + 시크바
            Column(Modifier.weight(0.45f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    PlainControl(38.dp, e::toggleShuffle) { Icon(Icons.Default.Shuffle, "셔플", Modifier.size(20.dp), tint = if (e.shuffle) AlbumAccent.color else dim) }
                    PlainControl(42.dp, e::previous) { Icon(Icons.Default.SkipPrevious, "이전", Modifier.size(30.dp)) }
                    PlainControl(48.dp, e::togglePlay) {
                        PlayPauseIcon(e.isPlaying, 34.dp)
                        if (e.isLoading) CircularProgressIndicator(Modifier.size(44.dp), strokeWidth = 1.5.dp, color = dim)
                    }
                    PlainControl(42.dp, e::next) { Icon(Icons.Default.SkipNext, "다음", Modifier.size(30.dp)) }
                    PlainControl(38.dp, e::cycleRepeat) {
                        Icon(if (e.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat, "반복", Modifier.size(20.dp), tint = if (e.repeatMode == 0) dim else AlbumAccent.color)
                    }
                }
                SeekBar(state, compact = true)
            }
            // 오른쪽: 도구 + 볼륨
            Row(Modifier.weight(0.25f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onExpand) { Icon(Icons.Default.FormatQuote, "가사·전체 화면") }
                IconButton(onClick = { state.panel = if (state.panel == Panel.QUEUE) Panel.NONE else Panel.QUEUE }) { Icon(Icons.Default.QueueMusic, "대기열") }
                VolumeBar(state, Modifier.width(150.dp))
                Box {
                    IconButton(onClick = { moreOpen = true }) {
                        Icon(Icons.Default.MoreHoriz, "더보기", tint = if (state.sleepActive || e.speed != 1f) AlbumAccent.color else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoreToolsMenu(state, moreOpen) { moreOpen = false }
                }
            }
        }
    }
}

/** 전체 화면 플레이어: 왼쪽 아트·제목·컨트롤, 오른쪽 가사 (Mac용 Apple Music 느낌) */
@Composable
fun PlayerOverlay(state: AppState, expand: Animatable<Float, AnimationVector1D>, heightPx: Float, onSettle: (Float) -> Unit, onCollapse: () -> Unit) {
    val visible by remember { derivedStateOf { expand.value > 0.001f } }
    if (!visible) return
    val e = state.engine
    val cur = e.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var moreOpen by remember { mutableStateOf(false) }
    LaunchedEffect(cur?.track?.url, state.showLyrics) { if (state.showLyrics) state.loadLyrics() }
    val dim = cs.onSurface.copy(alpha = 0.5f)
    val on = cs.onSurface
    val accent = AlbumAccent.color

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
            GlassBackground(state.bgColors, deep = true, art = cur?.track?.thumb)
            if (cur == null) return@Box
            Column(
                Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 18.dp).graphicsLayer {
                    val v = expand.value
                    alpha = ((v - 0.15f) / 0.85f).coerceIn(0f, 1f)
                    val s = 0.95f + 0.05f * v
                    scaleX = s
                    scaleY = s
                }
            ) {
                // ----- 위: 접기 / 어디서 재생 중 / 더보기 -----
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowDown, "접기 (Esc)", Modifier.size(30.dp)) }
                    val plName = state.playlist(cur.pid)?.let { if (it.id == FAV_ID) "좋아요" else it.name }
                        ?: when (cur.pid) { SEARCH_PID -> "검색 결과"; RADIO_PID -> "비슷한 곡 라디오"; else -> "" }
                    Text(
                        plName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreHoriz, "더보기") }
                        MoreToolsMenu(state, moreOpen) { moreOpen = false }
                    }
                }

                Row(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // ----- 왼쪽: 아트 + 정보 + 컨트롤 -----
                    Column(
                        Modifier.weight(1f).fillMaxHeight().widthIn(max = 620.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ArtPager(state, Modifier.weight(1f).fillMaxWidth())
                        Spacer(Modifier.height(22.dp))
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
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
                                        Text(t.shownTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(
                                            Modifier.clip(RoundedCornerShape(8.dp)).pointerHoverIcon(PointerIcon.Hand).clickable { state.findArtist(t.artist) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(t.shownArtist, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Icon(Icons.Default.ChevronRight, "이 가수 다른 곡 찾기", Modifier.size(18.dp), tint = dim)
                                        }
                                    }
                                    val fav = state.isFavorite(t.url)
                                    IconButton(onClick = { state.toggleFavorite(t) }) {
                                        Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "좋아요", tint = if (fav) accent else dim)
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            SeekBar(state)
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlainControl(44.dp, e::toggleShuffle) { Icon(Icons.Default.Shuffle, "셔플 (S)", Modifier.size(22.dp), tint = if (e.shuffle) accent else dim) }
                                PlainControl(60.dp, e::previous) { Icon(Icons.Default.SkipPrevious, "이전 (Ctrl+←)", Modifier.size(40.dp), tint = on) }
                                PlainControl(78.dp, e::togglePlay) {
                                    PlayPauseIcon(e.isPlaying, 54.dp)
                                    if (e.isLoading) CircularProgressIndicator(Modifier.size(70.dp), strokeWidth = 1.5.dp, color = dim)
                                }
                                PlainControl(60.dp, e::next) { Icon(Icons.Default.SkipNext, "다음 (Ctrl+→)", Modifier.size(40.dp), tint = on) }
                                PlainControl(44.dp, e::cycleRepeat) {
                                    Icon(if (e.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat, "반복 (R)", Modifier.size(22.dp), tint = if (e.repeatMode == 0) dim else accent)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            AudioVisualizer(state)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                PlainControl(44.dp, { state.showLyrics = !state.showLyrics }) {
                                    Icon(Icons.Default.FormatQuote, "가사", Modifier.size(22.dp), tint = if (state.showLyrics) on else dim)
                                }
                                val status = buildList {
                                    if (state.sleepEndOfTrack) add("곡 끝에서 멈춤")
                                    else if (state.sleepRemainingMs > 0) add("타이머 ${fmtTime(state.sleepRemainingMs)}")
                                    if (e.speed != 1f) add(speedLabel(e.speed))
                                    if (AbLoop.active && AbLoop.url == cur.track.url) add("A-B ${fmtTime(AbLoop.a)}~${fmtTime(AbLoop.b)}")
                                    if (e.eqOn) add("EQ")
                                }
                                Text(status.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = accent)
                                PlainControl(44.dp, { state.panel = if (state.panel == Panel.QUEUE) Panel.NONE else Panel.QUEUE }) {
                                    Icon(Icons.Default.QueueMusic, "대기열", Modifier.size(22.dp), tint = dim)
                                }
                            }
                        }
                    }
                    // ----- 오른쪽: 가사 -----
                    AnimatedVisibility(
                        visible = state.showLyrics,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        enter = fadeIn(tween(300)) + slideInHorizontally(spring(0.85f, 300f)) { it / 6 },
                        exit = fadeOut(tween(200))
                    ) {
                        Box(Modifier.fillMaxSize().padding(start = 48.dp)) {
                            LyricsView(state, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/** 판 없는 아이콘 버튼: 올리면 살짝 커지고, 누르면 작아졌다 튕겨 돌아와요 */
@Composable
fun PlainControl(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hovered by src.collectIsHoveredAsState()
    val sc by animateFloatAsState(when { pressed -> 0.82f; hovered -> 1.08f; else -> 1f }, spring(dampingRatio = 0.45f, stiffness = 700f), label = "ctl")
    Box(
        Modifier.size(size)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(CircleShape)
            .hoverable(src)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = src, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** 얇은 볼륨 바 (끌거나 눌러서 조절, 휠도 됨) */
@Composable
fun VolumeBar(state: AppState, modifier: Modifier = Modifier.fillMaxWidth()) {
    val e = state.engine
    var dragging by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    val vol = if (e.muted) 0f else e.volume
    val shown by animateFloatAsState(vol, spring(dampingRatio = 0.9f, stiffness = 800f), label = "vol")
    val thick by animateFloatAsState(if (dragging || hovered) 1f else 0f, spring(0.6f, 500f), label = "volThick")
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (vol == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeMute, "음소거 (M)",
            Modifier.size(18.dp).pointerHoverIcon(PointerIcon.Hand).clickable { e.toggleMute() }, tint = dim
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(24.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .onPointerEvent(PointerEventType.Scroll) { ev ->
                    val d = ev.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    e.updateVolume((e.volume - d * 0.04f).coerceIn(0f, 1f))
                }
                .pointerInput(Unit) { detectTapGestures { o -> e.updateVolume((o.x / size.width).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; e.updateVolume((o.x / size.width).coerceIn(0f, 1f)) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, _ ->
                        change.consume()
                        e.updateVolume((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .drawBehind {
                    val h = 4.dp.toPx() + 3.dp.toPx() * thick
                    val cy = size.height / 2f
                    val r = CornerRadius(h / 2f)
                    drawRoundRect(track, topLeft = Offset(0f, cy - h / 2f), size = Size(size.width, h), cornerRadius = r)
                    drawRoundRect(fill, topLeft = Offset(0f, cy - h / 2f), size = Size(size.width * shown, h), cornerRadius = r)
                }
        )
        Spacer(Modifier.width(10.dp))
        Icon(Icons.Default.VolumeUp, "크게", Modifier.size(18.dp), tint = dim)
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
    var hovered by remember { mutableStateOf(false) }
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
    Box(
        modifier
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().onPointerEvent(PointerEventType.Scroll) { ev ->
                val d = ev.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                val v = if (abs(d.x) > abs(d.y)) d.x else d.y
                if (abs(v) > 0.5f && !pagerState.isScrollInProgress) go(if (v > 0) 1 else -1)
            },
            pageSpacing = 40.dp,
            userScrollEnabled = false,
            key = { i -> e.order.getOrNull(i) ?: i }
        ) { page ->
            val item = queue.getOrNull(order.getOrNull(page) ?: -1)
            val isCur = page == e.orderPos
            BoxWithConstraints(
                Modifier.fillMaxSize().graphicsLayer {
                    val raw = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val off = abs(raw).coerceIn(0f, 1f)
                    val s = 1f - 0.12f * off
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.7f * off
                },
                contentAlignment = Alignment.Center
            ) {
                // 정사각형 앨범 아트면 1:1, 영상 썸네일이면 원래 비율
                var ratio by remember(item?.track?.thumb) { mutableFloatStateOf(1f) }
                val aRatio by animateFloatAsState(ratio, spring(0.8f, 300f), label = "artRatio")
                val w = if (maxWidth / aRatio <= maxHeight) maxWidth else maxHeight * aRatio
                val h = w / aRatio
                val playScale by animateFloatAsState(if (e.isPlaying || !isCur) 1f else 0.88f, spring(0.62f, 260f), label = "artPlay")
                val heart = remember { Animatable(0f) }
                val ctx = coil3.compose.LocalPlatformContext.current
                val req = remember(item?.track?.thumb) {
                    coil3.request.ImageRequest.Builder(ctx).data(hiResOf(item?.track?.thumb)).build()
                }
                Box(
                    Modifier.size(w, h)
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                        .shadow(if (e.isPlaying) 30.dp else 14.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .pointerInput(isCur) {
                            detectTapGestures(
                                onTap = { if (!isCur) go(page - pagerState.currentPage) else e.togglePlay() },
                                onDoubleTap = {
                                    // 두 번 클릭 → 좋아요 + 하트
                                    if (!state.isFavorite(item?.track?.url ?: "")) state.toggleFavoriteCurrent()
                                    scope.launch {
                                        heart.snapTo(0f)
                                        heart.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
                                        delay(350)
                                        heart.animateTo(0f, tween(220))
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = req, contentDescription = item?.track?.title, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { st ->
                            val img = st.result.image
                            if (img is coil3.BitmapImage) ratio = artRatioOf(img.bitmap)
                        }
                    )
                    Icon(
                        Icons.Default.Favorite, null,
                        Modifier.size(110.dp).graphicsLayer {
                            val v = heart.value
                            scaleX = 0.4f + 0.6f * v
                            scaleY = 0.4f + 0.6f * v
                            alpha = v.coerceIn(0f, 1f)
                        },
                        tint = Color.White
                    )
                }
            }
        }
        // 마우스를 올렸을 때만 좌우 화살표
        androidx.compose.animation.AnimatedVisibility(hovered, Modifier.align(Alignment.CenterStart), enter = fadeIn(), exit = fadeOut()) {
            PlainControl(44.dp, { go(-1) }) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChevronLeft, "이전 곡", tint = Color.White)
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(hovered, Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
            PlainControl(44.dp, { go(1) }) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChevronRight, "다음 곡", tint = Color.White)
                }
            }
        }
    }
}

/** 유튜브 썸네일은 가장 큰 화질 주소로 (실패하면 Coil이 원래 주소를 못 쓰므로 hq720 → 원래 순) */
fun hiResOf(url: String?): String? {
    if (url == null) return null
    val m = Regex("""i\.ytimg\.com/vi(?:_webp)?/([A-Za-z0-9_-]{6,})/""").find(url) ?: return url
    return if (url.contains("hq720") || url.contains("maxres")) url else "https://i.ytimg.com/vi/${m.groupValues[1]}/hq720.jpg"
}

@Composable
private fun LyricsView(state: AppState, modifier: Modifier) {
    val st = state.lyrics
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        when (st) {
            LyricsState.Idle, LyricsState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            LyricsState.NotFound -> Text("이 곡의 가사를 찾지 못했어요", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
            is LyricsState.Plain -> Text(
                st.text.ifBlank { "가사가 비어 있어요" },
                style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 38.sp), fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)
            )
            is LyricsState.Synced -> SyncedLyrics(state, st.lines)
        }
    }
}

/** Apple Music 가사: 크고 굵게, 지금 줄만 또렷하고 멀어질수록 흐리고 옅게 */
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
    LaunchedEffect(cur) { if (cur >= 0) listState.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 60.dp, bottom = 320.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
            val active = i == cur
            val dist = if (cur < 0) 3 else abs(i - cur).coerceAtMost(4)
            val w by animateFloatAsState(if (active) 1f else 0f, spring(0.7f, 260f), label = "ly")
            val blurR by animateFloatAsState(if (active) 0f else dist * 1.2f, tween(400), label = "lyBlur")
            Text(
                line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 42.sp),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer {
                        alpha = 0.28f + 0.72f * w
                        val sc = 0.96f + 0.04f * w
                        scaleX = sc
                        scaleY = sc
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .blur(blurR.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                    .clip(RoundedCornerShape(10.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { e.seek(line.ms) }
                    .padding(vertical = 2.dp)
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


/** 오디오 비주얼라이저: 소리에 맞춰 출렁이는 부드러운 파동 세 가닥 (낮은음 왼쪽, 높은음 오른쪽) */
@Composable
fun AudioVisualizer(state: AppState, modifier: Modifier = Modifier.fillMaxWidth().height(64.dp)) {
    val e = state.engine
    val n = Spectrum.BANDS
    val clock = rememberFrameClock(true)
    val target = remember { FloatArray(n) }
    val shown = remember { FloatArray(n) }
    val smooth = remember { FloatArray(n) }
    val accent by androidx.compose.animation.animateColorAsState(AlbumAccent.color, tween(600), label = "vizAccent")
    androidx.compose.foundation.Canvas(modifier) {
        clock.value
        if (e.isPlaying) Spectrum.current(target) else java.util.Arrays.fill(target, 0f)
        // 막대 값을 부드럽게 따라가고, 옆 칸끼리 섞어 매끈한 곡선으로
        for (i in 0 until n) {
            val tv = target[i]
            shown[i] = if (tv > shown[i]) shown[i] + (tv - shown[i]) * 0.35f else shown[i] * 0.92f + tv * 0.08f
        }
        for (i in 0 until n) {
            val a = shown[(i - 1).coerceAtLeast(0)]
            val b = shown[(i + 1).coerceAtMost(n - 1)]
            smooth[i] = (a + shown[i] * 2f + b) / 4f
        }
        val w = size.width
        val cy = size.height / 2f
        val maxH = size.height / 2f * 0.92f
        val time = System.nanoTime() / 1_000_000_000.0
        val step = 3f
        // 세 가닥: 굵고 진한 선 + 옅은 선 두 개가 서로 다른 박자로 흘러요
        val layers = arrayOf(
            floatArrayOf(1.00f, 2.2f, 1.6f, 1.0f, 2.2f),
            floatArrayOf(0.70f, 3.1f, -2.1f, 0.45f, 1.5f),
            floatArrayOf(0.45f, 1.6f, 2.7f, 0.25f, 1.2f)
        )
        for (L in layers) {
            val (scale, freq, speed, alpha, strokeDp) = L.toList()
            val path = androidx.compose.ui.graphics.Path()
            var x = 0f
            while (x <= w) {
                val u = x / w
                // 양 끝은 0으로 모이고 가운데가 가장 크게 (파동이 화면 안에서 숨 쉬듯)
                val env = kotlin.math.sin(Math.PI * u).toFloat().let { it * it }
                val fi = u * (n - 1)
                val i0 = fi.toInt().coerceIn(0, n - 1)
                val i1 = (i0 + 1).coerceAtMost(n - 1)
                val lv = smooth[i0] + (smooth[i1] - smooth[i0]) * (fi - i0)
                val amp = maxH * (0.03f + 0.97f * lv) * env * scale
                val y = cy + amp * kotlin.math.sin(u * freq * 2.0 * Math.PI + time * speed).toFloat()
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            drawPath(
                path, accent.copy(alpha = alpha),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeDp.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}
