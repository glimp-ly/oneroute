package com.onerouteandroid.oneroute.feature.trips

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.AvatarBadge
import com.onerouteandroid.oneroute.core.components.CallButton
import com.onerouteandroid.oneroute.core.components.DangerPillButton
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.RatingLabel
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.components.StylizedMapCanvas
import com.onerouteandroid.oneroute.core.components.StylizedQrCode
import com.onerouteandroid.oneroute.core.components.TonalPillButton
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.data.model.ScheduledTrip
import com.onerouteandroid.oneroute.data.model.TripRole
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.DangerRed
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.data.repository.MockRepository

@Composable
fun LiveNavigationScreen(tripId: String, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val trip = state.scheduledTrip(tripId)
    val driver = trip?.driver ?: MockRepository.carlos
    var muted by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showSos by remember { mutableStateOf(false) }
    var showTracking by remember { mutableStateOf(false) }
    var showEarlyFinish by remember { mutableStateOf(false) }

    val effectiveTrip = trip ?: ScheduledTrip(
        id = tripId, role = TripRole.PASSENGER, dateTag = "HOY", seatInfo = "1 asiento",
        status = "En curso", departure = "18:30", arrival = "19:12",
        origin = "Miraflores", originDetail = "Óvalo", destination = "San Isidro", destinationDetail = "Centro Empresarial",
        durationMin = 42, price = 24, driver = driver
    )

    if (showTracking) {
        TrackingLinkDialog(trip = effectiveTrip, state = state, onDismiss = { showTracking = false })
    }

    if (showEarlyFinish) {
        EarlyFinishDialog(
            trip = effectiveTrip,
            state = state,
            onDismiss = { showEarlyFinish = false },
            onFinished = {
                showEarlyFinish = false
                nav.navigate(Route.TripSummary(effectiveTrip.id))
            }
        )
    }

    val transition = rememberInfiniteTransition(label = "navProgress")
    val liveProgress by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "navFloat"
    )

    if (showQr) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Pase de Abordaje QR") },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StylizedQrCode(code = trip?.boardingCode ?: "8492", modifier = Modifier.size(180.dp).clip(RoundedCornerShape(16.dp)))
                    Text("Muestra este código o PIN 8492 al conductor", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Cerrar") } }
        )
    }

    if (showSos) {
        val contactsText = state.emergencyContacts.joinToString("\n") { "• ${it.name}: ${it.phone}" }
            .ifBlank { "Sin contactos configurados." }
        AlertDialog(
            onDismissRequest = { showSos = false },
            title = { Text("Emergencia SOS 105 / 911") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se enviará tu ubicación GPS en tiempo real al centro de seguridad y a tus contactos:\n$contactsText", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        showSos = false
                        nav.navigate(Route.EmergencyContacts)
                    }) { Text("Configurar contactos") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSos = false
                    Toast.makeText(context, "Alerta SOS enviada • Llamando a emergencias", Toast.LENGTH_LONG).show()
                }) { Text("Confirmar SOS", color = DangerRed) }
            },
            dismissButton = { TextButton(onClick = { showSos = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Navegación En Vivo",
                showBrandLogo = true,
                onBack = { nav.back() },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                StylizedMapCanvas(Modifier.fillMaxSize(), progress = liveProgress)

                Surface(
                    color = CardSurface,
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().padding(14.dp).align(Alignment.TopCenter)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.TurnRight, size = 46, container = PrimaryBlue, tint = Color.White)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("En 200 m", style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
                                Text("carril derecho", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Text("Gira a la derecha en Av. Vasco de Quiroga", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                        IconButton(onClick = { muted = !muted }) {
                            Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Audio", tint = TextSecondary)
                        }
                    }
                }

                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = { showTracking = true },
                        color = CardSurface,
                        shape = CircleShape,
                        shadowElevation = 3.dp
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Share, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Text("Compartir ruta", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                        }
                    }
                    Surface(color = CardSurface, shape = CircleShape, shadowElevation = 3.dp) {
                        IconButton(onClick = { Toast.makeText(context, "Mapa centrado", Toast.LENGTH_SHORT).show() }) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = "Centrar", tint = TextPrimary)
                        }
                    }
                }
            }

            Surface(
                color = CardSurface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.navigationBarsPadding().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("18 min • 8.4 km", style = MaterialTheme.typography.headlineSmall, color = PrimaryBlue)
                            Text("Llegada estimada: ${trip?.arrival ?: "19:12"} hrs", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        StatusPill("EN CAMINO", container = PrimaryContainer, content = PrimaryBlueDark)
                    }

                    Surface(color = SoftContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AvatarBadge(driver.initials, size = 44, verified = driver.verified)
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(driver.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    RatingLabel(driver.rating)
                                }
                                Text("${driver.vehicle} ${driver.color} • ${driver.plate}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            CallButton(onClick = { Toast.makeText(context, "Llamando a ${driver.name}", Toast.LENGTH_SHORT).show() })
                        }
                    }

                    Surface(color = SoftContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    Text("ABORDAJE SEGURO", style = MaterialTheme.typography.labelSmall, color = PrimaryBlueDark)
                                }
                                Text("Muestra al conductor", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = CardSurface, shape = CircleShape, modifier = Modifier.weight(1f)) {
                                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                                        Text("PIN: ", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        Text("8492", style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
                                    }
                                }
                                Button(
                                    onClick = { showQr = true },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Ver QR", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TonalPillButton(
                            text = "Finalizar recorrido",
                            icon = Icons.Rounded.CheckCircle,
                            modifier = Modifier.weight(1f),
                            onClick = { showEarlyFinish = true }
                        )
                        DangerPillButton(
                            text = "SOS",
                            modifier = Modifier.weight(0.6f),
                            onClick = { showSos = true }
                        )
                    }
                }
            }
        }
    }
}
