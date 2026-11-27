package com.onerouteandroid.oneroute.feature.trips

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.AvatarBadge
import com.onerouteandroid.oneroute.core.components.CallButton
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.MapOverlayPill
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.RatingLabel
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
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.SuccessContainer
import com.onerouteandroid.oneroute.core.theme.SuccessGreen
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.data.repository.MockRepository

@Composable
fun MeetingPointScreen(tripId: String, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val trip = state.scheduledTrip(tripId)
    val driver = trip?.driver ?: MockRepository.carlos
    val boardingCode = trip?.boardingCode ?: "#4192"
    val firstName = driver.name.substringBefore(' ')
    var waitingNotified by rememberSaveable(tripId) { mutableStateOf(false) }
    var showNoShowDialog by rememberSaveable { mutableStateOf(false) }
    var showModifyPickup by rememberSaveable { mutableStateOf(false) }
    var showTrackingDialog by rememberSaveable { mutableStateOf(false) }

    val activeTrip = trip ?: ScheduledTrip(
        id = tripId, role = TripRole.PASSENGER, dateTag = "HOY", seatInfo = "1 asiento",
        status = "Confirmado", departure = "18:30", arrival = "19:15",
        origin = "Miraflores", originDetail = "Óvalo de Miraflores",
        destination = "San Isidro", destinationDetail = "Centro Empresarial",
        durationMin = 45, price = 24, driver = driver, boardingCode = boardingCode
    )

    if (showNoShowDialog) {
        NoShowWaitDialog(
            trip = activeTrip,
            state = state,
            onDismiss = { showNoShowDialog = false },
            onDeclared = {
                showNoShowDialog = false
                nav.selectTab(MainTab.MIS_VIAJES)
            }
        )
    }

    if (showModifyPickup) {
        ModifyReservationDialog(
            trip = activeTrip,
            state = state,
            initialTab = 0,
            onDismiss = { showModifyPickup = false }
        )
    }

    if (showTrackingDialog) {
        TrackingLinkDialog(
            trip = activeTrip,
            state = state,
            onDismiss = { showTrackingDialog = false }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Punto de Encuentro",
                subtitle = "Conversación del viaje",
                showBrandLogo = true,
                onBack = { nav.back() },
                actions = {
                    CallButton(onClick = { Toast.makeText(context, "Llamando a ${driver.name}", Toast.LENGTH_SHORT).show() })
                }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
        ) {
            StylizedMapCanvas(Modifier.fillMaxWidth().height(230.dp), progress = 0.7f) {
                MapOverlayPill(
                    "$firstName • 4 min",
                    container = TextPrimary,
                    content = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                )
                MapOverlayPill(
                    "Tú a 45 m",
                    modifier = Modifier.align(Alignment.Center)
                )
                MapOverlayPill(
                    "Av. Michoacán esquina Av. México",
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                )
            }

            Surface(
                color = CardSurface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.navigationBarsPadding().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("PUNTO DE RECOGIDA VALIDADO", container = SuccessContainer, content = SuccessGreen, icon = Icons.Rounded.CheckCircle)
                        StatusPill("Sin fotomultas", container = SoftContainer, content = TextSecondary)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Av. México 123, Frente a Foro Lindbergh", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Text("Hipódromo Condesa, Cuauhtémoc, CDMX", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }

                    AppCard(container = SoftContainer, corner = 18) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBubble(Icons.Rounded.Schedule, size = 40, container = PrimaryContainer, tint = PrimaryBlue)
                            Column(Modifier.weight(1f)) {
                                Text("$firstName llega en 4 min", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("Distancia actual: 1.2 km aprox.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            StatusPill("Fluido", container = SuccessContainer, content = SuccessGreen, icon = Icons.Rounded.Traffic)
                        }
                    }

                    AppCard(corner = 20) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AvatarBadge(driver.initials, size = 50, verified = driver.verified)
                                Column(Modifier.weight(1f)) {
                                    Text(driver.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(driver.vehicle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    RatingLabel(driver.rating, suffix = "• ${driver.color}")
                                }
                                IconBubble(Icons.Rounded.DirectionsCar, size = 44, container = SoftContainer, tint = PrimaryBlueDark)
                            }

                            Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("IDENTIFICADOR DE PLACA", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Surface(color = PrimaryContainer, shape = RoundedCornerShape(10.dp)) {
                                        Text(driver.plate, style = MaterialTheme.typography.titleMedium, color = PrimaryBlueDark, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TonalPillButton(
                            text = "Llamar (Voz)",
                            icon = Icons.Rounded.Call,
                            modifier = Modifier.weight(1f),
                            onClick = { Toast.makeText(context, "Llamando a ${driver.name}", Toast.LENGTH_SHORT).show() }
                        )
                        TonalPillButton(
                            text = "Volver al Chat",
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val conv = state.conversations.firstOrNull { it.name.contains(firstName) }
                                if (conv != null) nav.navigate(Route.RideChat(conv.id)) else nav.back()
                            }
                        )
                    }

                    AppCard(container = SoftContainer, corner = 20) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = PrimaryBlue)
                                Text("Consejos para encontrarse rápido", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            }
                            TipRow(1, "El conductor encenderá las luces intermitentes al detenerse en la bahía de Foro Lindbergh.")
                            TipRow(2, "Verifica el código de abordaje seguro: $boardingCode al subir.")
                            TipRow(3, "No abordes en doble fila; la bahía señalizada mantiene el flujo vehicular seguro.")
                        }
                    }

                    PrimaryPillButton(
                        text = if (waitingNotified) "Aviso enviado a $firstName" else "Ya estoy esperando en la banqueta",
                        icon = if (waitingNotified) Icons.Rounded.CheckCircle else Icons.Rounded.PanTool,
                        enabled = !waitingNotified,
                        onClick = {
                            waitingNotified = true
                            Toast.makeText(context, "Notificación enviada a $firstName", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TonalPillButton(
                            text = "Cambiar recogida",
                            modifier = Modifier.weight(1f),
                            onClick = { showModifyPickup = true }
                        )
                        TonalPillButton(
                            text = "Compartir ruta",
                            modifier = Modifier.weight(1f),
                            onClick = { showTrackingDialog = true }
                        )
                    }
                    TonalPillButton(
                        text = "Espera 5 min / Marcar No-Show",
                        icon = Icons.Rounded.Schedule,
                        onClick = { showNoShowDialog = true }
                    )
                    Text(
                        "Esto le enviará una notificación con vibración inmediata a $firstName",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TipRow(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(PrimaryContainer), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.labelSmall, color = PrimaryBlueDark)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
