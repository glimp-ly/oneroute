package com.oneroute.feature.messaging

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppBottomBar
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.MainTab
import com.oneroute.core.components.ScreenList
import com.oneroute.core.components.SelectChip
import com.oneroute.core.components.StatusPill
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.data.model.NotificationCategory
import com.oneroute.data.model.NotificationItem
import com.oneroute.data.model.NotificationKind

@Composable
fun NotificationsScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val all = state.notifications
    val tripsCount = all.count { it.category == NotificationCategory.TRIPS }
    val payCount = all.count { it.category == NotificationCategory.PAYMENTS }
    val sysCount = all.count { it.category == NotificationCategory.SYSTEM }
    val chips = listOf("Todos ${all.size}", "Viajes $tripsCount", "Pagos $payCount", "Sistema $sysCount")

    val filtered = all.filter { n ->
        when (filterIndex) {
            1 -> n.category == NotificationCategory.TRIPS
            2 -> n.category == NotificationCategory.PAYMENTS
            3 -> n.category == NotificationCategory.SYSTEM
            else -> true
        }
    }
    val grouped = filtered.groupBy { it.group }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Notificaciones",
                showBrandLogo = true,
                onBack = { nav.back() },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        bottomBar = {
            AppBottomBar(MainTab.INICIO, onSelect = nav::selectTab, tripBadge = state.scheduledTrips.size)
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Notificaciones", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        if (state.unreadNotifications > 0) {
                            StatusPill("${state.unreadNotifications} no leídas", container = PrimaryBlue, content = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            state.markAllNotificationsRead()
                            Toast.makeText(context, "Todas marcadas como leídas", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = "Marcar todas leídas", tint = PrimaryBlue)
                        }
                    }
                    Text("Mantente al tanto de tus rutas, ganancias compartidas y actualizaciones de seguridad.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chips.indices.toList()) { idx ->
                        SelectChip(chips[idx], filterIndex == idx, { filterIndex = idx })
                    }
                }
            }

            grouped.forEach { (group, list) ->
                item(key = "grp-$group") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(group, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        val pending = list.count { !it.read }
                        if (pending > 0) Text("$pending pendientes", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                    }
                }
                items(list, key = { it.id }) { item ->
                    NotificationCard(
                        item = item,
                        onTap = { state.markNotificationRead(item.id) },
                        onAction = { label ->
                            state.markNotificationRead(item.id)
                            when (label) {
                                "Ver reserva" -> nav.selectTab(MainTab.MIS_VIAJES)
                                "Mensaje" -> nav.navigate(Route.RideChat("c1"))
                                "Ver Billetera" -> nav.navigate(Route.WalletPayments)
                                else -> Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    onTap: () -> Unit,
    onAction: (String) -> Unit
) {
    val icon = when (item.kind) {
        NotificationKind.TRIP -> Icons.Rounded.DirectionsCar
        NotificationKind.PAYMENT -> Icons.Rounded.AccountBalanceWallet
        NotificationKind.RATING -> Icons.Rounded.Star
        NotificationKind.DOCUMENT -> Icons.Rounded.Verified
        NotificationKind.ALERT -> Icons.Rounded.Traffic
        NotificationKind.ACHIEVEMENT -> Icons.Rounded.Park
    }

    AppCard(onClick = onTap, container = if (item.read) SoftContainer else CardSurface, corner = 22) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                IconBubble(
                    icon = icon,
                    size = 44,
                    container = if (!item.read && item.kind == NotificationKind.TRIP) PrimaryBlue else PrimaryContainer,
                    tint = if (!item.read && item.kind == NotificationKind.TRIP) Color.White else PrimaryBlueDark
                )
                if (!item.read) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(PrimaryBlue).align(Alignment.TopEnd))
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(item.time, style = MaterialTheme.typography.labelSmall, color = if (item.read) TextMuted else PrimaryBlue)
                }
                Text(item.body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (item.actionLabel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!item.read && item.secondaryActionLabel != null) {
                            Button(onClick = { onAction(item.actionLabel) }, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                                Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(" ${item.actionLabel}", style = MaterialTheme.typography.labelMedium)
                            }
                            Surface(onClick = { onAction(item.secondaryActionLabel) }, color = SoftContainer, shape = CircleShape) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = PrimaryBlueDark, modifier = Modifier.size(14.dp))
                                    Text(item.secondaryActionLabel, style = MaterialTheme.typography.labelMedium, color = PrimaryBlueDark)
                                }
                            }
                        } else {
                            Surface(onClick = { onAction(item.actionLabel) }, color = if (item.read) CardSurface else PrimaryContainer, shape = CircleShape) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val actionIcon = when (item.kind) {
                                        NotificationKind.ALERT -> Icons.AutoMirrored.Rounded.AltRoute
                                        NotificationKind.ACHIEVEMENT -> Icons.Rounded.Share
                                        else -> Icons.Rounded.AccountBalanceWallet
                                    }
                                    Icon(actionIcon, contentDescription = null, tint = PrimaryBlueDark, modifier = Modifier.size(14.dp))
                                    Text(item.actionLabel, style = MaterialTheme.typography.labelMedium, color = PrimaryBlueDark)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
