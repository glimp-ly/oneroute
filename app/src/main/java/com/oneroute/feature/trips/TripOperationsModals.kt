package com.oneroute.feature.trips

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppTextField
import com.oneroute.core.components.CounterStepper
import com.oneroute.core.components.DangerPillButton
import com.oneroute.core.components.PrimaryPillButton
import com.oneroute.core.components.SelectChip
import com.oneroute.core.components.StatusPill
import com.oneroute.core.components.TonalPillButton
import com.oneroute.core.navigation.AppState
import com.oneroute.core.theme.DangerContainer
import com.oneroute.core.theme.DangerRed
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.SuccessContainer
import com.oneroute.core.theme.SuccessGreen
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.core.theme.WarningAmber
import com.oneroute.core.theme.WarningContainer
import com.oneroute.data.model.RideRequest
import com.oneroute.data.model.ScheduledTrip
import kotlinx.coroutines.delay

@Composable
fun IncomingRequestsDialog(
    requests: List<RideRequest>,
    state: AppState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rejectTargetId by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("Desvío fuera de ruta operativa") }
    var remainingSeconds by remember(requests.size) { mutableIntStateOf(60) }

    LaunchedEffect(requests.size) {
        if (requests.isNotEmpty()) {
            remainingSeconds = 60
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds--
            }
        }
    }

    if (rejectTargetId != null) {
        AlertDialog(
            onDismissRequest = { rejectTargetId = null },
            title = { Text("Motivo de rechazo") },
            text = {
                AppTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = "Explica brevemente al pasajero",
                    singleLine = false,
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = rejectTargetId!!
                    state.rejectRideRequest(target, rejectReason)
                    rejectTargetId = null
                    Toast.makeText(context, "Solicitud rechazada", Toast.LENGTH_SHORT).show()
                }) { Text("Confirmar rechazo", color = DangerRed) }
            },
            dismissButton = { TextButton(onClick = { rejectTargetId = null }) { Text("Volver") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Solicitudes Entrantes", style = MaterialTheme.typography.titleLarge)
                if (requests.isNotEmpty()) {
                    StatusPill("${remainingSeconds}s", container = WarningContainer, content = WarningAmber, icon = Icons.Rounded.Timer)
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (requests.isEmpty()) {
                    Text("No tienes solicitudes pendientes por responder en este momento.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                } else {
                    if (remainingSeconds == 0) {
                        StatusPill("Tiempo límite alcanzado: se marcarán como expiradas", container = DangerContainer, content = DangerRed)
                    }
                    requests.forEach { req ->
                        Surface(color = SoftContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(req.passengerName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text("S/ ${req.estimatedPrice}.00 PEN", style = MaterialTheme.typography.titleSmall, color = PrimaryBlue)
                                }
                                Text(req.tripTitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text("Recogida: ${req.pickupPoint} → ${req.destinationPoint}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                Text("${req.seatsRequested} asiento(s) • ${req.paymentMethod}", style = MaterialTheme.typography.labelMedium, color = PrimaryBlueDark)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TonalPillButton(
                                        text = "Rechazar",
                                        modifier = Modifier.weight(1f),
                                        onClick = { rejectTargetId = req.id }
                                    )
                                    PrimaryPillButton(
                                        text = "Aceptar",
                                        enabled = remainingSeconds > 0,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            state.acceptRideRequest(req.id)
                                            Toast.makeText(context, "Aceptada: el pasajero tiene 60 s para confirmar", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
fun PassengerHoldConfirmationDialog(
    request: RideRequest,
    state: AppState,
    onDismiss: () -> Unit,
    onConfirmed: (ScheduledTrip) -> Unit
) {
    val context = LocalContext.current
    var secondsLeft by remember(request.id) { mutableIntStateOf(request.remainingSeconds.coerceIn(1, 60)) }

    LaunchedEffect(request.id) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        state.expireRideRequest(request.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Confirma tu reserva", style = MaterialTheme.typography.titleLarge)
                StatusPill("${secondsLeft}s", container = if (secondsLeft <= 15) DangerContainer else PrimaryContainer, content = if (secondsLeft <= 15) DangerRed else PrimaryBlueDark, icon = Icons.Rounded.Timer)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(progress = { secondsLeft / 60f }, modifier = Modifier.fillMaxWidth(), color = PrimaryBlue)
                Text(
                    if (request.autoAccepted) "Aceptación automática activada. Confirma antes de que expire la retención."
                    else "${request.driverName} aceptó tu solicitud y reservó temporalmente tu asiento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(request.tripTitle, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Recogida: ${request.pickupPoint}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Destino: ${request.destinationPoint}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("${request.seatsRequested} asiento(s) • Total: S/ ${request.estimatedPrice}.00 PEN (${request.paymentMethod})", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                    }
                }
                if (secondsLeft == 0) {
                    StatusPill("La retención de 60 s expiró (Intento consumido)", container = DangerContainer, content = DangerRed)
                }
            }
        },
        confirmButton = {
            PrimaryPillButton(
                text = "Confirmar reserva ahora",
                icon = Icons.Rounded.CheckCircle,
                enabled = secondsLeft > 0,
                onClick = {
                    val booked = state.confirmHeldRequest(request.id)
                    if (booked != null) {
                        Toast.makeText(context, "¡Reserva confirmada! Otras solicitudes activas fueron canceladas.", Toast.LENGTH_LONG).show()
                        onConfirmed(booked)
                    } else {
                        Toast.makeText(context, "No se pudo confirmar la reserva", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        },
        dismissButton = {
            TextButton(onClick = {
                state.cancelPendingRequestByPassenger(request.id)
                onDismiss()
            }) { Text("Cancelar retención", color = DangerRed) }
        }
    )
}

@Composable
fun ModifyReservationDialog(
    trip: ScheduledTrip,
    state: AppState,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(initialTab) }
    var newPickup by remember(trip.id) { mutableStateOf(trip.originDetail) }
    var newDest by remember(trip.id) { mutableStateOf(trip.destinationDetail) }
    var priceDelta by remember { mutableIntStateOf(-4) }
    var newSeats by remember(trip.id) { mutableIntStateOf(trip.seatsCount) }
    var paidAlready by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modificar Reserva") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip("Recogida", mode == 0, { mode = 0 })
                    SelectChip("Destino", mode == 1, { mode = 1 })
                    SelectChip("Asientos", mode == 2, { mode = 2 })
                }

                when (mode) {
                    0 -> {
                        if (trip.pickupChangesUsed >= 1) {
                            StatusPill("Ya utilizaste el único cambio de recogida permitido", container = DangerContainer, content = DangerRed)
                        } else {
                            Text("Solo se permite 1 cambio de punto de recogida dentro del desvío permitido.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            AppTextField(value = newPickup, onValueChange = { newPickup = it }, label = "Nuevo punto de recogida", leadingIcon = Icons.Rounded.LocationOn)
                        }
                    }
                    1 -> {
                        if (trip.destinationChangesUsed >= 1) {
                            StatusPill("Ya utilizaste el único cambio de destino permitido", container = DangerContainer, content = DangerRed)
                        } else {
                            Text("Solo se permite 1 cambio de destino sobre la ruta operativa. Si reduce tarifa, se acredita a tu Saldo Pasajero.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            AppTextField(value = newDest, onValueChange = { newDest = it }, label = "Nuevo destino en ruta", leadingIcon = Icons.Rounded.LocationOn)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SelectChip("-S/ 4 (Más cerca)", priceDelta == -4, { priceDelta = -4 })
                                SelectChip("+S/ 6 (Más lejos)", priceDelta == 6, { priceDelta = 6 })
                            }
                        }
                    }
                    else -> {
                        Text("Asientos actuales: ${trip.seatsCount}. Si reduces después de pagar, requiere aprobación del conductor y el saldo queda retenido 24 h.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Nueva cantidad:", style = MaterialTheme.typography.titleSmall)
                            CounterStepper(value = newSeats, onChange = { newSeats = it }, range = 1..4)
                        }
                        if (newSeats < trip.seatsCount) {
                            SelectChip(
                                text = if (paidAlready) "Ya pagado (retención 24 h)" else "Antes de pagar",
                                selected = paidAlready,
                                onClick = { paidAlready = !paidAlready }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ok = when (mode) {
                    0 -> state.modifyTripPickup(trip.id, newPickup)
                    1 -> state.modifyTripDestination(trip.id, newDest, priceDelta)
                    else -> state.modifyTripSeats(trip.id, newSeats, paidAlready)
                }
                Toast.makeText(context, if (ok) "Modificación aplicada" else "No se pudo aplicar la modificación", Toast.LENGTH_SHORT).show()
                if (ok) onDismiss()
            }) { Text("Aplicar cambio") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
fun EarlyFinishDialog(
    trip: ScheduledTrip,
    state: AppState,
    onDismiss: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var dropoffPoint by remember { mutableStateOf("Av. Javier Prado Este cdra. 24 (GPS actual)") }
    val recalculated = (trip.price * 0.7).toInt().coerceAtLeast(5)
    val refund = (trip.price - recalculated).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finalizar Recorrido Anticipado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Se registrará tu descenso real por GPS, se recalculará la tarifa del tramo recorrido y se liberará tu asiento para los siguientes tramos.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                AppTextField(value = dropoffPoint, onValueChange = { dropoffPoint = it }, label = "Punto de descenso", leadingIcon = Icons.Rounded.Flag)
                Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tarifa original: S/ ${trip.price}.00 PEN", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Tarifa recalculada: S/ $recalculated.00 PEN", style = MaterialTheme.typography.titleSmall, color = PrimaryBlue)
                        if (refund > 0) {
                            Text("Diferencia acreditada a Saldo Pasajero: +S/ $refund.00 PEN", style = MaterialTheme.typography.labelMedium, color = SuccessGreen)
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryPillButton(
                text = "Confirmar descenso",
                icon = Icons.Rounded.CheckCircle,
                onClick = {
                    state.finishTripEarly(trip.id, dropoffPoint, recalculated)
                    Toast.makeText(context, "Recorrido finalizado anticipadamente", Toast.LENGTH_LONG).show()
                    onFinished()
                }
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun NoShowWaitDialog(
    trip: ScheduledTrip,
    state: AppState,
    onDismiss: () -> Unit,
    onDeclared: () -> Unit
) {
    val context = LocalContext.current
    var totalSeconds by remember(trip.id) { mutableIntStateOf(5 * 60 + trip.waitExtendedMinutes * 60) }
    var observations by remember { mutableStateOf("") }

    LaunchedEffect(trip.id, trip.waitExtendedMinutes) {
        state.markDriverArrived(trip.id)
        while (totalSeconds > 0) {
            delay(1000L)
            totalSeconds--
        }
    }

    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    val formatted = "%02d:%02d".format(mins, secs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Espera de Abordaje", style = MaterialTheme.typography.titleLarge)
                StatusPill(formatted, container = if (totalSeconds == 0) DangerContainer else PrimaryContainer, content = if (totalSeconds == 0) DangerRed else PrimaryBlueDark, icon = Icons.Rounded.Schedule)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("La espera inicial máxima es de 5 minutos desde la llegada al punto de encuentro. Si el pasajero se comunica, puedes ampliar la espera.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPillButton(
                        text = "+3 min espera",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            state.extendNoShowWait(trip.id, 3)
                            totalSeconds += 180
                        }
                    )
                    TextButton(onClick = { totalSeconds = 0 }) {
                        Text("Simular vencimiento")
                    }
                }
                AppTextField(value = observations, onValueChange = { observations = it }, label = "Observaciones (opcional)")
                if (totalSeconds == 0) {
                    StatusPill("Espera vencida: ya puedes declarar No-Show sin cobro digital", container = WarningContainer, content = WarningAmber, icon = Icons.Rounded.Warning)
                }
            }
        },
        confirmButton = {
            DangerPillButton(
                text = "Declarar No-Show",
                enabled = totalSeconds == 0,
                onClick = {
                    state.declareNoShow(trip.id, observations)
                    Toast.makeText(context, "No-show registrado y capacidad liberada", Toast.LENGTH_LONG).show()
                    onDeclared()
                }
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
fun TrackingLinkDialog(
    trip: ScheduledTrip,
    state: AppState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var shareUrl by remember(trip.id, trip.trackingShareUrl) {
        mutableStateOf(trip.trackingShareUrl ?: state.generateTrackingLink(trip.id))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enlace de Seguimiento Privado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Comparte este enlace temporal con familiares o contactos. No requiere cuenta y expira automáticamente al finalizar el viaje.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Share, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Text(shareUrl, style = MaterialTheme.typography.labelMedium, color = PrimaryBlueDark, modifier = Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryPillButton(
                        text = "Copiar enlace",
                        icon = Icons.Rounded.ContentCopy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            clipboard.setText(AnnotatedString(shareUrl))
                            Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                        }
                    )
                    TonalPillButton(
                        text = "Revocar",
                        icon = Icons.Rounded.LinkOff,
                        modifier = Modifier.weight(0.8f),
                        onClick = {
                            state.revokeTrackingLink(trip.id)
                            Toast.makeText(context, "Enlace revocado", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } }
    )
}
