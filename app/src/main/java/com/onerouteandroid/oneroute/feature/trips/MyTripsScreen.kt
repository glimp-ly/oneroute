package com.onerouteandroid.oneroute.feature.trips

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppBottomBar
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.AvatarBadge
import com.onerouteandroid.oneroute.core.components.CallButton
import com.onerouteandroid.oneroute.core.components.DriverMiniCard
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.OutlinedPillButton
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.RouteTimeline
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SectionHeader
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.components.TonalPillButton
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.EcoContainer
import com.onerouteandroid.oneroute.core.theme.EcoTeal
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.SuccessContainer
import com.onerouteandroid.oneroute.core.theme.SuccessGreen
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.data.model.ScheduledTrip
import com.onerouteandroid.oneroute.data.model.TripRole

@Composable
fun MyTripsScreen(initialRole: Int, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    var roleIndex by rememberSaveable(initialRole) { mutableIntStateOf(initialRole) }
    val selectedRole = if (roleIndex == 0) TripRole.PASSENGER else TripRole.DRIVER
    val filteredTrips = state.scheduledTrips.filter { it.role == selectedRole }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Mis Viajes",
                subtitle = "RUTACOMPARTIDA",
                showBrandLogo = true,
                onNotifications = { nav.navigate(Route.Notifications) },
                unreadNotifications = state.unreadNotifications,
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        bottomBar = {
            AppBottomBar(MainTab.MIS_VIAJES, onSelect = nav::selectTab, tripBadge = state.scheduledTrips.size)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Route.CreateTrip) },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Publicar Viaje", style = MaterialTheme.typography.labelLarge) }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = 0, containerColor = CardSurface, contentColor = PrimaryBlue) {
                Tab(
                    selected = true,
                    onClick = {},
                    text = { Text("Próximos (${state.scheduledTrips.size})", style = MaterialTheme.typography.labelLarge) }
                )
                Tab(
                    selected = false,
                    onClick = { nav.navigate(Route.TripHistory) },
                    text = { Text("Historial (${state.historyTrips.size})", style = MaterialTheme.typography.labelLarge, color = TextSecondary) }
                )
            }

            ScreenList(
                padding = androidx.compose.foundation.layout.PaddingValues(bottom = padding.calculateBottomPadding())
            ) {
                item {
                    Surface(color = SoftContainer, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(4.dp)) {
                            RolePill("Como Pasajero", Icons.Rounded.Person, roleIndex == 0, Modifier.weight(1f)) { roleIndex = 0 }
                            RolePill("Como Conductor", Icons.Rounded.DirectionsCar, roleIndex == 1, Modifier.weight(1f)) { roleIndex = 1 }
                        }
                    }
                }

                filteredTrips.firstOrNull()?.let { next ->
                    item {
                        AppCard(container = PrimaryContainer, corner = 20) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconBubble(Icons.Rounded.Schedule, size = 38, container = CardSurface, tint = PrimaryBlue)
                                Column(Modifier.weight(1f)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("SALIDA INMINENTE", style = MaterialTheme.typography.labelSmall, color = PrimaryBlueDark)
                                        StatusPill("En 2 horas", container = CardSurface, content = PrimaryBlue)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text("${next.origin} a ${next.destination} • Prepárate en punto", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                }
                            }
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Viajes Agendados", badge = filteredTrips.size.toString(), modifier = Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                Toast.makeText(context, "Mostrando viajes de ${if (roleIndex == 0) "pasajero" else "conductor"}", Toast.LENGTH_SHORT).show()
                            }.padding(6.dp)
                        ) {
                            Icon(Icons.Rounded.FilterList, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Text("Filtrar", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                        }
                    }
                }

                if (filteredTrips.isEmpty()) {
                    item {
                        AppCard(container = SoftContainer) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("No tienes viajes agendados en este rol", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                PrimaryPillButton(
                                    text = if (roleIndex == 0) "Buscar viaje" else "Publicar viaje",
                                    onClick = { if (roleIndex == 0) nav.selectTab(MainTab.BUSCAR) else nav.navigate(Route.CreateTrip) }
                                )
                            }
                        }
                    }
                } else {
                    items(filteredTrips, key = { it.id }) { trip ->
                        ScheduledTripCard(
                            trip = trip,
                            onOpenActive = { nav.navigate(Route.ActiveTrip(trip.id)) },
                            onOpenMeeting = { nav.navigate(Route.MeetingPoint(trip.id)) },
                            onOpenChat = {
                                val driverName = trip.driver?.name?.substringBefore(' ')
                                val conv = if (driverName != null) state.conversations.firstOrNull { it.name.contains(driverName) } else null
                                if (conv != null) nav.navigate(Route.RideChat(conv.id))
                                else Toast.makeText(context, "Abriendo chat del viaje", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                item {
                    val savedKg = (filteredTrips.size * 3.2).coerceAtLeast(3.2)
                    AppCard(container = SoftContainer, corner = 20) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBubble(Icons.Rounded.Eco, size = 40, container = EcoContainer, tint = EcoTeal)
                            Column(Modifier.weight(1f)) {
                                Text("Impacto Positivo", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    "Con tus viajes compartidos ahorras ${"%.1f".format(savedKg)} kg de CO₂ frente a viajar en auto individual.",
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
}

@Composable
private fun RolePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) CardSurface else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Icon(icon, contentDescription = null, tint = if (selected) PrimaryBlue else TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) PrimaryBlue else TextSecondary)
        }
    }
}

@Composable
private fun ScheduledTripCard(
    trip: ScheduledTrip,
    onOpenActive: () -> Unit,
    onOpenMeeting: () -> Unit,
    onOpenChat: () -> Unit
) {
    val context = LocalContext.current
    AppCard(corner = 24) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(trip.dateTag, container = PrimaryContainer, content = PrimaryBlueDark)
                    StatusPill(trip.seatInfo, container = SoftContainer, content = TextSecondary)
                }
                if (trip.role == TripRole.PASSENGER) {
                    StatusPill(trip.status, container = SuccessContainer, content = SuccessGreen, icon = Icons.Rounded.Check)
                }
            }

            if (trip.role == TripRole.DRIVER) {
                Text(trip.status, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    RouteTimeline(
                        departure = trip.departure,
                        origin = trip.origin,
                        originDetail = trip.originDetail,
                        arrival = trip.arrival,
                        destination = trip.destination,
                        destinationDetail = trip.destinationDetail,
                        duration = "${trip.durationMin} min"
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (trip.role == TripRole.PASSENGER) {
                    Surface(onClick = onOpenMeeting, color = SoftContainer, shape = CircleShape) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                            Text("Punto de encuentro", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$${trip.price}.00", style = MaterialTheme.typography.titleLarge, color = PrimaryBlue)
                    Text(if (trip.role == TripRole.PASSENGER) "MXN pagado" else "MXN proyectados", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }

            if (trip.role == TripRole.PASSENGER && trip.driver != null) {
                DriverMiniCard(trip.driver) {
                    CallButton(onClick = { Toast.makeText(context, "Llamando a ${trip.driver.name}", Toast.LENGTH_SHORT).show() })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Text(trip.paymentCard ?: "", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Text("Código abordaje: ${trip.boardingCode}", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedPillButton(
                        text = "Chat con conductor",
                        icon = Icons.AutoMirrored.Rounded.Chat,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenChat
                    )
                    PrimaryPillButton(
                        text = "Ver viaje activo",
                        icon = Icons.Rounded.Navigation,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenActive
                    )
                }
            } else {
                Surface(
                    onClick = { Toast.makeText(context, "Pasajeros confirmados: ${trip.passengersSummary ?: "0"}", Toast.LENGTH_SHORT).show() },
                    color = SoftContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarBadge("AR", size = 34)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(trip.passengersSummary ?: "Sin reservas aún", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Confirmaron punto de encuentro", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedPillButton(
                        text = "Detalle de ruta",
                        icon = Icons.Rounded.Route,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenActive
                    )
                    TonalPillButton(
                        text = "Gestionar pasajeros",
                        icon = Icons.Rounded.Groups,
                        modifier = Modifier.weight(1f),
                        onClick = { Toast.makeText(context, "2 pasajeros listos para abordar", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }
    }
}
