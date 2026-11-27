package com.oneroute.core.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.DangerRed
import com.oneroute.core.theme.MapBackground
import com.oneroute.core.theme.MapBlock
import com.oneroute.core.theme.MapPark
import com.oneroute.core.theme.MapRoad
import com.oneroute.core.theme.OutlineSoft
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.StarYellow
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.data.model.Driver

@Composable
fun ScreenList(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    container: Color = CardSurface,
    corner: Int = 24,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = container)
    val shape = RoundedCornerShape(corner.dp)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick != null) {
        Card(onClick = onClick, shape = shape, colors = colors, elevation = elevation, modifier = modifier.fillMaxWidth()) { content() }
    } else {
        Card(shape = shape, colors = colors, elevation = elevation, modifier = modifier.fillMaxWidth()) { content() }
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Int = 40,
    container: Color = PrimaryContainer,
    tint: Color = PrimaryBlue
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = PrimaryContainer,
    content: Color = PrimaryBlueDark,
    icon: ImageVector? = null
) {
    Surface(color = container, shape = CircleShape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = content)
        }
    }
}

@Composable
fun AvatarBadge(
    initials: String,
    modifier: Modifier = Modifier,
    size: Int = 44,
    verified: Boolean = false,
    container: Color = PrimaryContainer,
    content: Color = PrimaryBlueDark
) {
    Box(modifier = modifier.size(size.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = content,
                fontWeight = FontWeight.Bold,
                style = if (size >= 56) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge
            )
        }
        if (verified) {
            Icon(
                Icons.Rounded.Verified,
                contentDescription = "Verificado",
                tint = PrimaryBlue,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.4f).dp)
                    .background(CardSurface, CircleShape)
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            StatusPill(badge)
        }
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun RatingLabel(rating: Double, modifier: Modifier = Modifier, suffix: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = StarYellow, modifier = Modifier.size(14.dp))
        Text(rating.toString(), style = MaterialTheme.typography.labelMedium, color = TextPrimary)
        if (suffix != null) Text(suffix, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
fun RouteTimeline(
    departure: String,
    origin: String,
    originDetail: String,
    arrival: String,
    destination: String,
    destinationDetail: String,
    modifier: Modifier = Modifier,
    duration: String? = null
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 6.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(PrimaryBlue))
            Box(Modifier.width(2.dp).height(54.dp).background(OutlineSoft))
            Box(Modifier.size(12.dp).clip(CircleShape).background(PrimaryBlueDark))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TimelineStop(departure, origin, originDetail, trailing = duration)
            TimelineStop(arrival, destination, destinationDetail)
        }
    }
}

@Composable
private fun TimelineStop(time: String, name: String, detail: String, trailing: String? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time, style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.weight(1f))
            if (trailing != null) StatusPill(trailing, container = SoftContainer, content = TextSecondary)
        }
        Text(name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
fun DriverMiniCard(
    driver: Driver,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = SoftContainer, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarBadge(driver.initials, size = 44, verified = driver.verified)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(driver.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    RatingLabel(driver.rating)
                }
                Text(
                    "${driver.vehicle} ${driver.color} • Placas ${driver.plate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            trailing()
        }
    }
}

@Composable
fun CallButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .background(PrimaryContainer, CircleShape)
    ) {
        Icon(Icons.Rounded.Call, contentDescription = "Llamar", tint = PrimaryBlue)
    }
}

@Composable
fun StylizedMapCanvas(
    modifier: Modifier = Modifier,
    showRoute: Boolean = true,
    progress: Float = 0.5f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier = modifier.background(MapBackground)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(MapPark, topLeft = Offset(w * 0.04f, h * 0.55f), size = Size(w * 0.22f, h * 0.3f))
            drawRect(MapPark, topLeft = Offset(w * 0.68f, h * 0.08f), size = Size(w * 0.2f, h * 0.22f))
            val cols = 5
            val rows = 4
            for (c in 0 until cols) for (r in 0 until rows) {
                if ((c + r) % 3 == 0) continue
                drawRect(
                    MapBlock,
                    topLeft = Offset(w * (0.03f + c * 0.2f), h * (0.04f + r * 0.25f)),
                    size = Size(w * 0.14f, h * 0.16f)
                )
            }
            for (c in 0..cols) drawLine(MapRoad, Offset(w * (0.01f + c * 0.2f), 0f), Offset(w * (0.01f + c * 0.2f), h), strokeWidth = 10f)
            for (r in 0..rows) drawLine(MapRoad, Offset(0f, h * (0.0f + r * 0.25f)), Offset(w, h * (0.0f + r * 0.25f)), strokeWidth = 10f)
            drawLine(MapRoad, Offset(0f, h * 0.9f), Offset(w, h * 0.15f), strokeWidth = 14f)
            if (showRoute) {
                val route = Path().apply {
                    moveTo(w * 0.12f, h * 0.82f)
                    cubicTo(w * 0.35f, h * 0.9f, w * 0.4f, h * 0.45f, w * 0.62f, h * 0.45f)
                    cubicTo(w * 0.8f, h * 0.45f, w * 0.75f, h * 0.2f, w * 0.88f, h * 0.16f)
                }
                drawPath(route, PrimaryBlue, style = Stroke(width = 12f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 16f))))
                drawCircle(PrimaryBlue, radius = 14f, center = Offset(w * 0.12f, h * 0.82f))
                drawCircle(Color.White, radius = 6f, center = Offset(w * 0.12f, h * 0.82f))
                drawCircle(DangerRed, radius = 14f, center = Offset(w * 0.88f, h * 0.16f))
                val marker = Offset(w * (0.12f + 0.76f * progress.coerceIn(0f, 1f)), h * (0.82f - 0.66f * progress.coerceIn(0f, 1f)))
                drawCircle(PrimaryBlue.copy(alpha = 0.2f), radius = 34f, center = marker)
                drawCircle(PrimaryBlue, radius = 16f, center = marker)
                drawCircle(Color.White, radius = 6f, center = marker)
            }
        }
        content()
    }
}

@Composable
fun StylizedQrCode(code: String, modifier: Modifier = Modifier) {
    val cells = remember(code) {
        val seed = code.hashCode()
        val random = java.util.Random(seed.toLong())
        Array(21) { BooleanArray(21) { random.nextInt(100) < 48 } }
    }
    Canvas(modifier.background(Color.White).padding(6.dp)) {
        val cell = size.width / 21f
        fun finder(cx: Int, cy: Int) {
            drawRect(Color(0xFF0F172A), Offset(cx * cell, cy * cell), Size(7 * cell, 7 * cell))
            drawRect(Color.White, Offset((cx + 1) * cell, (cy + 1) * cell), Size(5 * cell, 5 * cell))
            drawRect(Color(0xFF0F172A), Offset((cx + 2) * cell, (cy + 2) * cell), Size(3 * cell, 3 * cell))
        }
        for (x in 0 until 21) for (y in 0 until 21) {
            val inFinder = (x < 8 && y < 8) || (x > 12 && y < 8) || (x < 8 && y > 12)
            if (!inFinder && cells[x][y]) {
                drawRect(
                    if ((x + y) % 7 == 0) PrimaryBlue else Color(0xFF0F172A),
                    Offset(x * cell, y * cell),
                    Size(cell, cell)
                )
            }
        }
        finder(0, 0)
        finder(14, 0)
        finder(0, 14)
    }
}

@Composable
fun MapOverlayPill(text: String, modifier: Modifier = Modifier, container: Color = CardSurface, content: Color = TextPrimary) {
    Surface(color = container, shape = CircleShape, shadowElevation = 2.dp, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

