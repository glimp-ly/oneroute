package com.oneroute.feature.publish

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.MainTab
import com.oneroute.core.components.PrimaryPillButton
import com.oneroute.core.components.RouteTimeline
import com.oneroute.core.components.ScreenList
import com.oneroute.core.components.StatusPill
import com.oneroute.core.components.StylizedQrCode
import com.oneroute.core.components.TonalPillButton
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary

@Composable
fun PublishConfirmationScreen(tripCode: String, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val trip = state.scheduledTrip(tripCode)
    val origin = trip?.origin ?: "Condesa"
    val originDetail = trip?.originDetail ?: "Parque México (Foro Lindbergh)"
    val destination = trip?.destination ?: "Santa Fe"
    val destinationDetail = trip?.destinationDetail ?: "Centro Financiero / Torre Arcos"
    val time = trip?.departure ?: "18:30"
    val arrival = trip?.arrival ?: "19:15"
    val price = trip?.price ?: 195

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Publicación Exitosa",
                showBrandLogo = true,
                onClose = { nav.resetTo(Route.Home) },
                onHelp = { nav.navigate(Route.HelpCenter) },
                avatarInitials = state.currentUser.initials
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Box(
                    Modifier.size(92.dp).clip(CircleShape).background(PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    IconBubble(Icons.Rounded.Check, size = 64, container = PrimaryBlue, tint = Color.White)
                }
                Spacer(Modifier.height(14.dp))
                Text("¡Viaje publicado con éxito!", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tu ruta ya está visible para miles de pasajeros que viajan hacia $destination hoy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            item {
                AppCard(container = SoftContainer, corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            StatusPill("ID: #$tripCode", container = PrimaryContainer, content = PrimaryBlueDark, icon = Icons.Rounded.ConfirmationNumber)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                Text("Hoy a las $time hrs", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                            }
                        }

                        Surface(color = CardSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.padding(14.dp)) {
                                RouteTimeline(
                                    departure = time,
                                    origin = origin,
                                    originDetail = originDetail,
                                    arrival = arrival,
                                    destination = destination,
                                    destinationDetail = destinationDetail
                                )
                            }
                        }

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("GANANCIA PROYECTADA", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("Hasta S/ $price PEN", style = MaterialTheme.typography.headlineSmall, color = PrimaryBlue)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.AirlineSeatReclineNormal, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    Text(trip?.seatInfo ?: "3 asientos disponibles", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StylizedQrCode(code = tripCode, modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)))
                                Text("Pase Conductor", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 22) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = PrimaryBlue)
                            Text("Próximos pasos", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        NextStepRow(1, "Te notificaremos por push y WhatsApp cuando un pasajero reserve un asiento.")
                        NextStepRow(2, "Podrás revisar las calificaciones y el perfil verificado de cada pasajero antes de aceptar.")
                        NextStepRow(3, "Recuerda llegar 5 minutos antes al punto de encuentro en $origin.")
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PrimaryPillButton(
                        text = "Ver en Mis Viajes",
                        icon = Icons.Rounded.DirectionsCar,
                        onClick = { nav.selectTab(MainTab.MIS_VIAJES) }
                    )
                    TonalPillButton(
                        text = "Compartir enlace del viaje",
                        icon = Icons.Rounded.Share,
                        onClick = { Toast.makeText(context, "Enlace del viaje #$tripCode copiado", Toast.LENGTH_SHORT).show() }
                    )
                    TextButton(onClick = { nav.resetTo(Route.Home) }) {
                        Text("Ir al Inicio", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun NextStepRow(step: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(PrimaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(step.toString(), style = MaterialTheme.typography.labelMedium, color = PrimaryBlueDark)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
