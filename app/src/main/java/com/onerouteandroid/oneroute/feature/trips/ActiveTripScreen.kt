package com.onerouteandroid.oneroute.feature.trips

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.AvatarBadge
import com.onerouteandroid.oneroute.core.components.DangerPillButton
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.MapOverlayPill
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.RatingLabel
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SectionHeader
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.components.StylizedMapCanvas
import com.onerouteandroid.oneroute.core.components.TonalPillButton
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.data.model.ScheduledTrip
import com.onerouteandroid.oneroute.data.model.TripRole
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.DangerRed
import com.onerouteandroid.oneroute.core.theme.OutlineSoft
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.SuccessContainer
import com.onerouteandroid.oneroute.core.theme.SuccessGreen
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary

private data class TripPassenger(val name: String, val initials: String, val rating: Double, val trips: Int, val seat: String, val status: String, val destination: String, val extra: String, val verified: Boolean)

private val activePassengers = listOf(
    TripPassenger("Sofía Morales", "SM", 4.9, 32, "Copiloto", "A bordo (QR Validado)", "Torre Arcos", "", true),
    TripPassenger("Diego Valdés", "DV", 4.8, 18, "Trasero Izq.", "A bordo (PIN: 8492)", "Samara Shops", "", false),
    TripPassenger("Andrea Ruiz", "AR", 5.0, 45, "Trasero Der.", "Punto próximo (en 400m)", "Espera en acera", "", true)
)

@Composable
fun ActiveTripScreen(tripId: String, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val trip = state.scheduledTrip(tripId) ?: state.scheduledTrips.firstOrNull()
    val origin = trip?.origin ?: "Condesa"
    val originDetail = trip?.originDetail ?: "Parque México"
    val destination = trip?.destination ?: "Santa Fe"
    val destinationDetail = trip?.destinationDetail ?: "Torre Arcos"
    val departure = trip?.departure ?: "18:35"
    var showSosDialog by remember { mutableStateOf(false) }
    var showTrackingDialog by remember { mutableStateOf(false) }
    var showEarlyFinishDialog by remember { mutableStateOf(false) }
    var showModifyDestDialog by remember { mutableStateOf(false) }

    val effectiveTrip = trip ?: ScheduledTrip(
        id = tripId, role = TripRole.PASSENGER, dateTag = "HOY", seatInfo = "1 asiento",
        status = "En curso", departure = departure, arrival = "19:15",
        origin = origin, originDetail = originDetail, destination = destination, destinationDetail = destinationDetail,
        durationMin = 45, price = 24
    )

    if (showTrackingDialog) {
        TrackingLinkDialog(trip = effectiveTrip, state = state, onDismiss = { showTrackingDialog = false })
    }

    if (showEarlyFinishDialog) {
        EarlyFinishDialog(
            trip = effectiveTrip,
            state = state,
            onDismiss = { showEarlyFinishDialog = false },
            onFinished = {
                showEarlyFinishDialog = false
                nav.navigate(Route.TripSummary(effectiveTrip.id))
            }
        )
    }

    if (showModifyDestDialog) {
        ModifyReservationDialog(trip = effectiveTrip, state = state, initialTab = 1, onDismiss = { showModifyDestDialog = false })
    }

    if (showSosDialog) {
        val contactsText = state.emergencyContacts.joinToString("\n") { "• ${it.name} (${it.relationship}): ${it.phone}" }
            .ifBlank { "Sin contactos configurados." }
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            title = { Text("Protocolo de Emergencia SOS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se registrará tu ubicación GPS y se enviará alerta a la plataforma y a tus contactos de confianza:", style = MaterialTheme.typography.bodySmall)
                    Text(contactsText, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    TextButton(onClick = {
                        showSosDialog = false
                        nav.navigate(Route.EmergencyContacts)
                    }) { Text("Administrar contactos de emergencia") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSosDialog = false
                    Toast.makeText(context, "Alerta SOS activada • Llamando a emergencias 105", Toast.LENGTH_LONG).show()
                }) { Text("Activar SOS / Llamar 105", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showSosDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Viaje Activo",
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
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            StatusPill("EN CURSO", container = PrimaryBlue, content = Color.White)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                Text("Llegada: ${trip?.arrival ?: "19:12"} hrs", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text("22 min restantes", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                            Text("11.4 km de 18 km", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }

                        LinearProgressIndicator(
                            progress = { 0.65f },
                            color = PrimaryBlue,
                            trackColor = OutlineSoft,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("65% completado", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            StatusPill("Fluido", container = SuccessContainer, content = SuccessGreen, icon = Icons.Rounded.Traffic)
                        }

                        StylizedMapCanvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            progress = 0.65f
                        ) {
                            Box(Modifier.matchParentSize().padding(10.dp)) {
                                Surface(
                                    onClick = { nav.navigate(Route.LiveNavigation(trip?.id ?: tripId)) },
                                    color = CardSurface.copy(alpha = 0.92f),
                                    shape = CircleShape,
                                    modifier = Modifier.align(Alignment.BottomStart)
                                ) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Rounded.Navigation, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                        Text("Constituyentes Pte. • Abrir guía", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                                    }
                                }
                                StatusPill(
                                    "GPS Activo",
                                    container = PrimaryBlue,
                                    content = Color.White,
                                    icon = Icons.Rounded.GpsFixed,
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                )
                            }
                        }
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Detalles de Ruta", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            StatusPill("Trayecto Compartido", container = PrimaryContainer, content = PrimaryBlueDark)
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(PrimaryBlue))
                                Box(Modifier.width(2.dp).height(44.dp).background(OutlineSoft))
                                Box(Modifier.size(12.dp).clip(CircleShape).background(SuccessGreen))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("$origin ($originDetail)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        Text("$departure hrs", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                    }
                                    Text("Punto de partida inicial • Completado", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("$destination ($destinationDetail)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        StatusPill("Próxima parada", container = SoftContainer, content = PrimaryBlue)
                                    }
                                    Text("Tráfico fluido por Vía Constituyentes (12 min restantes)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("Pasajeros del Viaje", badge = "3 a bordo / esperados")
            }

            items(activePassengers, key = { it.name }) { p ->
                AppCard(corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarBadge(p.initials, size = 44, verified = p.verified)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                if (p.verified) Icon(Icons.Rounded.Verified, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                            }
                            RatingLabel(p.rating, suffix = "• ${p.trips} viajes • ${p.seat}")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(p.status, container = SoftContainer, content = PrimaryBlueDark, icon = if (p.status.contains("QR")) Icons.Rounded.QrCode2 else Icons.Rounded.Pin)
                                Text(p.destination, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { Toast.makeText(context, "Llamando a ${p.name}", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.size(36.dp).background(SoftContainer, CircleShape)
                            ) { Icon(Icons.Rounded.Call, contentDescription = "Llamar", tint = PrimaryBlueDark, modifier = Modifier.size(18.dp)) }
                            IconButton(
                                onClick = { Toast.makeText(context, "Abriendo chat con ${p.name}", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.size(36.dp).background(SoftContainer, CircleShape)
                            ) { Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "Chat", tint = PrimaryBlueDark, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }

            item {
                Surface(
                    color = SoftContainer,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Shield, contentDescription = null, tint = PrimaryBlue)
                                Text("Seguridad y Asistencia", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            }
                            StatusPill("24/7 Activo", container = PrimaryBlue, content = Color.White)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(color = CardSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("Monitoreo Satelital", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text("Cabina Protegida", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                }
                            }
                            Surface(color = CardSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("PIN de Descenso", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text("#4920-SE", style = MaterialTheme.typography.titleSmall, color = PrimaryBlue)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Surface(onClick = { showSosDialog = true }, color = CardSurface, shape = CircleShape) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(14.dp))
                                    Text("Botón SOS disponible", style = MaterialTheme.typography.labelMedium, color = DangerRed)
                                }
                            }
                            Text("Protocolo Seguro ›", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryPillButton(
                        text = "Generar enlace de seguimiento",
                        icon = Icons.Rounded.LocationOn,
                        onClick = { showTrackingDialog = true }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TonalPillButton(
                            text = "Cambiar destino",
                            modifier = Modifier.weight(1f),
                            onClick = { showModifyDestDialog = true }
                        )
                        TonalPillButton(
                            text = "Finalizar antes",
                            modifier = Modifier.weight(1f),
                            onClick = { showEarlyFinishDialog = true }
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TonalPillButton(
                            text = "Finalizar viaje",
                            icon = Icons.Rounded.Flag,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val targetId = trip?.id ?: tripId
                                state.completeActiveTrip(targetId)
                                nav.navigate(Route.TripSummary(targetId))
                            }
                        )
                        DangerPillButton(
                            text = "Cancelar",
                            tonal = true,
                            modifier = Modifier.weight(1f),
                            onClick = { nav.navigate(Route.CancelTrip(trip?.id ?: tripId)) }
                        )
                    }
                    TonalPillButton(
                        text = "Reportar incidente o usuario",
                        icon = Icons.Rounded.Warning,
                        onClick = { nav.navigate(Route.SafetyReports(effectiveTrip.id, initialTab = 0)) }
                    )
                }
            }
        }
    }
}
