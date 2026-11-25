package com.onerouteandroid.oneroute.feature.trips

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.AvatarBadge
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.RatingLabel
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SelectChip
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.components.TonalPillButton
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.DangerContainer
import com.onerouteandroid.oneroute.core.theme.DangerRed
import com.onerouteandroid.oneroute.core.theme.EcoContainer
import com.onerouteandroid.oneroute.core.theme.EcoTeal
import com.onerouteandroid.oneroute.core.theme.OutlineSoft
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.StarYellow
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.core.theme.WarningContainer
import com.onerouteandroid.oneroute.data.model.HistoryStatus
import com.onerouteandroid.oneroute.data.model.HistoryTrip

@Composable
fun TripHistoryScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val filters = listOf("Todos", "Completados", "Cancelados", "Como Conductor")
    var selectedFilter by rememberSaveable { mutableIntStateOf(0) }
    var refundDialogText by remember { mutableStateOf<String?>(null) }

    val filtered = state.historyTrips.filter { trip ->
        when (selectedFilter) {
            1 -> trip.status == HistoryStatus.COMPLETED
            2 -> trip.status == HistoryStatus.CANCELLED
            3 -> trip.status == HistoryStatus.AS_DRIVER
            else -> true
        }
    }
    val grouped = filtered.groupBy { it.month }

    refundDialogText?.let { note ->
        AlertDialog(
            onDismissRequest = { refundDialogText = null },
            title = { Text("Detalle de reembolso") },
            text = { Text(note) },
            confirmButton = { TextButton(onClick = { refundDialogText = null }) { Text("Entendido") } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Historial de Viajes",
                onBack = { nav.back() },
                onHelp = { nav.navigate(Route.HelpCenter) },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters.indices.toList()) { idx ->
                        SelectChip(filters[idx], selectedFilter == idx, { selectedFilter = idx })
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconBubble(Icons.AutoMirrored.Rounded.TrendingUp, size = 32, container = SoftContainer, tint = PrimaryBlue)
                                Text("Resumen de Octubre", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            }
                            StatusPill("Nivel Eco Pro", container = PrimaryContainer, content = PrimaryBlueDark)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KpiBox(Icons.Rounded.AccountBalanceWallet, "$585", "MXN ahorrados", Modifier.weight(1f))
                            KpiBox(Icons.Rounded.Route, "8", "Viajes hechos", Modifier.weight(1f))
                            KpiBox(Icons.Rounded.Eco, "24.5", "kg CO₂ menos", Modifier.weight(1f), eco = true)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("• Equivale a plantar 3 árboles urbanos", style = MaterialTheme.typography.bodySmall, color = PrimaryBlue)
                            Icon(Icons.Rounded.Park, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (grouped.isEmpty()) {
                item {
                    AppCard(container = SoftContainer) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconBubble(Icons.Rounded.History, size = 44, container = PrimaryContainer, tint = PrimaryBlueDark)
                            Text("Sin viajes en esta categoría", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                    }
                }
            } else {
                grouped.forEach { (month, list) ->
                    item(key = "hdr-$month") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(month, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("${list.size} viajes", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                    items(list, key = { it.id }) { trip ->
                        HistoryTripCard(
                            trip = trip,
                            onReceipt = { Toast.makeText(context, "Descargando recibo...", Toast.LENGTH_SHORT).show() },
                            onRebook = { nav.selectTab(MainTab.BUSCAR) },
                            onRate = { nav.navigate(Route.RateUser(trip.id, trip.counterpart, trip.counterpartInfo)) },
                            onRefund = { refundDialogText = trip.refundNote },
                            onRepublish = { nav.navigate(Route.CreateTrip) }
                        )
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 22) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconBubble(Icons.Rounded.History, size = 40, container = PrimaryContainer, tint = PrimaryBlueDark)
                        Text("Has llegado al final", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            "Tus viajes anteriores a Septiembre 2024 están resguardados en tu archivo fiscal de facturación.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        TonalPillButton(
                            text = "Descargar reporte anual (PDF)",
                            onClick = { Toast.makeText(context, "Generando reporte anual PDF...", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    eco: Boolean = false
) {
    Surface(color = SoftContainer, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = if (eco) EcoTeal else PrimaryBlue, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun HistoryTripCard(
    trip: HistoryTrip,
    onReceipt: () -> Unit,
    onRebook: () -> Unit,
    onRate: () -> Unit,
    onRefund: () -> Unit,
    onRepublish: () -> Unit
) {
    val (pillBg, pillFg, pillIcon) = when (trip.status) {
        HistoryStatus.COMPLETED -> Triple(PrimaryContainer, PrimaryBlueDark, Icons.Rounded.CheckCircle)
        HistoryStatus.CANCELLED -> Triple(DangerContainer, DangerRed, Icons.Rounded.Cancel)
        HistoryStatus.AS_DRIVER -> Triple(SoftContainer, PrimaryBlueDark, Icons.Rounded.DirectionsCar)
    }

    AppCard(corner = 22) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StatusPill("${trip.statusLabel} • ${trip.dateLabel}", container = pillBg, content = pillFg, icon = pillIcon)
                Text(
                    text = when (trip.status) {
                        HistoryStatus.AS_DRIVER -> "+$${trip.amount}.00 MXN"
                        else -> "$${trip.amount}.00 MXN"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (trip.status == HistoryStatus.CANCELLED) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = when (trip.status) {
                        HistoryStatus.AS_DRIVER -> PrimaryBlue
                        HistoryStatus.CANCELLED -> TextMuted
                        HistoryStatus.COMPLETED -> TextPrimary
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(if (trip.status == HistoryStatus.CANCELLED) TextMuted else PrimaryBlue))
                    Column {
                        Text(trip.origin, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(trip.originDetail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(TextMuted))
                    Column {
                        Text(trip.destination, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(trip.destinationDetail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            if (trip.status == HistoryStatus.CANCELLED && trip.refundNote != null) {
                Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        Text(trip.refundNote, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onRefund, shape = CircleShape) {
                            Text("Detalle", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AvatarBadge(trip.counterpart.take(2).uppercase(), size = 34)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(trip.counterpart, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                trip.counterpartRating?.let { RatingLabel(it) }
                            }
                            Text(trip.counterpartInfo, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                if (trip.canRate) {
                    Surface(color = WarningContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Star, contentDescription = null, tint = StarYellow, modifier = Modifier.size(18.dp))
                            Text("Calificar a ${trip.counterpart} (+10 pts comunidad)", style = MaterialTheme.typography.labelMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                            Button(onClick = onRate, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                                Text("Calificar", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReceipt, shape = CircleShape, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (trip.status == HistoryStatus.AS_DRIVER) "Desglose" else "Ver recibo", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = if (trip.status == HistoryStatus.AS_DRIVER) onRepublish else onRebook,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (trip.status == HistoryStatus.AS_DRIVER) PrimaryBlue else PrimaryContainer,
                            contentColor = if (trip.status == HistoryStatus.AS_DRIVER) Color.White else PrimaryBlueDark
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (trip.status == HistoryStatus.AS_DRIVER) "Publicar igual" else "Volver a reservar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
