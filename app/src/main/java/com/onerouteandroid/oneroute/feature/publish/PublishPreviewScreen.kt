package com.onerouteandroid.oneroute.feature.publish

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
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
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.AvatarBadge
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.MapOverlayPill
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
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
fun PublishPreviewScreen(state: AppState, nav: AppNavigator) {
    val draft = state.tripDraft
    val vehicle = state.vehicle

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Resumen de Publicación",
                showBrandLogo = true,
                onBack = { nav.back() },
                onHelp = { nav.navigate(Route.HelpCenter) },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        bottomBar = {
            Surface(color = CardSurface, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TonalPillButton(
                        text = "Editar",
                        icon = Icons.Rounded.Edit,
                        modifier = Modifier.weight(0.9f),
                        onClick = { nav.back() }
                    )
                    PrimaryPillButton(
                        text = "Publicar viaje ahora",
                        trailingIcon = Icons.Rounded.RocketLaunch,
                        modifier = Modifier.weight(1.6f),
                        onClick = {
                            val published = state.publishDraftTrip()
                            nav.resetTo(Route.Home)
                            nav.navigate(Route.PublishConfirmation(published.id))
                        }
                    )
                }
            }
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatusPill("Paso final de publicación", container = PrimaryContainer, content = PrimaryBlueDark, icon = Icons.Rounded.CheckCircle)
                    Text("Publicación rápida", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Revisa las condiciones antes de poner tu ruta a disposición de los pasajeros de la comunidad.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            item {
                AppCard(container = SoftContainer, corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Tus ganancias estimadas", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text("$${draft.earnings}.00 MXN", style = MaterialTheme.typography.headlineMedium, color = PrimaryBlue)
                                Text("Basado en ${draft.seats} asientos ocupados a $${draft.pricePerSeat}.00 c/u", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            IconBubble(Icons.Rounded.Payments, size = 48, container = PrimaryBlue, tint = Color.White)
                        }

                        Surface(color = CardSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                BreakdownRow(Icons.Rounded.Payments, "Tarifa base conductor", "${draft.seats} × $${draft.pricePerSeat}.00 MXN")
                                BreakdownRow(Icons.Rounded.Verified, "Comisión de plataforma", "0% (Primeros 5 viajes)")
                                BreakdownRow(Icons.Rounded.LocalGasStation, "Ahorro en combustible", "~60% recuperado")
                                BreakdownRow(Icons.Rounded.Eco, "Impacto ambiental", "~8.6 kg CO₂ evitados")
                            }
                        }

                        Text("Depósito directo: CLABE terminación ••4109", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Itinerario y Recorrido", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            StatusPill(draft.dateLabel, container = SoftContainer, content = TextSecondary)
                        }
                        StylizedMapCanvas(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)), progress = 0.5f) {
                            Row(
                                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MapOverlayPill("14.8 km • ~45 min", container = TextPrimary.copy(alpha = 0.78f), content = Color.White)
                                MapOverlayPill("Vía Constituyentes", container = TextPrimary.copy(alpha = 0.78f), content = Color.White)
                            }
                        }
                        RouteTimeline(
                            departure = "${draft.time} hrs",
                            origin = draft.origin,
                            originDetail = "${draft.pickupPoint} • ${draft.pickupReference}",
                            arrival = "~19:15 hrs",
                            destination = draft.destination,
                            destinationDetail = "Bahía vehicular principal"
                        )
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Vehículo & Espacios", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            StatusPill("Verificado", container = SoftContainer, content = TextPrimary, icon = Icons.Rounded.Shield)
                        }
                        Surface(color = SoftContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconBubble(Icons.Rounded.DirectionsCar, size = 44, container = PrimaryContainer, tint = PrimaryBlueDark)
                                Column {
                                    Text("${vehicle.model} ${vehicle.year}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text("${vehicle.colorName} • Placas ${vehicle.plate}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Capacidad de viaje", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("${draft.seats} plazas reservables", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SeatBox("Conductor", "Tú", active = false, modifier = Modifier.weight(1f))
                            SeatBox("Copiloto", if (draft.seats >= 1) "Disponible" else "Cerrado", active = draft.seats >= 1, modifier = Modifier.weight(1f))
                            SeatBox("Atrás Izq.", if (draft.seats >= 2) "Disponible" else "Cerrado", active = draft.seats >= 2, modifier = Modifier.weight(1f))
                            SeatBox("Atrás Der.", if (draft.seats >= 3) "Disponible" else "Cerrado", active = draft.seats >= 3, modifier = Modifier.weight(1f))
                        }

                        Text("Reglas a bordo aceptadas", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            draft.rules.forEach { StatusPill(it, container = SoftContainer, content = TextPrimary) }
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.Shield, size = 40, container = CardSurface, tint = PrimaryBlueDark)
                        Column {
                            Text("Cobertura Total GNP & RutaCompartida", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                "Viaje 100% protegido con póliza de seguro GNP para ocupantes y monitoreo telemático GPS satelital 24/7.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarBadge(state.currentUser.initials, size = 36)
                    Text(
                        "Publicas como ${state.currentUser.firstName} (${state.currentUser.rating} ★ • ${state.currentUser.sharedTrips} viajes). Los pasajeros podrán enviarte solicitudes al instante.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    }
}

@Composable
private fun SeatBox(label: String, status: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (active) PrimaryContainer else SoftContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                Icons.Rounded.AirlineSeatReclineNormal,
                contentDescription = null,
                tint = if (active) PrimaryBlueDark else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            Text(status, style = MaterialTheme.typography.labelSmall, color = if (active) PrimaryBlue else TextMuted)
        }
    }
}
