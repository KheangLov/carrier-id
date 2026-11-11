package com.carrierid.app.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrierid.app.ui.theme.One
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Space screens should leave at the bottom so their last rows can scroll above the floating bar. */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

private val BarHeight = 64.dp
private val BarPad = 5.dp
private val TabWidth = 92.dp      // each tab's slot; the capsule hugs its tabs
private val BarBottomGap = 12.dp  // space between the capsule and the gesture bar

/**
 * Screen content with a floating "liquid glass" tab bar on top (inspired by Rien).
 *
 * The content is recorded into a GraphicsLayer; the bar draws that same layer
 * behind itself, blurred, so whatever scrolls under the bar shows through as
 * frosted glass. Real blur needs Android 12+; older phones get a solid frosted
 * capsule instead.
 */
@Composable
fun GlassScaffold(
    selected: Int,
    tabs: List<TabItem>,
    onSelect: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val backdrop = rememberGraphicsLayer()
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val space: Dp = BarHeight + BarBottomGap + navInset + 12.dp

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .onGloballyPositioned { contentOrigin = it.positionInRoot() }
                .drawWithContent {
                    backdrop.record { this@drawWithContent.drawContent() }
                    drawLayer(backdrop)
                }
        ) {
            CompositionLocalProvider(LocalBottomBarSpace provides space) { content() }
        }

        GlassTabBar(
            backdrop = backdrop,
            contentOrigin = contentOrigin,
            selected = selected,
            tabs = tabs,
            onSelect = onSelect,
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = BarBottomGap),
        )
    }
}

@Composable
private fun GlassTabBar(
    backdrop: GraphicsLayer,
    contentOrigin: Offset,
    selected: Int,
    tabs: List<TabItem>,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    val c = One.colors
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val barShape = RoundedCornerShape(BarHeight / 2)
    val lensShape = RoundedCornerShape((BarHeight - BarPad * 2) / 2)

    // Glass colours (same recipe as Rien's Liquid Glass: translucent tint + bright edge)
    val tint = if (c.dark) Color(0xFF1E293B).copy(alpha = if (canBlur) 0.52f else 0.94f)
    else Color(0xFFF8FAFC).copy(alpha = if (canBlur) 0.58f else 0.95f)
    val edge = if (c.dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.75f)
    val sheen = if (c.dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.45f)
    val lensColor = c.accent.copy(alpha = if (c.dark) 0.22f else 0.15f)

    var barOrigin by remember { mutableStateOf(Offset.Zero) }
    var barWidth by remember { mutableFloatStateOf(0f) }
    val padPx = with(density) { BarPad.toPx() }
    val slotW = if (barWidth > 0f) (barWidth - padPx * 2) / tabs.size else 0f

    fun leftOf(i: Int) = padPx + i * slotW
    fun clampLeft(x: Float) = x.coerceIn(padPx, padPx + (tabs.size - 1) * slotW)
    fun indexAt(x: Float) = ((x - padPx) / slotW).toInt().coerceIn(0, tabs.size - 1)

    // Lens state: slides with a springy overshoot and stretches sideways like liquid.
    val lensX = remember { Animatable(0f) }
    val stretch = remember { Animatable(1f) }
    var placed by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf<Float?>(null) }
    var hover by remember { mutableStateOf<Int?>(null) }
    val lift by animateFloatAsState(if (dragX != null) 1f else 0f, tween(220), label = "lift")

    LaunchedEffect(selected, slotW) {
        if (slotW <= 0f) return@LaunchedEffect
        if (!placed) {
            lensX.snapTo(leftOf(selected)); placed = true; return@LaunchedEffect
        }
        launch {
            stretch.snapTo(1f)
            stretch.animateTo(1f, keyframes {
                durationMillis = 520
                1f at 0
                1.28f at 180
                1f at 520
            })
        }
        lensX.animateTo(leftOf(selected), spring(dampingRatio = 0.62f, stiffness = 380f))
    }

    Box(
        modifier
            // Compact floating capsule centred above the gesture bar
            .width(TabWidth * tabs.size + BarPad * 2)
            .height(BarHeight)
            .onGloballyPositioned {
                barOrigin = it.positionInRoot()
                barWidth = it.size.width.toFloat()
            }
            .shadow(
                elevation = 22.dp, shape = barShape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = if (c.dark) 0.55f else 0.18f),
            )
            .clip(barShape)
            // Press and drag along the bar to move the lens; lifting opens that tab.
            .pointerInput(slotW, selected, tabs.size) {
                if (slotW <= 0f) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragX = clampLeft(o.x - slotW / 2)
                        hover = indexAt(o.x)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val x = change.position.x
                        dragX = clampLeft(x - slotW / 2)
                        val i = indexAt(x)
                        if (i != hover) {
                            hover = i
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        val from = dragX
                        val i = hover ?: selected
                        dragX = null; hover = null
                        scope.launch {
                            if (from != null) lensX.snapTo(from)
                            if (i == selected) {
                                lensX.animateTo(leftOf(i), spring(dampingRatio = 0.62f, stiffness = 380f))
                            }
                        }
                        if (i != selected) onSelect(i)
                    },
                    onDragCancel = {
                        dragX = null; hover = null
                        scope.launch { lensX.animateTo(leftOf(selected), spring(Spring.DampingRatioMediumBouncy)) }
                    },
                )
            }
    ) {
        // 1) Frosted backdrop: the screen behind the bar, blurred
        if (canBlur) {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer {
                        val r = 24.dp.toPx()
                        renderEffect = BlurEffect(r, r, TileMode.Clamp)
                        clip = true
                    }
                    .drawBehind {
                        val o = barOrigin - contentOrigin
                        translate(-o.x, -o.y) { drawLayer(backdrop) }
                    }
            )
        }

        // 2) Glass tint, top sheen and bright rim
        Box(
            Modifier.matchParentSize()
                .background(tint)
                .background(Brush.verticalGradient(0f to sheen, 0.55f to Color.Transparent))
                .border(1.dp, edge, barShape)
        )

        // 3) Selected-tab lens
        if (slotW > 0f) {
            val slotWdp = with(density) { slotW.toDp() }
            Box(
                Modifier
                    .offset { IntOffset((dragX ?: lensX.value).roundToInt(), padPx.roundToInt()) }
                    .size(slotWdp, BarHeight - BarPad * 2)
                    .graphicsLayer {
                        val s = stretch.value
                        scaleX = s * (1f + 0.12f * lift)
                        scaleY = (1f - (s - 1f) * 0.36f) * (1f + 0.08f * lift)
                    }
                    .clip(lensShape)
                    .background(lensColor)
                    .border(0.5.dp, edge.copy(alpha = edge.alpha * 0.6f), lensShape)
            )
        }

        // 4) Tabs
        Row(Modifier.fillMaxSize().padding(BarPad)) {
            tabs.forEachIndexed { i, tab ->
                val active = (hover ?: selected) == i
                GlassTab(tab, active, Modifier.weight(1f)) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(i)
                }
            }
        }
    }
}

@Composable
private fun GlassTab(tab: TabItem, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = One.colors
    val pop = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            pop.snapTo(0.8f)
            pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
        }
    }
    val color = if (active) c.accent else c.text.copy(alpha = 0.62f)
    Column(
        modifier.fillMaxHeight().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (active) tab.selectedIcon else tab.icon, contentDescription = tab.label, tint = color,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label, color = color, fontSize = 10.5.sp, letterSpacing = 0.1.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
