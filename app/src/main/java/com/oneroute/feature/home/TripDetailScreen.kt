package com.oneroute.feature.home

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
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTextField
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.AvatarBadge
import com.oneroute.core.components.CounterStepper
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.MainTab
import com.oneroute.core.components.MapOverlayPill
import com.oneroute.core.components.PrimaryPillButton
import com.oneroute.core.components.RatingLabel
import com.oneroute.core.components.RouteTimeline
import com.oneroute.core.components.ScreenList
import com.oneroute.core.components.SelectChip
import com.oneroute.core.components.StatusPill
import com.oneroute.core.components.StylizedMapCanvas
import com.oneroute.core.components.TonalPillButton
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.DangerContainer
import com.oneroute.core.theme.DangerRed
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.core.theme.WarningAmber
import com.oneroute.core.theme.WarningContainer
import com.oneroute.data.model.RideRequestStatus
import com.oneroute.feature.trips.PassengerHoldConfirmationDialog
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripDetailScreen(tripId: String, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val trip = state.availableTrip(tripId)

    var showRequestSheet by remember { mutableStateOf(false) }
    var pickupInput by remember(trip?.id) { mutableStateOf(trip?.originDetail ?: "") }
    var destInput by remember(trip?.id) { mutableStateOf(trip?.destinationDetail ?: "") }
    var seatsInput by remember { mutableIntStateOf(1) }
    var selectedPayId by remember(state.selectedPaymentId) { mutableStateOf(state.selectedPaymentId) }
    var activeRequestId by remember { mutableStateOf<String?>(null) }

    val activeRequest = activeRequestId?.let { id -> state.rideRequests.firstOrNull { it.id == id } }
    val attemptsUsed = trip?.let { state.attemptsUsedForTrip(it.id) } ?: 0

    if (showRequestSheet && trip != null) {
        AlertDialog(
            onDismissRequest = { showRequestSheet = false },
            title = { Text("Solicitar Tramo de Viaje") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (trip.autoAccept && trip.gpsReliable) "Aceptación automática disponible: pasarás directo a retención de 60 s."
                        else "El conductor revisará tu solicitud y tendrá 60 s para responder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    AppTextField(value = pickupInput, onValueChange = { pickupInput = it }, label = "Punto de recogida en ruta", leadingIcon = Icons.Rounded.LocationOn)
                    AppTextField(value = destInput, onValueChange = { destInput = it }, label = "Punto de descenso en ruta", leadingIcon = Icons.Rounded.LocationOn)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Asientos:", style = MaterialTheme.typography.titleSmall)
                        CounterStepper(value = seatsInput, onChange = { seatsInput = it }, range = 1..trip.seatsLeft.coerceAtLeast(1))
                    }
                    Text("Método de pago (se cobra al abordar):", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.paymentMethods.forEach { pm ->
                            SelectChip(text = pm.name, selected = selectedPayId == pm.id, onClick = { selectedPayId = pm.id })
                        }
                    }
                    Text("Total estimado: S/ ${trip.price * seatsInput}.00 PEN • Intentos usados: $attemptsUsed/3", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                }
            },
            confirmButton = {
                PrimaryPillButton(
                    text = "Enviar solicitud",
                    onClick = {
                        val methodName = state.paymentMethods.firstOrNull { it.id == selectedPayId }?.name
                        val req = state.requestTrip(trip.id, pickupInput, destInput, seatsInput, methodName)
                        showRequestSheet = false
                        if (req != null) {
                            activeRequestId = req.id
                        } else {
                            Toast.makeText(context, "No se pudo enviar (verifica cupo o límite de 3 intentos)", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            },
            dismissButton = { TextButton(onClick = { showRequestSheet = false }) { Text("Cancelar") } }
        )
    }

    if (activeRequest != null) {
        when (activeRequest.status) {
            RideRequestStatus.PENDIENTE -> {
                var secondsLeft by remember(activeRequest.id) { mutableIntStateOf(60) }
                LaunchedEffect(activeRequest.id) {
                    while (secondsLeft > 0) {
                        delay(1000L)
                        secondsLeft--
                    }
                    state.expireRideRequest(activeRequest.id)
                }
                AlertDialog(
                    onDismissRequest = {},
                    title = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Esperando al conductor", style = MaterialTheme.typography.titleLarge)
                            StatusPill("${secondsLeft}s", container = WarningContainer, content = WarningAmber, icon = Icons.Rounded.Timer)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Tu solicitud fue enviada a ${activeRequest.driverName}. Si cancelas ahora antes de que responda, no consumes un intento.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            TonalPillButton(
                                text = "Simular aceptación del conductor",
                                onClick = { state.acceptRideRequest(activeRequest.id) }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            state.cancelPendingRequestByPassenger(activeRequest.id)
                            activeRequestId = null
                            Toast.makeText(context, "Solicitud cancelada sin consumir intento", Toast.LENGTH_SHORT).show()
                        }) { Text("Cancelar solicitud", color = DangerRed) }
                    }
                )
            }
            RideRequestStatus.ACEPTADA_HOLD -> {
                PassengerHoldConfirmationDialog(
                    request = activeRequest,
                    state = state,
                    onDismiss = { activeRequestId = null },
                    onConfirmed = {
                        activeRequestId = null
                        nav.selectTab(MainTab.MIS_VIAJES)
                    }
                )
            }
            RideRequestStatus.EXPIRADA, RideRequestStatus.RECHAZADA -> {
                AlertDialog(
                    onDismissRequest = { activeRequestId = null },
                    title = { Text(if (activeRequest.status == RideRequestStatus.EXPIRADA) "Solicitud Expirada" else "Solicitud Rechazada") },
                    text = {
                        Text(
                            "Motivo: ${activeRequest.rejectionReason ?: "Tiempo de respuesta vencido"}. Intentos consumidos: ${activeRequest.attemptsUsed}/${activeRequest.maxAttempts}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    },
                    confirmButton = { TextButton(onClick = { activeRequestId = null }) { Text("Entendido") } }
                )
            }
            else -> {}
        }
    }

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
                                Text("S/ ${trip.price}.00 PEN", style = MaterialTheme.typography.headlineSmall, color = PrimaryBlue)
                                Text(
                                    if (trip.autoAccept && trip.gpsReliable) "Aceptación automática • ${trip.distanceKm} km"
                                    else "Precio definido por conductor • ${trip.distanceKm} km",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }

                        if (attemptsUsed >= 3) {
                            StatusPill("Límite de 3 intentos alcanzado para este viaje", container = DangerContainer, content = DangerRed)
                        }

                        PrimaryPillButton(
                            text = when {
                                trip.seatsLeft <= 0 -> "Sin asientos disponibles"
                                attemptsUsed >= 3 -> "Límite de intentos alcanzado"
                                else -> "Solicitar reserva (S/ ${trip.price}.00 PEN)"
                            },
                            icon = Icons.Rounded.CheckCircle,
                            enabled = trip.seatsLeft > 0 && attemptsUsed < 3,
                            onClick = { showRequestSheet = true }
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
                        MapOverlayPill(if (trip.gpsReliable) "GPS confiable" else "GPS desactualizado (solo manual)")
                        StatusPill("${trip.durationMin} min", container = CardSurface, content = TextPrimary, icon = Icons.Rounded.Schedule)
                    }
                    MapOverlayPill(
                        "${trip.distanceKm} km • ${trip.via}",
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
                                Text("Conductor Verificado", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
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
                                    Text("${trip.driver.color} • Placa: ${trip.driver.plate}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
                                Text("${trip.durationMin} min de trayecto aproximado • Cobro al abordar", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
                            Text("Viaje Monitoreado One Route", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Doble confirmación (hold 60 s), chat privado por reserva y monitoreo GPS continuo.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
