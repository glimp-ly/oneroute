package com.onerouteandroid.oneroute.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppBottomBar
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SectionHeader
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.DangerRed
import com.onerouteandroid.oneroute.core.theme.EcoContainer
import com.onerouteandroid.oneroute.core.theme.EcoTeal
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary

@Composable
fun HomeScreen(state: AppState, nav: AppNavigator) {
    var origin by rememberSaveable { mutableStateOf("Mi ubicación actual") }
    var destination by rememberSaveable { mutableStateOf("Santa Fe, Ciudad de México") }
    val schedules = listOf("Hoy, 18:30", "Hoy, 19:00", "Mañana, 07:30")
    var scheduleIndex by rememberSaveable { mutableIntStateOf(0) }
    var passengers by rememberSaveable { mutableIntStateOf(1) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Inicio",
                showBrandLogo = true,
                onNotifications = { nav.navigate(Route.Notifications) },
                unreadNotifications = state.unreadNotifications,
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) },
                actions = {
                    IconButton(onClick = { nav.navigate(Route.MessagesInbox) }) {
                        BadgedBox(badge = { if (state.unreadMessages > 0) Badge { Text(state.unreadMessages.toString()) } }) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "Mensajes", tint = TextSecondary)
                        }
                    }
                }
            )
        },
        bottomBar = {
            AppBottomBar(MainTab.INICIO, onSelect = nav::selectTab, tripBadge = state.scheduledTrips.size)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Route.CreateTrip) },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Publicar viaje", style = MaterialTheme.typography.labelLarge) }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hola, ${state.currentUser.firstName} 👋", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("¿A dónde viajas hoy?", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("Av. Reforma, CDMX", container = PrimaryContainer, content = PrimaryBlueDark, icon = Icons.Rounded.NearMe)
                        StatusPill("En tiempo real", container = SoftContainer, content = PrimaryBlue)
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 28) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SearchPointBox(
                                    label = "PUNTO DE SALIDA",
                                    value = origin,
                                    onValueChange = { origin = it },
                                    dotColor = PrimaryBlue,
                                    filledDot = false
                                )
                                SearchPointBox(
                                    label = "DESTINO",
                                    value = destination,
                                    onValueChange = { destination = it },
                                    dotColor = DangerRed,
                                    filledDot = true
                                )
                            }
                            Surface(
                                onClick = {
                                    val tmp = origin
                                    origin = destination
                                    destination = tmp
                                },
                                shape = CircleShape,
                                color = PrimaryContainer,
                                shadowElevation = 2.dp,
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.SwapVert, contentDescription = "Intercambiar", tint = PrimaryBlueDark)
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickSelectorCard(
                                label = "Horario",
                                value = schedules[scheduleIndex],
                                icon = Icons.Rounded.Schedule,
                                onClick = { scheduleIndex = (scheduleIndex + 1) % schedules.size },
                                modifier = Modifier.weight(1f)
                            )
                            QuickSelectorCard(
                                label = "Pasajeros",
                                value = if (passengers == 1) "1 pasajero" else "$passengers pasajeros",
                                icon = Icons.Rounded.Person,
                                onClick = { passengers = (passengers % 3) + 1 },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        PrimaryPillButton(
                            text = "Buscar viaje",
                            icon = Icons.Rounded.Search,
                            onClick = { nav.selectTab(MainTab.BUSCAR) }
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Viajes disponibles hoy",
                    badge = "En ruta",
                    action = "Ver todos",
                    onAction = { nav.selectTab(MainTab.BUSCAR) }
                )
            }

            state.availableTrips.firstOrNull()?.let { trip ->
                item {
                    AvailableTripCard(
                        trip = trip,
                        compact = true,
                        onClick = { nav.navigate(Route.TripDetail(trip.id)) }
                    )
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 22) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.Eco, size = 44, container = EcoContainer, tint = EcoTeal)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("TU IMPACTO ECOLÓGICO", style = MaterialTheme.typography.labelSmall, color = PrimaryBlueDark)
                                StatusPill("Nivel 2", container = CardSurface, content = TextPrimary)
                            }
                            Text(
                                "Has ahorrado 14.2 kg de CO₂ este mes compartiendo auto con tu comunidad.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                AppCard(onClick = { nav.navigate(Route.DocumentVerification) }, container = CardSurface, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.Shield, size = 40, container = SoftContainer, tint = TextPrimary)
                        Column(Modifier.weight(1f)) {
                            Text("Comunidad Segura", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Perfiles verificados con INE y reseñas", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }

            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

@Composable
private fun SearchPointBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    dotColor: Color,
    filledDot: Boolean
) {
    Surface(color = CardSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (filledDot) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = dotColor, modifier = Modifier.size(20.dp))
            } else {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(CardSurface)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 36.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleSmall.copy(color = TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun QuickSelectorCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(onClick = onClick, color = CardSurface, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(value, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
        }
    }
}
