package com.oneroute.feature.trips

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTextField
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.AvatarBadge
import com.oneroute.core.components.DangerPillButton
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.MainTab
import com.oneroute.core.components.ScreenList
import com.oneroute.core.components.StatusPill
import com.oneroute.core.components.TonalPillButton
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.DangerContainer
import com.oneroute.core.theme.DangerRed
import com.oneroute.core.theme.OutlineSoft
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary

private val cancelReasons = listOf(
    "Cambio imprevisto de planes o itinerario",
    "Punto de recogida muy lejano o inaccesible",
    "Tiempo de espera excesivo / retraso reportado",
    "Encontré otro medio de transporte",
    "Emergencia personal / de salud",
    "Otro motivo"
)

@Composable
fun CancelTripScreen(tripId: String, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val trip = state.scheduledTrip(tripId) ?: state.scheduledTrips.firstOrNull()
    val driverName = trip?.driver?.name ?: "Carlos Mendoza"
    val price = trip?.price ?: 65
    var reasonIndex by rememberSaveable { mutableIntStateOf(0) }
    var comments by rememberSaveable { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    val commentRequired = reasonIndex == cancelReasons.lastIndex
    val canConfirm = !commentRequired || comments.trim().length >= 4

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("¿Confirmar cancelación?") },
            text = { Text("Se cancelará tu lugar en la ruta hacia ${trip?.destination ?: "Santa Fe"} y se procesará tu reembolso.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    state.cancelScheduledTrip(trip?.id ?: tripId, cancelReasons[reasonIndex])
                    Toast.makeText(context, "Viaje cancelado. Reembolso acreditado.", Toast.LENGTH_LONG).show()
                    nav.selectTab(MainTab.MIS_VIAJES)
                }) { Text("Sí, cancelar", color = DangerRed) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Volver") } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Cancelar Viaje",
                onBack = { nav.back() },
                onHelp = { nav.navigate(Route.HelpCenter) },
                avatarInitials = state.currentUser.initials
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, modifier = Modifier.imePadding()) {
            item {
                AppCard(container = DangerContainer, corner = 22) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBubble(Icons.Rounded.Warning, size = 38, container = DangerRed.copy(alpha = 0.14f), tint = DangerRed)
                            Column {
                                Text("¿Seguro que deseas cancelar este viaje?", style = MaterialTheme.typography.titleMedium, color = DangerRed)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Si cancelas con más de 1 hora de anticipación, recibirás un reembolso del 100%. Los conductores y pasajeros dependen de tu compromiso.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DangerRed
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                            Text("0 penalizaciones pendientes en tu perfil", style = MaterialTheme.typography.labelMedium, color = DangerRed)
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("RESUMEN DE LA RESERVA", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            StatusPill("Reembolsable", container = PrimaryContainer, content = PrimaryBlueDark)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AvatarBadge(driverName.split(' ').take(2).joinToString("") { it.take(1) }, size = 40, verified = true)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(driverName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                    Text("${trip?.dateTag ?: "Hoy"}, ${trip?.departure ?: "18:30"} hrs", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                            Text("$$price.00 MXN", style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
                        }
                        Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(PrimaryBlue))
                                    Text("${trip?.origin ?: "Condesa"} (${trip?.originDetail ?: "Parque México"})", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                }
                                Box(Modifier.padding(start = 3.dp).width(2.dp).height(14.dp).background(OutlineSoft))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(DangerRed))
                                    Text("${trip?.destination ?: "Santa Fe"} (${trip?.destinationDetail ?: "Centro Comercial"})", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Motivo de cancelación", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Requerido", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Spacer(Modifier.height(8.dp))
                AppCard(corner = 20) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        cancelReasons.forEachIndexed { idx, reason ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { reasonIndex = idx }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(reason, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                                RadioButton(
                                    selected = reasonIndex == idx,
                                    onClick = { reasonIndex = idx },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue, unselectedColor = TextMuted)
                                )
                            }
                        }
                    }
                }
            }

            item {
                AppTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = if (commentRequired) "Describe el motivo (requerido)" else "Comentarios adicionales (opcional)",
                    placeholder = "Cuéntanos más detalles para mejorar el servicio...",
                    singleLine = false,
                    minLines = 3
                )
            }

            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.CreditCard, size = 38, container = PrimaryContainer, tint = PrimaryBlue)
                        Column {
                            Text("Política de Reembolso", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                "Reembolso inmediato de $$price.00 MXN a tu tarjeta terminada en ••8912 (1-2 días hábiles según tu banco emisor).",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DangerPillButton(
                        text = "Confirmar cancelación",
                        icon = Icons.Rounded.Cancel,
                        enabled = canConfirm,
                        onClick = { showConfirm = true }
                    )
                    TonalPillButton(
                        text = "Mantener mi reserva",
                        onClick = { nav.back() }
                    )
                }
            }
        }
    }
}
