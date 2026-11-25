package com.onerouteandroid.oneroute.feature.messaging

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTextField
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.AvatarBadge
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.RatingLabel
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SectionHeader
import com.onerouteandroid.oneroute.core.components.SelectChip
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.data.model.Conversation

@Composable
fun MessagesInboxScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }

    val matching = state.conversations.filter { conv ->
        val matchesQuery = query.isBlank() || conv.name.contains(query, true) || conv.preview.contains(query, true)
        val matchesTab = when (filterIndex) {
            1 -> conv.routeLabel.contains("Hoy", true)
            2 -> conv.active
            else -> true
        }
        matchesQuery && matchesTab
    }
    val active = matching.filter { it.active }
    val finished = matching.filter { it.finished }
    val tabs = listOf(
        "Todos (${state.conversations.size})",
        "Viajes de Hoy (${state.conversations.count { it.routeLabel.contains("Hoy", true) }})",
        "Próximos (${state.conversations.count { it.active }})"
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Carpool Inbox",
                showBrandLogo = true,
                onBack = { nav.back() },
                onNotifications = { nav.navigate(Route.Notifications) },
                unreadNotifications = state.unreadNotifications,
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { Toast.makeText(context, "Selecciona un viaje para iniciar chat", Toast.LENGTH_SHORT).show() },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                icon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                text = { Text("Mensaje", style = MaterialTheme.typography.labelLarge) }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mensajes de Viajes", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text("Coordinación en tiempo real", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = { searchOpen = !searchOpen }, color = SoftContainer, shape = CircleShape) {
                            Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = "Buscar", tint = TextPrimary, modifier = Modifier.padding(10.dp))
                        }
                        Surface(onClick = { filterIndex = (filterIndex + 1) % 3 }, color = SoftContainer, shape = CircleShape) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Filtrar", tint = TextPrimary, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }

            if (searchOpen) {
                item {
                    AppTextField(value = query, onValueChange = { query = it }, label = "Buscar conversación", leadingIcon = Icons.Rounded.Search)
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tabs.indices.toList()) { idx ->
                        SelectChip(tabs[idx], filterIndex == idx, { filterIndex = idx })
                    }
                }
            }

            item {
                AppCard(container = PrimaryContainer, corner = 22) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.NearMe, size = 40, container = PrimaryBlue, tint = Color.White)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("VIAJE EN CURSO HOY", style = MaterialTheme.typography.labelSmall, color = PrimaryBlueDark)
                            Text(
                                "Salida hoy a las 18:30 hrs • Tu conductor Carlos M. está en camino al punto de encuentro en Parque México.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = { nav.navigate(Route.RideChat("c1")) },
                                    color = CardSurface,
                                    shape = CircleShape
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Abrir chat activo", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text("Llegada ~8 min", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            if (active.isNotEmpty()) {
                item {
                    SectionHeader("Conversaciones Activas", badge = "${active.size} activas", action = "Hoy")
                }
                items(active, key = { it.id }) { conv ->
                    ActiveConversationCard(
                        conv = conv,
                        onOpen = { nav.navigate(Route.RideChat(conv.id)) },
                        onCall = { Toast.makeText(context, "Llamando a ${conv.name}", Toast.LENGTH_SHORT).show() },
                        onMap = {
                            val tripId = state.scheduledTrips.firstOrNull()?.id ?: "st1"
                            nav.navigate(Route.MeetingPoint(tripId))
                        }
                    )
                }
            }

            if (finished.isNotEmpty()) {
                item {
                    SectionHeader("Historial de Viajes", action = "Finalizados")
                }
                items(finished, key = { it.id }) { conv ->
                    AppCard(onClick = { nav.navigate(Route.RideChat(conv.id)) }, container = SoftContainer, corner = 20) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AvatarBadge(conv.name.split(' ').take(2).joinToString("") { it.take(1) }, size = 44)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(conv.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text(conv.time, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                }
                                StatusPill("Viaje Finalizado", container = CardSurface, content = TextSecondary, icon = Icons.Rounded.CheckCircle)
                                Text(conv.preview, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(conv.routeLabel, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                    }
                }
            }

            if (matching.isEmpty()) {
                item {
                    AppCard(container = SoftContainer) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No se encontraron conversaciones", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.Lock, size = 38, container = PrimaryContainer, tint = PrimaryBlueDark)
                        Column {
                            Text("Privacidad de viaje protegida", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                "Mensajes protegidos con cifrado de extremo a extremo. Los números de teléfono reales se mantienen privados en todo momento.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

@Composable
private fun ActiveConversationCard(
    conv: Conversation,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onMap: () -> Unit
) {
    AppCard(onClick = onOpen, corner = 22) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (conv.unread > 0) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(PrimaryBlue))
            }
            Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarBadge(conv.name.split(' ').take(2).joinToString("") { it.take(1) }, size = 48, verified = conv.unread > 0)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(conv.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(conv.time, style = MaterialTheme.typography.labelMedium, color = if (conv.unread > 0) PrimaryBlue else TextMuted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(conv.roleLabel, container = SoftContainer, content = TextSecondary)
                            RatingLabel(conv.rating)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conv.preview,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (conv.unread > 0) FontWeight.Bold else FontWeight.Normal),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (conv.unread > 0) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(conv.unread.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }

                Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Route, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Text(conv.routeLabel, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    }
                }

                if (conv.unread > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCall, shape = CircleShape, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Llamar", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(onClick = onMap, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ver Mapa", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
