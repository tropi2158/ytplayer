@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package com.example.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ======================= 공통 =======================

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

fun mb(bytes: Long): String =
    if (bytes >= 1024L * 1024L * 1024L) "%.1f GB".format(bytes / 1073741824.0)
    else "%.0f MB".format(bytes / 1048576.0)

/** 마우스 휠(세로)로 가로 목록 넘기기 */
fun Modifier.wheelToHorizontal(state: ScrollableState, scope: kotlinx.coroutines.CoroutineScope): Modifier =
    this.onPointerEvent(PointerEventType.Scroll) { e ->
        val d = e.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
        val amount = if (abs(d.x) > abs(d.y)) d.x else d.y
        scope.launch { state.scrollBy(amount * 70f) }
    }

/** 재생 중일 때만 매 프레임 바뀌는 시계 ('그리기'에서만 읽기) */
@Composable
fun rememberFrameClock(running: Boolean): State<Long> {
    val t = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (running) while (true) withFrameMillis { t.longValue = it }
    }
    return t
}

@Composable
fun rememberSlowClock(running: Boolean): State<Long> {
    val t = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (running) while (true) {
            t.longValue = nowMs()
            delay(250)
        }
    }
    return t
}

// ======================= 테마 =======================

val MONO = Color(0xFF808080)

val ACCENTS = listOf(
    "레드" to Color(0xFFE53935),
    "오렌지" to Color(0xFFFB8C00),
    "옐로" to Color(0xFFF9A825),
    "그린" to Color(0xFF43A047),
    "틸" to Color(0xFF00897B),
    "블루" to Color(0xFF1E88E5),
    "인디고" to Color(0xFF5C6BC0),
    "퍼플" to Color(0xFF8E24AA),
    "핑크" to Color(0xFFD81B60),
    "흑백" to MONO
)

private fun monoScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme(
        primary = Color.White, onPrimary = Color.Black,
        primaryContainer = Color(0xFF2B2B2B), onPrimaryContainer = Color.White,
        secondary = Color(0xFFBDBDBD), onSecondary = Color.Black,
        background = Color.Black, onBackground = Color.White,
        surface = Color.Black, onSurface = Color.White,
        surfaceVariant = Color(0xFF1E1E1E), onSurfaceVariant = Color(0xFFB5B5B5),
        outline = Color(0xFF777777)
    ) else lightColorScheme(
        primary = Color.Black, onPrimary = Color.White,
        primaryContainer = Color(0xFFE6E6E6), onPrimaryContainer = Color.Black,
        secondary = Color(0xFF555555), onSecondary = Color.White,
        background = Color.White, onBackground = Color.Black,
        surface = Color.White, onSurface = Color.Black,
        surfaceVariant = Color(0xFFF0F0F0), onSurfaceVariant = Color(0xFF555555),
        outline = Color(0xFF888888)
    )

private fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    val c = when {
        !dark && seed.luminance() > 0.5f -> lerp(seed, Color.Black, 0.35f)
        dark && seed.luminance() < 0.08f -> lerp(seed, Color.White, 0.3f)
        else -> seed
    }
    return if (dark) darkColorScheme(
        primary = lerp(c, Color.White, 0.3f), onPrimary = Color(0xFF101010),
        primaryContainer = lerp(c, Color.Black, 0.55f), onPrimaryContainer = Color.White,
        secondary = lerp(c, Color.White, 0.3f),
        background = Color(0xFF111113), surface = Color(0xFF111113),
        surfaceVariant = Color(0xFF26262B), onSurfaceVariant = Color(0xFFC4C4CC)
    ) else lightColorScheme(
        primary = c, onPrimary = Color.White,
        primaryContainer = lerp(c, Color.White, 0.82f), onPrimaryContainer = lerp(c, Color.Black, 0.6f),
        secondary = c,
        background = Color(0xFFFAF9FB), surface = Color(0xFFFAF9FB),
        surfaceVariant = lerp(c, Color.White, 0.9f), onSurfaceVariant = Color(0xFF55555C)
    )
}

fun isNightHour(h: Int, start: Int, end: Int): Boolean = when {
    start == end -> false
    start < end -> h in start until end
    else -> h >= start || h < end
}

val LocalGlass = compositionLocalOf { true }

@Composable
fun AppTheme(state: AppState, content: @Composable () -> Unit) {
    val hour by produceState(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        while (true) {
            delay(60_000)
            value = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }
    val dark = when (state.themeMode) {
        1 -> false
        2 -> true
        3 -> isNightHour(hour, state.nightStart, state.nightEnd)
        else -> isSystemInDarkTheme()
    }
    val accent = ACCENTS[state.accent.coerceIn(0, ACCENTS.lastIndex)].second
    val mono = accent == MONO
    val art = state.artColor
    val seed = if (state.useArtColor && art != null && !mono) art else accent
    val scheme = if (mono) monoScheme(dark) else seedScheme(seed, dark)
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalGlass provides state.glass,
        LocalDensity provides Density(base.density, base.fontScale * state.fontScale)
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

// ======================= 유리 =======================

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color? = null,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    pressScale: Float = 0.96f,
    content: @Composable BoxScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val dark = cs.background.luminance() < 0.5f
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val clickable = onClick != null || onLongClick != null
    val lift = if (hovered && clickable) 0.06f else 0f

    val (top, bot) = when {
        !glass && tint != null -> cs.primaryContainer to cs.primaryContainer
        !glass && strong -> cs.surface to cs.surface
        !glass -> cs.surfaceVariant to cs.surfaceVariant
        tint != null -> tint.copy(alpha = 0.40f + lift) to tint.copy(alpha = 0.22f + lift)
        strong && dark -> cs.surface.copy(alpha = 0.78f) to cs.surface.copy(alpha = 0.66f)
        strong -> Color.White.copy(alpha = 0.86f) to Color.White.copy(alpha = 0.74f)
        dark -> Color.White.copy(alpha = 0.15f + lift) to Color.White.copy(alpha = 0.07f + lift)
        else -> Color.White.copy(alpha = 0.72f + lift) to Color.White.copy(alpha = 0.48f + lift)
    }
    val aTop = animateColorAsState(top, tween(220), label = "gt")
    val aBot = animateColorAsState(bot, tween(220), label = "gb")
    val border: Brush = remember(glass, dark) {
        if (glass) Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.16f else 0.55f),
                Color.White.copy(alpha = if (dark) 0.02f else 0.08f),
                Color.White.copy(alpha = 0f)
            )
        ) else SolidColor(Color.Transparent)
    }
    val scale = animateFloatAsState(
        if (pressed && clickable) pressScale else 1f,
        spring(dampingRatio = 0.45f, stiffness = 520f), label = "press"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(shape)
            .drawBehind { drawRect(Brush.verticalGradient(listOf(aTop.value, aBot.value))) }
            .then(if (glass) Modifier.border(0.7.dp, border, shape) else Modifier)
            .then(
                if (clickable) Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .combinedClickable(
                        interactionSource = source,
                        indication = LocalIndication.current,
                        onLongClick = onLongClick,
                        onClick = { onClick?.invoke() }
                    )
                else Modifier
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** 몽환적인 커버 색 그라데이션 배경 (그리기 단계에서만 움직임) */
@Composable
fun GlassBackground(colors: List<Color>, modifier: Modifier = Modifier, deep: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val dark = cs.background.luminance() < 0.5f
    val base = if (dark) Color(0xFF050507) else cs.background
    val dream = Color(0xFF7B6CF6)
    val c0 = animateColorAsState(colors.getOrElse(0) { cs.primary }, tween(1800), label = "bg0")
    val c1 = animateColorAsState(colors.getOrElse(1) { cs.secondary }, tween(1800), label = "bg1")
    val c2 = animateColorAsState(colors.getOrElse(2) { cs.primary }, tween(1800), label = "bg2")
    val drift = rememberInfiniteTransition(label = "drift")
    val phase = drift.animateFloat(
        0f, (2.0 * PI).toFloat(),
        infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "phase"
    )
    val k = when {
        deep && dark -> 1.35f
        dark -> 1f
        deep -> 0.7f
        else -> 0.55f
    }
    Box(
        modifier.fillMaxSize().drawBehind {
            drawRect(base)
            if (!glass) return@drawBehind
            val w = size.width
            val h = size.height
            val m = size.maxDimension
            val ph = phase.value
            val mono = colors.isNotEmpty() && colors.all { abs(it.red - it.green) < 0.02f && abs(it.green - it.blue) < 0.02f }
            fun blob(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
                val a = (alpha * k).coerceIn(0f, 1f)
                drawRect(
                    Brush.radialGradient(
                        0f to color.copy(alpha = a),
                        0.45f to color.copy(alpha = a * 0.38f),
                        1f to Color.Transparent,
                        center = Offset(cx, cy),
                        radius = r
                    )
                )
            }
            val main = c0.value
            val soft = if (mono) c2.value else lerp(c2.value, dream, 0.35f)
            val haze = lerp(main, Color.White, 0.25f)
            blob(main, w * (0.22f + 0.14f * sin(ph)), h * (0.14f + 0.08f * cos(ph)), m * 0.62f, 0.42f)
            blob(soft, w * (0.80f + 0.12f * cos(ph * 2f)), h * (0.30f + 0.10f * sin(ph)), m * 0.48f, 0.30f)
            blob(c1.value, w * (0.35f + 0.18f * sin(ph + 1f)), h * (0.52f + 0.09f * cos(ph * 2f)), m * 0.50f, 0.22f)
            blob(haze, w * (0.60f + 0.05f * cos(ph + 2f)), h * (0.08f + 0.03f * sin(ph * 2f)), m * 0.32f, 0.16f)
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to base.copy(alpha = 0.35f),
                    1f to base.copy(alpha = if (deep) 0.7f else if (dark) 0.92f else 0.6f)
                )
            )
        }
    )
}

// ======================= 작은 부품 =======================

@Composable
fun GlassChip(label: String, selected: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    GlassSurface(shape = CircleShape, tint = if (selected) MaterialTheme.colorScheme.primary else null, onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, maxLines = 1, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
fun PlayPauseIcon(playing: Boolean, size: Dp) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (scaleIn(initialScale = 0.5f, animationSpec = spring(0.6f, 600f)) + fadeIn(tween(160))) togetherWith
                (scaleOut(targetScale = 0.5f, animationSpec = tween(140)) + fadeOut(tween(140)))
        },
        label = "pp"
    ) { p ->
        Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, if (p) "일시정지" else "재생", Modifier.size(size))
    }
}

@Composable
fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ToolButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) { Icon(icon, label, tint = color) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
fun SkeletonList(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.10f, 0.25f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    val c = MaterialTheme.colorScheme.onSurface.copy(alpha = a)
    Column(modifier) {
        repeat(8) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.3f).height(10.dp).clip(CircleShape).background(c))
                }
            }
        }
    }
}

@Composable
fun ErrorCard(message: String, onRetry: (() -> Unit)?, onDismiss: () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        tint = MaterialTheme.colorScheme.error
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onRetry != null) TextButton(onClick = onRetry) { Text("다시 시도") }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "닫기", Modifier.size(18.dp)) }
        }
    }
}

/**
 * 곡 한 줄 (PC용): 누르면 재생, 마우스를 올리면 '다음에 재생 / 대기열 / 좋아요' 버튼, 오른쪽 클릭 메뉴
 */
@Composable
fun TrackRow(
    number: Int,
    t: Track,
    selected: Boolean,
    playing: Boolean,
    saved: Boolean,
    favorite: Boolean,
    isNew: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onFav: () -> Unit,
    menu: () -> List<ContextMenuItem>,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val primary = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(
        when {
            selected -> primary.copy(alpha = 0.22f)
            hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
            else -> Color.Transparent
        },
        tween(160), label = "rowBg"
    )
    ContextMenuArea(items = menu) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .combinedClickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                if (playing) Icon(Icons.Default.GraphicEq, null, Modifier.size(18.dp), tint = primary)
                else Text(
                    "$number",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AsyncImage(
                model = t.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) primary else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNew) {
                        Text(
                            "NEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(primary).padding(horizontal = 4.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (hovered) {
                IconButton(onClick = onPlayNext) { Icon(Icons.Default.SkipNext, "다음에 재생", Modifier.size(20.dp)) }
                IconButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, "대기열에 추가", Modifier.size(20.dp)) }
            }
            if (hovered || favorite) {
                IconButton(onClick = onFav) {
                    Icon(
                        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "좋아요",
                        Modifier.size(20.dp), tint = if (favorite) primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (saved) {
                Icon(Icons.Default.DownloadDone, "저장됨", Modifier.size(16.dp), tint = primary)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                fmtTime(t.durationSec * 1000), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp)
            )
        }
    }
}

/** 진행바(그리기 단계에서만 위치 계산) */
@Composable
fun ThinProgress(state: AppState, modifier: Modifier) {
    val e = state.engine
    val clock = rememberFrameClock(e.isPlaying)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val barColor = MaterialTheme.colorScheme.primary
    Box(
        modifier.drawBehind {
            clock.value
            val d = e.durationMs
            val f = if (d > 0) (e.livePosition().toFloat() / d).coerceIn(0f, 1f) else 0f
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(trackColor, cornerRadius = r)
            drawRoundRect(barColor, size = Size(size.width * f, size.height), cornerRadius = r)
        }
    )
}

@Composable
fun Thumb(url: String?, modifier: Modifier) {
    AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.alpha(1f))
}
