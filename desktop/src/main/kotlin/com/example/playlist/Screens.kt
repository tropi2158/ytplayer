@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)

package com.example.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

// ======================= 화면 전환 =======================

val SharedSpring = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.78f, stiffness = 260f, visibilityThreshold = Rect.VisibilityThreshold)
}

@Composable
fun Modifier.sharedEl(key: String, s: SharedTransitionScope?, a: AnimatedVisibilityScope?): Modifier {
    if (s == null || a == null) return this
    return with(s) { this@sharedEl.sharedElement(rememberSharedContentState(key), a, boundsTransform = SharedSpring) }
}

@Composable
fun Modifier.sharedBox(key: String, s: SharedTransitionScope?, a: AnimatedVisibilityScope?): Modifier {
    if (s == null || a == null) return this
    return with(s) { this@sharedBox.sharedBounds(rememberSharedContentState(key), a, boundsTransform = SharedSpring) }
}

fun Modifier.riseIn(a: AnimatedVisibilityScope?, delay: Int = 0, divisor: Int = 5): Modifier {
    if (a == null) return this
    return with(a) {
        this@riseIn.animateEnterExit(
            enter = slideInVertically(
                spring(dampingRatio = 0.72f, stiffness = 240f, visibilityThreshold = IntOffset.VisibilityThreshold)
            ) { it / divisor } + fadeIn(tween(320, delayMillis = delay)),
            exit = fadeOut(tween(140))
        )
    }
}

@Composable
fun PlaylistCover(p: Playlist, modifier: Modifier, corner: Dp = 18.dp) {
    val shape = RoundedCornerShape(corner)
    val cs = MaterialTheme.colorScheme
    Box(modifier.clip(shape).background(cs.surfaceVariant)) {
        when {
            p.id == FAV_ID -> Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFF6B8B), Color(0xFFE0457B), Color(0xFF7A3CF0)))),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))))
                Icon(Icons.Default.Favorite, null, Modifier.fillMaxSize(0.34f), tint = Color.White)
            }

            p.tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LibraryMusic, null, Modifier.fillMaxSize(0.35f), tint = cs.onSurfaceVariant)
            }

            p.tracks.size >= 4 -> Column(Modifier.fillMaxSize()) {
                for (r in 0 until 2) {
                    Row(Modifier.weight(1f)) {
                        for (c in 0 until 2) {
                            AsyncImage(
                                model = p.tracks[r * 2 + c].thumb, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }

            else -> AsyncImage(
                model = p.tracks.first().thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ======================= 홈 =======================

private val MESSAGES = listOf(
    "오늘은 어떤 노래로\n하루를 채워볼까요?",
    "좋아하는 노래 한 곡이면\n충분한 날도 있어요",
    "이어폰 꽂고\n잠깐 쉬어가요",
    "기분이 노래를 고르고,\n노래가 기분을 바꿔요",
    "오래된 플레이리스트에서\n보물을 찾아보세요",
    "셔플이 오늘의\n운세예요",
    "지금 이 순간의\n배경음악은 뭘까요?",
    "한 곡만 더 듣고 잘게요…\n아마도",
    "가사가 마음을 대신\n말해줄 때가 있죠",
    "집중이 필요할 땐\n가사 없는 곡도 좋아요",
    "반복 재생은\n사랑이에요",
    "오늘 하루도\n수고했어요",
    "볼륨은 조금 낮게,\n마음은 조금 크게",
    "좋은 노래는 나눌수록\n더 좋아져요"
)

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..10 -> "좋은 아침이에요"
    in 11..16 -> "좋은 오후예요"
    in 17..21 -> "좋은 저녁이에요"
    else -> "늦은 밤이네요"
}

@Composable
private fun RandomMessage(modifier: Modifier = Modifier) {
    var idx by remember { mutableIntStateOf(Random.nextInt(MESSAGES.size)) }
    val next = { idx = (idx + 1 + Random.nextInt(MESSAGES.size - 1)) % MESSAGES.size }
    LaunchedEffect(Unit) {
        while (true) {
            delay(12_000)
            next()
        }
    }
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { next() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(greeting(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = idx,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 180f, visibilityThreshold = IntOffset.VisibilityThreshold)) { it / 2 } +
                    fadeIn(tween(450))) togetherWith (slideOutVertically(tween(280)) { -it / 2 } + fadeOut(tween(220)))
            },
            label = "msg"
        ) { i ->
            Text(
                MESSAGES[i], style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun HomeScreen(
    state: AppState,
    s: SharedTransitionScope?,
    a: AnimatedVisibilityScope?,
    onPlaylistMenu: (String) -> List<ContextMenuItem>,
    onAdd: () -> Unit,
    onMix: () -> Unit,
    trackMenu: (Track, String, Int) -> List<ContextMenuItem> = { _, _, _ -> emptyList() }
) {
    var tagFilter by remember { mutableStateOf("") }
    val tags = state.tags
    LaunchedEffect(tags) { if (tagFilter.isNotEmpty() && tagFilter !in tags) tagFilter = "" }
    LaunchedEffect(state.playingUrl, state.online) {
        delay(1200)
        state.loadRecs()
    }
    val shown = if (tagFilter.isEmpty()) state.playlists else state.playlists.filter { it.tag == tagFilter }
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    var menu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 22.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            // ----- 머리글: 인사 + 큰 제목 + 한 줄 메시지 -----
            item(span = { GridItemSpan(maxLineSpan) }, key = "__header__") {
                Column(Modifier.riseIn(a, 0)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.online) greeting() else "오프라인 · 저장한 곡만 재생돼요",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.online) muted else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { state.panel = Panel.SEARCH }) { Icon(Icons.Default.Search, "유튜브에서 찾기 (Ctrl+F)") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "더보기") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("새 플레이리스트") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { menu = false; onAdd() })
                                DropdownMenuItem(text = { Text("모두 섞어 듣기") }, leadingIcon = { Icon(Icons.Default.Shuffle, null) }, onClick = { menu = false; onMix() })
                                DropdownMenuItem(text = { Text("청취 통계") }, leadingIcon = { Icon(Icons.Default.BarChart, null) }, onClick = { menu = false; state.panel = Panel.STATS })
                                DropdownMenuItem(text = { Text("설정") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { menu = false; state.panel = Panel.SETTINGS })
                            }
                        }
                    }
                    Text("Playlist", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    QuietMessage(muted)
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TagPill("전체", tagFilter.isEmpty()) { tagFilter = "" }
                            tags.forEach { t -> TagPill("#$t", tagFilter == t) { tagFilter = if (tagFilter == t) "" else t } }
                        }
                    }
                }
            }

            // ----- 플레이리스트: 커버 + 이름만 -----
            items(shown, key = { it.id }) { p ->
                androidx.compose.foundation.ContextMenuArea(items = { onPlaylistMenu(p.id) }) {
                    PlaylistTile(state, p, s, a, Modifier.animateItem()) { state.openId = p.id }
                }
            }

            // ----- 새 플레이리스트 -----
            item(key = "__add__") {
                HoverPress(onClick = onAdd) { hovered ->
                    Column {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = if (hovered) 0.10f else 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Add, null, Modifier.size(40.dp), tint = muted) }
                        Spacer(Modifier.height(10.dp))
                        Text("새 플레이리스트", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = muted)
                    }
                }
            }

            if (state.loading) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "__loading__") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            state.error?.let { msg ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "__err__") { ErrorCard(msg, state.retry, state::clearError) }
            }

            // ----- 비슷한 곡 -----
            if (state.recs.isNotEmpty() || state.recsLoading) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "__recs__") {
                    RecsRow(state, trackMenu, Modifier.riseIn(a, 120, 3))
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(gridState), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp))
    }
}

/** 마우스를 올리면 살짝 커지고, 누르면 살짝 작아지는 공통 틀 */
@Composable
fun HoverPress(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable (hovered: Boolean) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        when { pressed -> 0.96f; hovered -> 1.02f; else -> 1f },
        spring(dampingRatio = 0.55f, stiffness = 500f), label = "hp"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .hoverable(src)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) { content(hovered) }
}

@Composable
private fun PlaylistTile(state: AppState, p: Playlist, s: SharedTransitionScope?, a: AnimatedVisibilityScope?, modifier: Modifier, onClick: () -> Unit) {
    val name = if (p.id == FAV_ID) "좋아요" else p.name
    val prog = state.offlineProgress[p.id]
    HoverPress(modifier, onClick = onClick) { hovered ->
        Column {
            Box {
                PlaylistCover(
                    p,
                    Modifier.sharedEl("cover-${p.id}", s, a).fillMaxWidth().aspectRatio(1f)
                        .shadow(if (hovered) 18.dp else 10.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black),
                    12.dp
                )
                // 마우스를 올리면 바로 재생 버튼
                androidx.compose.animation.AnimatedVisibility(
                    visible = hovered && p.tracks.isNotEmpty(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut()
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                            .clickable { state.playAllIn(p.id) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.PlayArrow, "바로 재생", tint = Color.White) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.sharedBox("title-${p.id}", s, a)
            )
            Text(
                when {
                    prog != null -> "저장 중 ${prog.first}/${prog.second}"
                    p.tag.isNotBlank() -> "${p.tracks.size}곡 · #${p.tag}"
                    else -> "${p.tracks.size}곡"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun QuietMessage(color: Color) {
    var idx by remember { mutableIntStateOf(Random.nextInt(MESSAGES.size)) }
    val next = { idx = (idx + 1 + Random.nextInt(MESSAGES.size - 1)) % MESSAGES.size }
    LaunchedEffect(Unit) {
        while (true) {
            delay(12_000)
            next()
        }
    }
    androidx.compose.animation.Crossfade(targetState = idx, animationSpec = tween(500), label = "quietMsg") { i ->
        Text(
            MESSAGES[i].replace("\n", " "),
            style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1,
            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { next() }
        )
    }
}

@Composable
private fun TagPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        modifier = Modifier.clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

/** 비슷한 곡: 누르면 그 곡으로 라디오, 오른쪽 클릭하면 메뉴 */
@Composable
private fun RecsRow(state: AppState, trackMenu: (Track, String, Int) -> List<ContextMenuItem>, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val rs = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("비슷한 곡", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val cur = state.engine.current?.track
                Text(
                    if (cur != null) "‘${TitleClean.title(cur.title, cur.artist).take(30)}’ 와(과) 어울리는 곡" else "최근 들은 곡과 어울리는 곡",
                    style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1
                )
            }
            IconButton(onClick = { state.loadRecs(force = true) }) { Icon(Icons.Default.Refresh, "다시 추천", tint = muted) }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(state = rs, modifier = Modifier.wheelToHorizontal(rs, scope), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (state.recs.isEmpty()) {
                items(7) {
                    Box(Modifier.width(170.dp).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)))
                }
            } else {
                items(state.recs, key = { it.url }) { t ->
                    androidx.compose.foundation.ContextMenuArea(items = { trackMenu(t, SEARCH_PID, -1) }) {
                        HoverPress(Modifier.width(170.dp).animateItem(), onClick = { state.startRadio(t) }) { hovered ->
                            Column {
                                Box {
                                    Thumb(t.thumb, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
                                    if (hovered) Box(
                                        Modifier.align(Alignment.BottomEnd).padding(8.dp).size(38.dp).clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Radio, "라디오 시작", Modifier.size(20.dp), tint = Color.White) }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(t.shownTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.shownArtist, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================= 플레이리스트 화면 =======================

private val SORTS = listOf("기본 순서", "제목순", "짧은 곡부터", "최근 추가된 순")

private fun totalLabel(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}시간 ${m}분" else "${m}분"
}

@Composable
fun PlaylistScreen(
    state: AppState,
    pl: Playlist,
    s: SharedTransitionScope?,
    a: AnimatedVisibilityScope?,
    trackMenu: (Track, String, Int) -> List<ContextMenuItem>,
    playlistMenu: () -> List<ContextMenuItem>
) {
    var sort by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val name = if (pl.id == FAV_ID) "좋아요" else pl.name
    val total = remember(pl.tracks) { pl.tracks.sumOf { it.durationSec } }
    val prog = state.offlineProgress[pl.id]
    val cacheVer = state.cacheVersion
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    val shown = remember(pl.tracks, query, sort) {
        val base = pl.tracks.withIndex().filter {
            query.isBlank() || it.value.title.contains(query, ignoreCase = true) || it.value.artist.contains(query, ignoreCase = true)
        }
        when (sort) {
            1 -> base.sortedBy { it.value.shownTitle.lowercase() }
            2 -> base.sortedBy { it.value.durationSec }
            3 -> base.reversed()
            else -> base
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(sort) { if (listState.firstVisibleItemIndex > 1) listState.scrollToItem(1) }
    val curPos = if (state.playingPid == pl.id) shown.indexOfFirst { it.value.url == state.playingUrl } else -1

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 24.dp)
        ) {
            // ----- 머리: 뒤로 / 커버 + 이름 + 버튼 -----
            item(key = "__head__") {
                Column {
                    IconButton(onClick = { state.openId = null }) { Icon(Icons.Default.ArrowBackIosNew, "뒤로 (Esc)", Modifier.size(20.dp)) }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        PlaylistCover(
                            pl,
                            Modifier.sharedEl("cover-${pl.id}", s, a).size(230.dp)
                                .shadow(24.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black),
                            14.dp
                        )
                        Spacer(Modifier.width(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.sharedBox("title-${pl.id}", s, a)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                buildString {
                                    append(when { pl.id == FAV_ID -> "좋아요한 곡"; pl.url.isBlank() -> "내가 만든 목록"; else -> "YouTube" })
                                    append(" · ${pl.tracks.size}곡")
                                    if (total > 0) append(" · ${totalLabel(total)}")
                                    if (pl.tag.isNotBlank()) append(" · #${pl.tag}")
                                },
                                style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.riseIn(a, 80)
                            )
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.riseIn(a, 120, 2)) {
                                if (pl.tracks.isNotEmpty()) {
                                    PillButton(Icons.Default.PlayArrow, "재생") { state.playAllIn(pl.id) }
                                    PillButton(Icons.Default.Shuffle, "셔플") { state.shuffleIn(pl.id) }
                                }
                                Box {
                                    IconButton(onClick = { sortOpen = true }) { Icon(Icons.Default.Sort, "정렬", tint = muted) }
                                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                        SORTS.forEachIndexed { i, label ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                trailingIcon = { if (sort == i) Icon(Icons.Default.Check, null) },
                                                onClick = { sort = i; sortOpen = false }
                                            )
                                        }
                                    }
                                }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreHoriz, "메뉴", tint = muted) }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        playlistMenu().forEach { item ->
                                            DropdownMenuItem(text = { Text(item.label) }, onClick = { menuOpen = false; item.onClick() })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (prog != null) {
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(progress = { if (prog.second > 0) prog.first / prog.second.toFloat() else 0f }, modifier = Modifier.weight(1f).height(3.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("저장 중 ${prog.first}/${prog.second}", style = MaterialTheme.typography.labelSmall, color = muted)
                            TextButton(onClick = { state.cancelOffline(pl.id) }) { Text("멈추기") }
                        }
                    }
                    state.error?.let { Spacer(Modifier.height(8.dp)); ErrorCard(it, state.retry, state::clearError) }
                    Spacer(Modifier.height(22.dp))
                    // ----- 찾기 + 현재 곡으로 -----
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.widthIn(max = 360.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = muted)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                if (query.isEmpty()) Text("이 목록에서 찾기", color = muted, style = MaterialTheme.typography.bodyMedium)
                                androidx.compose.foundation.text.BasicTextField(
                                    value = query, onValueChange = { query = it }, singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (query.isNotEmpty()) Icon(Icons.Default.Close, "지우기", Modifier.size(18.dp).clickable { query = "" }, tint = muted)
                        }
                        Spacer(Modifier.weight(1f))
                        if (curPos >= 0) {
                            TextButton(onClick = { scope.launch { listState.animateScrollToItem((curPos + 1 - 3).coerceAtLeast(0)) } }) {
                                Icon(Icons.Default.MyLocation, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("현재 곡")
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    Spacer(Modifier.height(6.dp))
                }
            }

            when {
                pl.tracks.isEmpty() && state.loading -> item(key = "__skel__") { SkeletonList(Modifier.fillMaxWidth().height(400.dp)) }
                pl.tracks.isEmpty() -> item(key = "__empty__") {
                    Text(
                        when {
                            pl.id == FAV_ID -> "좋아요한 곡이 없어요. 곡에 마우스를 올려 ♥를 눌러보세요"
                            pl.url.isBlank() -> "빈 플레이리스트예요. 다른 목록의 곡을 오른쪽 클릭해서 담아보세요"
                            else -> "곡이 없어요. ⋯ 메뉴에서 새로고침을 눌러보세요"
                        },
                        color = muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp)
                    )
                }
                else -> itemsIndexed(shown, key = { _, it -> it.index }) { n, (i, t) ->
                    val isCurrent = state.playingPid == pl.id && t.url == state.playingUrl
                    TrackRow(
                        number = n + 1,
                        t = t,
                        selected = isCurrent,
                        playing = isCurrent && state.engine.isPlaying,
                        saved = remember(t.url, cacheVer) { AudioCache.isPinned(t.url) },
                        favorite = state.isFavorite(t.url),
                        isNew = t.url in state.newUrls,
                        onClick = { state.playIn(pl.id, i) },
                        onPlayNext = { state.playNext(t, pl.id) },
                        onQueue = { state.addToQueue(t, pl.id) },
                        onFav = { state.toggleFavorite(t) },
                        menu = { trackMenu(t, pl.id, i) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp))
    }
}

/** 납작한 알약 버튼 (Apple Music의 재생/셔플) */
@Composable
fun PillButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    HoverPress(onClick = onClick) { hovered ->
        Row(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = if (hovered) 0.16f else 0.10f))
                .padding(horizontal = 26.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = AlbumAccent.color)
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold, color = AlbumAccent.color)
        }
    }
}
