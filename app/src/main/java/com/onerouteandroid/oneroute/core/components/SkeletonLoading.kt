package com.onerouteandroid.oneroute.core.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.ShimmerBase
import com.onerouteandroid.oneroute.core.theme.ShimmerHighlight
import kotlinx.coroutines.delay

enum class SkeletonVariant(val hasBottomBar: Boolean) {
    HOME(true),
    SEARCH(true),
    TRIPS(true),
    PROFILE(true),
    DETAIL(false),
    LIST(false),
    FORM(false),
    CHAT(false)
}

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerOffset"
    )
    background(
        Brush.linearGradient(
            colors = listOf(ShimmerBase, ShimmerHighlight, ShimmerBase),
            start = Offset(offset, 0f),
            end = Offset(offset + 500f, 300f)
        )
    )
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, radius: Dp = 12.dp) {
    Box(modifier.clip(RoundedCornerShape(radius)).shimmerEffect())
}

@Composable
fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).shimmerEffect())
}

@Composable
private fun SkeletonLine(width: Dp? = null, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    SkeletonBlock(
        modifier = (if (width != null) modifier.width(width) else modifier.fillMaxWidth()).height(height),
        radius = 6.dp
    )
}

@Composable
private fun SkeletonCard(height: Dp, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(CardSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun SkeletonFrame(variant: SkeletonVariant, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(CanvasBackground)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(CardSurface)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonCircle(36.dp)
            SkeletonLine(width = 140.dp, height = 20.dp)
            Spacer(Modifier.weight(1f))
            SkeletonCircle(36.dp)
        }
        Column(
            Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
        if (variant.hasBottomBar) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CardSurface)
                    .navigationBarsPadding()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(4) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonCircle(26.dp)
                        SkeletonLine(width = 40.dp, height = 8.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonRowItem() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardSurface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SkeletonCircle(44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(width = 150.dp)
            SkeletonLine(height = 10.dp)
        }
    }
}

@Composable
private fun SkeletonFor(variant: SkeletonVariant) {
    SkeletonFrame(variant) {
        when (variant) {
            SkeletonVariant.HOME -> {
                SkeletonLine(width = 180.dp, height = 24.dp)
                SkeletonLine(width = 120.dp)
                SkeletonCard(250.dp) {
                    SkeletonBlock(Modifier.fillMaxWidth().height(64.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(64.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(52.dp), radius = 26.dp)
                }
                SkeletonLine(width = 200.dp, height = 20.dp)
                SkeletonCard(150.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonCircle(44.dp)
                        SkeletonLine(width = 130.dp)
                    }
                    SkeletonBlock(Modifier.fillMaxWidth().height(60.dp))
                }
                SkeletonCard(80.dp) { SkeletonLine(); SkeletonLine(width = 200.dp) }
            }

            SkeletonVariant.SEARCH -> {
                SkeletonCard(230.dp) {
                    SkeletonBlock(Modifier.fillMaxWidth().height(56.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(56.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { SkeletonBlock(Modifier.width(80.dp).height(36.dp), radius = 18.dp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { SkeletonBlock(Modifier.width(110.dp).height(38.dp), radius = 19.dp) }
                }
                repeat(2) {
                    SkeletonCard(170.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SkeletonCircle(44.dp)
                            SkeletonLine(width = 130.dp)
                        }
                        SkeletonBlock(Modifier.fillMaxWidth().height(64.dp))
                        SkeletonLine(width = 160.dp)
                    }
                }
            }

            SkeletonVariant.TRIPS -> {
                SkeletonBlock(Modifier.fillMaxWidth().height(44.dp))
                SkeletonBlock(Modifier.fillMaxWidth().height(52.dp), radius = 26.dp)
                SkeletonBlock(Modifier.fillMaxWidth().height(70.dp), radius = 16.dp)
                SkeletonCard(280.dp) {
                    SkeletonLine(width = 160.dp)
                    SkeletonBlock(Modifier.fillMaxWidth().height(100.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(56.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(48.dp), radius = 24.dp)
                }
            }

            SkeletonVariant.PROFILE -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonCircle(92.dp)
                    SkeletonLine(width = 200.dp, height = 22.dp)
                    SkeletonLine(width = 150.dp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { SkeletonBlock(Modifier.weight(1f).height(100.dp), radius = 20.dp) }
                }
                SkeletonCard(130.dp) { SkeletonLine(width = 180.dp); SkeletonBlock(Modifier.fillMaxWidth().height(60.dp)) }
                repeat(2) { SkeletonRowItem() }
            }

            SkeletonVariant.DETAIL -> {
                SkeletonBlock(Modifier.fillMaxWidth().height(170.dp), radius = 24.dp)
                SkeletonCard(150.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonCircle(56.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SkeletonLine(width = 150.dp); SkeletonLine(width = 100.dp) }
                    }
                    SkeletonBlock(Modifier.fillMaxWidth().height(48.dp))
                }
                SkeletonCard(180.dp) { SkeletonLine(width = 140.dp, height = 18.dp); SkeletonBlock(Modifier.fillMaxWidth().height(110.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { SkeletonBlock(Modifier.width(100.dp).height(38.dp), radius = 19.dp) }
                }
            }

            SkeletonVariant.LIST -> {
                SkeletonLine(width = 220.dp, height = 24.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { SkeletonBlock(Modifier.width(90.dp).height(36.dp), radius = 18.dp) }
                }
                repeat(5) { SkeletonRowItem() }
            }

            SkeletonVariant.FORM -> {
                SkeletonLine(width = 240.dp, height = 28.dp)
                SkeletonLine(width = 300.dp)
                repeat(4) { SkeletonBlock(Modifier.fillMaxWidth().height(58.dp), radius = 18.dp) }
                SkeletonBlock(Modifier.fillMaxWidth().height(52.dp), radius = 26.dp)
            }

            SkeletonVariant.CHAT -> {
                SkeletonCard(110.dp) { SkeletonLine(width = 180.dp); SkeletonLine() }
                SkeletonBlock(Modifier.width(240.dp).height(70.dp), radius = 20.dp)
                SkeletonBlock(Modifier.align(Alignment.End).width(220.dp).height(60.dp), radius = 20.dp)
                SkeletonBlock(Modifier.width(260.dp).height(90.dp), radius = 20.dp)
                SkeletonBlock(Modifier.align(Alignment.End).width(200.dp).height(56.dp), radius = 20.dp)
            }
        }
    }
}

@Composable
fun SkeletonContentHost(
    variant: SkeletonVariant,
    contentKey: Any,
    shouldAnimate: Boolean,
    modifier: Modifier = Modifier,
    durationMs: Long = 650L,
    content: @Composable () -> Unit
) {
    var loading by remember(contentKey) { mutableStateOf(shouldAnimate) }
    LaunchedEffect(contentKey) {
        if (shouldAnimate) {
            delay(durationMs)
            loading = false
        }
    }
    Crossfade(targetState = loading, animationSpec = tween(260), modifier = modifier, label = "skeletonCrossfade") { isLoading ->
        if (isLoading) SkeletonFor(variant) else content()
    }
}
