@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.example.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.awt.Dimension
import java.io.File

fun main() {
    NewPipe.init(NewPipeDownloader(), Localization("ko", "KR"), ContentCountry("KR"))
    Fx.init()
    application {
        val state = remember { AppState() }
        var visible by remember { mutableStateOf(true) }
        val icon = painterResource("icon.png")
        setSingletonImageLoaderFactory { ctx ->
            ImageLoader.Builder(ctx)
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(AppDirs.local, "images").toOkioPath())
                        .maxSizeBytes(300L * 1024 * 1024)
                        .build()
                }
                .build()
        }
        LaunchedEffect(state.showWindowRequest) { if (state.showWindowRequest > 0) visible = true }

        fun quit() {
            state.shutdown()
            Fx.exit()
            exitApplication()
        }

        Tray(
            icon = icon,
            tooltip = state.engine.current?.track?.title?.let { "Playlist · $it" } ?: "Playlist",
            onAction = { visible = true },
            menu = {
                Item("열기", onClick = { visible = true })
                Item(if (state.engine.isPlaying) "일시정지" else "재생", onClick = { state.engine.togglePlay() })
                Item("다음 곡", onClick = { state.engine.next() })
                Item("이전 곡", onClick = { state.engine.previous() })
                Separator()
                Item("종료", onClick = { quit() })
            }
        )

        Window(
            onCloseRequest = { if (state.closeToTray) visible = false else quit() },
            visible = visible,
            title = "Playlist",
            icon = icon,
            state = rememberWindowState(width = 1240.dp, height = 840.dp),
            onKeyEvent = { handleKey(state, it) }
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(960, 660) }
            CompositionLocalProvider(LocalWindow provides window) {
                AppTheme(state) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppScreen(state)
                    }
                }
            }
        }
    }
}

/** 단축키 (글자 입력 중에는 입력창이 먼저 받아서 겹치지 않아요) */
private fun handleKey(state: AppState, ev: KeyEvent): Boolean {
    if (ev.type != KeyEventType.KeyDown) return false
    val e = state.engine
    return when {
        ev.key == Key.Spacebar -> { e.togglePlay(); true }
        ev.isCtrlPressed && ev.key == Key.DirectionRight -> { e.next(); true }
        ev.isCtrlPressed && ev.key == Key.DirectionLeft -> { e.previous(); true }
        ev.isCtrlPressed && ev.key == Key.F -> { state.panel = Panel.SEARCH; true }
        ev.key == Key.DirectionRight -> { e.seek((e.livePosition() + 5000).coerceAtMost(e.durationMs)); true }
        ev.key == Key.DirectionLeft -> { e.seek((e.livePosition() - 5000).coerceAtLeast(0)); true }
        ev.key == Key.DirectionUp -> { e.updateVolume(e.volume + 0.05f); true }
        ev.key == Key.DirectionDown -> { e.updateVolume(e.volume - 0.05f); true }
        ev.key == Key.M -> { e.toggleMute(); true }
        ev.key == Key.S -> { e.toggleShuffle(); true }
        ev.key == Key.R -> { e.cycleRepeat(); true }
        ev.key == Key.L -> { state.expandRequest++; true }
        ev.key == Key.Escape -> {
            when {
                state.panel != Panel.NONE -> state.panel = Panel.NONE
                state.expanded -> state.collapseRequest++
                state.openId != null -> state.openId = null
            }
            true
        }
        else -> false
    }
}

@Composable
fun AppScreen(state: AppState) {
    val e = state.engine
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { state.toasts.collect { snackbar.showSnackbar(it) } }

    var showAdd by remember { mutableStateOf(false) }
    var showNewEmpty by remember { mutableStateOf(false) }
    var newWithTrack by remember { mutableStateOf<Track?>(null) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var tagId by remember { mutableStateOf<String?>(null) }
    var showMix by remember { mutableStateOf(false) }
    val hasCurrent = e.current != null

    // ----- 오른쪽 클릭 메뉴 -----
    val trackMenu: (Track, String, Int) -> List<ContextMenuItem> = { t, pid, index ->
        val owner = state.playlist(pid)
        buildList {
            add(ContextMenuItem("다음에 재생") { state.playNext(t, pid) })
            add(ContextMenuItem("대기열에 추가") { state.addToQueue(t, pid) })
            add(ContextMenuItem(if (state.isFavorite(t.url)) "좋아요 취소" else "좋아요") { state.toggleFavorite(t) })
            add(ContextMenuItem("이 곡으로 라디오 시작") { state.startRadio(t) })
            // 직접 만든 목록 + 유튜브에서 가져온 목록 모두에 담을 수 있어요 (유튜브 원본은 그대로)
            state.playlists.filter { it.id != FAV_ID && it.id != pid }.forEach { p ->
                val has = p.tracks.any { it.url == t.url }
                add(ContextMenuItem("'${p.name}'에 담기" + if (has) " (이미 있음)" else "") { state.copyTo(t, p.id) })
            }
            add(ContextMenuItem("새 플레이리스트에 담기…") { newWithTrack = t })
            if (t.artist.isNotBlank()) add(ContextMenuItem("이 가수 다른 곡 찾기") { state.findArtist(t.artist) })
            add(ContextMenuItem("유튜브에서 열기") { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(t.url)) } })
            add(ContextMenuItem("링크 복사") {
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(t.url), null)
                state.toast("링크를 복사했어요")
            })
            if (owner != null && index >= 0) {
                add(ContextMenuItem("이 목록에서 빼기") { if (pid == FAV_ID) state.toggleFavorite(t) else state.removeFromPlaylist(pid, index) })
            }
        }
    }
    val playlistMenu: (String) -> List<ContextMenuItem> = { pid ->
        val p = state.playlist(pid)
        if (p == null) emptyList() else buildList {
            add(ContextMenuItem("열기") { state.openId = pid })
            if (p.tracks.isNotEmpty()) {
                add(ContextMenuItem("바로 재생") { state.playAllIn(pid) })
                add(ContextMenuItem("셔플 재생") { state.shuffleIn(pid) })
            }
            if (p.url.isNotBlank()) add(ContextMenuItem("새로고침") { state.refreshPlaylist(pid) })
            if (state.duplicateCount(pid) > 0) add(ContextMenuItem("중복 곡 정리 (${state.duplicateCount(pid)}곡)") { state.removeDuplicates(pid) })
            if (p.tracks.isNotEmpty()) {
                if (state.offlineProgress.containsKey(pid)) add(ContextMenuItem("오프라인 저장 멈추기") { state.cancelOffline(pid) })
                else {
                    add(ContextMenuItem("오프라인에 저장") { state.startOffline(pid) })
                    add(ContextMenuItem("저장한 곡 지우기") { state.removeOffline(pid) })
                }
            }
            if (pid != FAV_ID) {
                add(ContextMenuItem("맨 앞으로 옮기기") { state.movePlaylistToFront(pid) })
                add(ContextMenuItem(if (p.tag.isBlank()) "태그 정하기" else "태그 바꾸기 (#${p.tag})") { tagId = pid })
                add(ContextMenuItem("이름 바꾸기") { renameId = pid })
                add(ContextMenuItem("삭제") { deleteId = pid })
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val expand = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val panelSpring = spring<Float>(dampingRatio = 0.76f, stiffness = 240f)
        val settle: (Float) -> Unit = { v ->
            scope.launch {
                val target = when {
                    v < -1000f -> 1f
                    v > 1000f -> 0f
                    expand.value > 0.45f -> 1f
                    else -> 0f
                }
                expand.animateTo(target, panelSpring, initialVelocity = -v / heightPx)
            }
        }
        val openPlayer: () -> Unit = { if (hasCurrent) scope.launch { expand.animateTo(1f, panelSpring) } }
        val closePlayer: () -> Unit = { scope.launch { expand.animateTo(0f, panelSpring) } }
        LaunchedEffect(hasCurrent) { if (!hasCurrent) expand.snapTo(0f) }
        LaunchedEffect(state.expandRequest) {
            if (state.expandRequest > 0) {
                if (expand.targetValue > 0.5f) closePlayer() else openPlayer()
            }
        }
        LaunchedEffect(state.collapseRequest) { if (state.collapseRequest > 0) closePlayer() }
        LaunchedEffect(Unit) { snapshotFlow { expand.targetValue > 0.5f }.collect { state.expanded = it } }

        GlassBackground(state.bgColors, art = state.engine.current?.track?.thumb)

        Column(
            Modifier.fillMaxSize().graphicsLayer {
                val v = expand.value
                val sc = 1f - 0.05f * v
                scaleX = sc
                scaleY = sc
                alpha = 1f - 0.4f * v
            }
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = state.openId,
                        transitionSpec = {
                            val forward = targetState != null
                            (fadeIn(tween(320, delayMillis = 60)) + scaleIn(spring(0.8f, 260f), initialScale = if (forward) 0.95f else 1.03f)) togetherWith
                                (fadeOut(tween(200)) + scaleOut(tween(260), targetScale = if (forward) 1.03f else 0.95f))
                        },
                        label = "screen"
                    ) { id ->
                        val pl = state.playlist(id)
                        if (pl == null) {
                            HomeScreen(
                                state, this@SharedTransitionLayout, this@AnimatedContent,
                                onPlaylistMenu = playlistMenu,
                                onAdd = { showAdd = true },
                                onMix = { showMix = true },
                                trackMenu = trackMenu
                            )
                            if (id != null) LaunchedEffect(id) { state.openId = null }
                        } else {
                            PlaylistScreen(
                                state, pl, this@SharedTransitionLayout, this@AnimatedContent,
                                trackMenu = trackMenu,
                                playlistMenu = { playlistMenu(pl.id).filterNot { it.label == "열기" } }
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = hasCurrent,
                enter = slideInVertically(spring(0.7f, 300f)) { it } + fadeIn(),
                exit = slideOutVertically(tween(220)) { it } + fadeOut()
            ) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp, top = 4.dp)) {
                    PlayerBar(state, onExpand = openPlayer)
                }
            }
        }

        PlayerOverlay(state, expand, heightPx, settle, closePlayer)

        // ----- 오른쪽 패널 -----
        val panelOpen = state.panel != Panel.NONE
        AnimatedVisibility(visible = panelOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.panel = Panel.NONE }
            )
        }
        AnimatedVisibility(
            visible = panelOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(spring(0.82f, 320f)) { it } + fadeIn(),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(180))
        ) {
            var last by remember { mutableStateOf(Panel.SETTINGS) }
            if (state.panel != Panel.NONE) last = state.panel
            GlassSurface(
                Modifier.fillMaxHeight().width(460.dp).padding(12.dp),
                shape = RoundedCornerShape(28.dp), strong = true
            ) {
                AnimatedContent(targetState = last, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "panel") { p ->
                    SidePanelContent(state, p, trackMenu)
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (hasCurrent) 120.dp else 24.dp))

        if (!state.onboarded) OnboardingDialog { state.finishOnboarding() }
    }

    // ----- 대화상자 -----
    if (showAdd) AddPlaylistDialog(onDismiss = { showAdd = false }, onAdd = state::addPlaylist, onCreateEmpty = { showNewEmpty = true })
    if (showNewEmpty) NameDialog("새 플레이리스트", "", "만들기", { showNewEmpty = false }) { state.openId = state.createLocalPlaylist(it) }
    newWithTrack?.let { t -> NameDialog("새 플레이리스트", "", "만들고 곡 넣기", { newWithTrack = null }) { state.createLocalPlaylist(it, t) } }
    renameId?.let { rid -> state.playlist(rid)?.let { p -> NameDialog("이름 바꾸기", p.name, "확인", { renameId = null }) { state.renamePlaylist(rid, it) } } }
    tagId?.let { TagDialog(state, it) { tagId = null } }
    if (showMix) MixDialog(state) { showMix = false }
    deleteId?.let { did ->
        state.playlist(did)?.let { p ->
            AlertDialog(
                onDismissRequest = { deleteId = null },
                title = { Text("플레이리스트 삭제") },
                text = { Text("'${p.name}'을(를) 목록에서 삭제할까요? (유튜브의 원본은 그대로예요)") },
                confirmButton = { TextButton(onClick = { state.deletePlaylist(did); deleteId = null }) { Text("삭제") } },
                dismissButton = { TextButton(onClick = { deleteId = null }) { Text("취소") } }
            )
        }
    }
}
