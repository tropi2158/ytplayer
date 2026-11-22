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
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(cs.primary, cs.primary.copy(alpha = 0.45f), cs.secondary))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Favorite, null, Modifier.fillMaxSize(0.4f), tint = Color.White) }

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
    onMix: () -> Unit
) {
    var tagFilter by remember { mutableStateOf("") }
    val pick = remember(state.playlists) { state.todayPick() }
    val tags = state.tags
    LaunchedEffect(tags) { if (tagFilter.isNotEmpty() && tagFilter !in tags) tagFilter = "" }
    val shown = if (tagFilter.isEmpty()) state.playlists else state.playlists.filter { it.tag == tagFilter }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        // ----- 머리글 -----
        Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Playlist", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (!state.online) {
                GlassSurface(shape = CircleShape, tint = MaterialTheme.colorScheme.error) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudOff, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("오프라인", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { state.panel = Panel.SEARCH }) { Icon(Icons.Default.Search, "유튜브에서 찾기 (Ctrl+F)") }
            IconButton(onClick = { state.panel = Panel.STATS }) { Icon(Icons.Default.BarChart, "청취 통계") }
            IconButton(onClick = { state.panel = Panel.SETTINGS }) { Icon(Icons.Default.Settings, "설정") }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(176.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ----- 가운데 메시지 + 오늘의 추천 -----
            item(span = { GridItemSpan(maxLineSpan) }, key = "__msg__") {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    RandomMessage(Modifier.widthIn(max = 640.dp).riseIn(a, 40, 3))
                    if (pick != null) {
                        Spacer(Modifier.height(22.dp))
                        GlassSurface(
                            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().riseIn(a, 120, 2),
                            shape = RoundedCornerShape(22.dp), strong = true,
                            onClick = { state.playTrackIn(pick.pid, pick.track.url) }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Thumb(pick.track.thumb, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("오늘의 추천 · ${pick.reason}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(pick.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(pick.track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.PlayArrow, "재생")
                            }
                        }
                    }
                }
            }

            // ----- 최근 들은 곡 -----
            if (state.recent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "__recent__") {
                    val rs = rememberLazyListState()
                    Column {
                        Text("최근 들은 곡", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                        LazyRow(state = rs, modifier = Modifier.wheelToHorizontal(rs, scope), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.recent, key = { it.track.url }) { r ->
                                GlassSurface(
                                    modifier = Modifier.width(140.dp).animateItem(),
                                    shape = RoundedCornerShape(18.dp), strong = true,
                                    onClick = { state.playTrackIn(r.pid, r.track.url) }
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Thumb(r.track.thumb, Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(12.dp)))
                                        Spacer(Modifier.height(6.dp))
                                        Text(r.track.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ----- 내 플레이리스트 머리 -----
            item(span = { GridItemSpan(maxLineSpan) }, key = "__plhead__") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("내 플레이리스트", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${state.playlists.count { it.id != FAV_ID }}개",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        GlassChip("섞어 듣기", icon = Icons.Default.Shuffle, onClick = onMix)
                        Spacer(Modifier.width(8.dp))
                        GlassChip("플레이리스트 추가", icon = Icons.Default.Add, selected = true, onClick = onAdd)
                    }
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassChip("전체", selected = tagFilter.isEmpty()) { tagFilter = "" }
                            tags.forEach { t -> GlassChip("#$t", selected = tagFilter == t) { tagFilter = if (tagFilter == t) "" else t } }
                        }
                    }
                }
            }

            // ----- 카드 -----
            items(shown, key = { it.id }) { p ->
                val name = if (p.id == FAV_ID) "좋아요" else p.name
                androidx.compose.foundation.ContextMenuArea(items = { onPlaylistMenu(p.id) }) {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        shape = RoundedCornerShape(26.dp), strong = true, pressScale = 0.95f,
                        onClick = { state.openId = p.id }
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            PlaylistCover(p, Modifier.sharedEl("cover-${p.id}", s, a).fillMaxWidth().aspectRatio(1f))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.sharedBox("title-${p.id}", s, a)
                            )
                            val prog = state.offlineProgress[p.id]
                            Text(
                                when {
                                    prog != null -> "저장 중 ${prog.first}/${prog.second}"
                                    p.tag.isNotBlank() -> "${p.tracks.size}곡 · #${p.tag}"
                                    else -> "${p.tracks.size}곡"
                                },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!state.hasUserPlaylists) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "__empty__") {
                    Text(
                        "플레이리스트가 없어요. '플레이리스트 추가'에 유튜브 링크를 넣거나, 설정 → 백업 → 가져오기로 폰의 목록을 불러오세요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
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

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.openId = null }) { Icon(Icons.Default.ArrowBack, "뒤로 (Esc)") }
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlaylistCover(pl, Modifier.sharedEl("cover-${pl.id}", s, a).size(168.dp), 26.dp)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        pl.id == FAV_ID -> "내가 좋아요한 곡"
                        pl.url.isBlank() -> "직접 만든 플레이리스트"
                        else -> "YouTube 플레이리스트"
                    },
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.riseIn(a, 80)
                )
                Text(
                    name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.sharedBox("title-${pl.id}", s, a)
                )
                Text(
                    "${pl.tracks.size}곡 · 총 ${totalLabel(total)}" + if (pl.tag.isNotBlank()) " · #${pl.tag}" else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.riseIn(a, 120)
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.riseIn(a, 140, 2)) {
                    GlassSurface(
                        modifier = Modifier.height(46.dp).widthIn(min = 140.dp), shape = RoundedCornerShape(23.dp),
                        tint = MaterialTheme.colorScheme.primary, onClick = { state.playAllIn(pl.id) }
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("전체 재생", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    GlassSurface(
                        modifier = Modifier.height(46.dp).widthIn(min = 140.dp), shape = RoundedCornerShape(23.dp),
                        strong = true, onClick = { state.shuffleIn(pl.id) }
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shuffle, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("셔플 재생", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Box {
                        GlassSurface(modifier = Modifier.size(46.dp), shape = CircleShape, strong = true, onClick = { sortOpen = true }) {
                            Icon(Icons.Default.Sort, "정렬")
                        }
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
                        GlassSurface(modifier = Modifier.size(46.dp), shape = CircleShape, strong = true, onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreHoriz, "메뉴")
                        }
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
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { if (prog.second > 0) prog.first / prog.second.toFloat() else 0f }, modifier = Modifier.weight(1f).height(4.dp))
                Spacer(Modifier.width(8.dp))
                Text("오프라인 저장 ${prog.first}/${prog.second}", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { state.cancelOffline(pl.id) }) { Text("멈추기") }
            }
        }
        state.error?.let { ErrorCard(it, state.retry, state::clearError) }

        Spacer(Modifier.height(14.dp))
        when {
            pl.tracks.isEmpty() && state.loading -> SkeletonList(Modifier.weight(1f))
            pl.tracks.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        pl.id == FAV_ID -> "좋아요한 곡이 없어요. 곡에 마우스를 올려 ♥를 눌러보세요"
                        pl.url.isBlank() -> "빈 플레이리스트예요. 다른 목록의 곡을 오른쪽 클릭해서 담아보세요"
                        else -> "곡이 없어요. 메뉴에서 새로고침을 눌러보세요"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
            else -> {
                val shown = remember(pl.tracks, query, sort) {
                    val base = pl.tracks.withIndex().filter {
                        query.isBlank() || it.value.title.contains(query, ignoreCase = true) || it.value.artist.contains(query, ignoreCase = true)
                    }
                    when (sort) {
                        1 -> base.sortedBy { it.value.title.lowercase() }
                        2 -> base.sortedBy { it.value.durationSec }
                        3 -> base.reversed()
                        else -> base
                    }
                }
                val listState = rememberLazyListState()
                LaunchedEffect(sort) { listState.scrollToItem(0) }
                val curPos = if (state.playingPid == pl.id) shown.indexOfFirst { it.value.url == state.playingUrl } else -1

                GlassSurface(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp).riseIn(a, 160, 3),
                    shape = RoundedCornerShape(26.dp), strong = true
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            TextField(
                                value = query, onValueChange = { query = it },
                                placeholder = { Text("이 목록에서 찾기") }, singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            if (curPos >= 0) {
                                GlassChip("현재 곡", icon = Icons.Default.MyLocation) {
                                    scope.launch { listState.animateScrollToItem((curPos - 3).coerceAtLeast(0)) }
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 10.dp), contentPadding = PaddingValues(8.dp)) {
                                itemsIndexed(shown, key = { _, it -> it.index }) { n, (i, t) ->
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
                            VerticalScrollbar(
                                rememberScrollbarAdapter(listState),
                                Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
