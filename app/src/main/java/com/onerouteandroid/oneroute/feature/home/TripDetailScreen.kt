package com.onerouteandroid.oneroute.feature.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.AvatarBadge
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.MapOverlayPill
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.RatingLabel
import com.onerouteandroid.oneroute.core.components.RouteTimeline
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.components.StylizedMapCanvas
import com.onerouteandroid.oneroute.core.components.TonalPillButton
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripDetailScreen(tripId: String, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val trip = state.availableTrip(tripId)

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Detalle del Viaje",
                showBrandLogo = true,
                onBack = { nav.back() },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        bottomBar = {
            if (trip != null) {
                Surface(color = CardSurface, shadowElevation = 6.dp) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Asientos disponibles", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    repeat(3) { idx ->
                                        Icon(
                                            Icons.Rounded.AirlineSeatReclineNormal,
                                            contentDescription = null,
                                            tint = if (idx < trip.seatsLeft) PrimaryBlue else TextMuted.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text("${trip.seatsLeft} libres", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$${trip.price}.00 MXN", style = MaterialTheme.typography.headlineSmall, color = PrimaryBlue)
                                Text("Base $${trip.price - 10} + Seguro $10", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }

                        PrimaryPillButton(
                            text = if (trip.seatsLeft > 0) "Reservar plaza ($${trip.price}.00 MXN)" else "Sin asientos disponibles",
                            icon = Icons.Rounded.CheckCircle,
                            enabled = trip.seatsLeft > 0,
                            onClick = {
                                if (state.bookTrip(trip.id) != null) {
                                    Toast.makeText(context, "Reserva confirmada", Toast.LENGTH_SHORT).show()
                                    nav.selectTab(MainTab.MIS_VIAJES)
                                }
                            }
                        )
                    }
                }
            }
        },
        containerColor = CanvasBackground
    ) { padding ->
        if (trip == null) {
            Box(Modifier.padding(padding).padding(24.dp)) {
                AppCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Viaje no encontrado", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        TonalPillButton("Volver", onClick = { nav.back() })
                    }
                }
            }
            return@Scaffold
        }

        ScreenList(padding) {
            item {
                StylizedMapCanvas(
                    modifier = Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(24.dp)),
                    progress = 0.35f
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MapOverlayPill("Ruta en vivo disponible")
                        StatusPill("${trip.durationMin} min", container = CardSurface, content = TextPrimary, icon = Icons.Rounded.Schedule)
                    }
                    MapOverlayPill(
                        "14.2 km • ${trip.via}",
                        container = PrimaryBlue,
                        content = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AvatarBadge(trip.driver.initials, size = 56, verified = trip.driver.verified)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(trip.driver.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                RatingLabel(trip.driver.rating, suffix = "(${trip.driver.totalTrips})")
                                Text("Conductor Élite Verificado", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                            }
                            Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Viajes", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(trip.driver.totalTrips.toString(), style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
                                }
                            }
                        }

                        Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IconBubble(Icons.Rounded.DirectionsCar, size = 36, container = PrimaryContainer, tint = PrimaryBlueDark)
                                Column {
                                    Text(trip.driver.vehicle, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text("${trip.driver.color} • Placas: ${trip.driver.plate}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TonalPillButton(
                                text = "Mensaje",
                                icon = Icons.AutoMirrored.Rounded.Chat,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val conv = state.conversations.firstOrNull { it.name.contains(trip.driver.name.substringBefore(' ')) }
                                    if (conv != null) nav.navigate(Route.RideChat(conv.id))
                                    else Toast.makeText(context, "Iniciando chat con ${trip.driver.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            TonalPillButton(
                                text = "Llamar",
                                icon = Icons.Rounded.Call,
                                modifier = Modifier.weight(1f),
                                onClick = { Toast.makeText(context, "Llamando a ${trip.driver.name}", Toast.LENGTH_SHORT).show() }
                            )
                        }
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Itinerario de Ruta", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            StatusPill(trip.dateTag, container = SoftContainer, content = PrimaryBlueDark)
                        }
                        RouteTimeline(
                            departure = trip.departure,
                            origin = trip.origin,
                            originDetail = trip.originDetail,
                            arrival = trip.arrival,
                            destination = trip.destination,
                            destinationDetail = trip.destinationDetail
                        )
                        Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Traffic, contentDescription = null, tint = PrimaryBlueDark, modifier = Modifier.size(18.dp))
                                Text("${trip.durationMin} min de trayecto aproximado en tráfico regular", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Comodidades a bordo", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("${trip.perks.size} incluidas", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            trip.perks.forEach { StatusPill(it, container = SoftContainer, content = TextPrimary) }
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.Shield, size = 40, container = PrimaryContainer, tint = PrimaryBlueDark)
                        Column {
                            Text("Viaje Protegido RutaCompartida", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Incluye seguro de accidentes por plaza y monitoreo satelital continuo.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
