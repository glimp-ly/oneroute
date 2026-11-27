package com.oneroute.feature.trips

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
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.AvatarBadge
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.PrimaryPillButton
import com.oneroute.core.components.RatingLabel
import com.oneroute.core.components.RouteTimeline
import com.oneroute.core.components.ScreenList
import com.oneroute.core.components.StatusPill
import com.oneroute.core.components.TonalPillButton
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.EcoContainer
import com.oneroute.core.theme.EcoTeal
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.SuccessContainer
import com.oneroute.core.theme.SuccessGreen
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.data.repository.MockRepository

@Composable
fun TripSummaryScreen(tripId: String, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val historyItem = state.historyTrips.firstOrNull { it.id == "ht-done-$tripId" || it.id == tripId }
    val scheduledItem = state.scheduledTrip(tripId)
    val driver = scheduledItem?.driver ?: MockRepository.carlos
    val origin = historyItem?.origin ?: scheduledItem?.origin ?: "Condesa"
    val destination = historyItem?.destination ?: scheduledItem?.destination ?: "Santa Fe"
    val price = historyItem?.amount ?: scheduledItem?.price ?: 65
    val vehicleLabel = "${driver.vehicle} • ${driver.color}"

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Viaje Finalizado",
                onBack = { nav.back() },
                onClose = { nav.resetTo(Route.Home) },
                avatarInitials = state.currentUser.initials
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(SoftContainer), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(62.dp).clip(CircleShape).background(PrimaryContainer), contentAlignment = Alignment.Center) {
                            IconBubble(Icons.Rounded.Check, size = 44, container = PrimaryBlue, tint = Color.White)
                        }
                    }
                    Text("¡Has llegado a tu destino!", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
                    Text("Viaje completado con éxito • Hoy, 19:18 hrs", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    StatusPill("$origin  ➔  $destination", container = PrimaryContainer, content = PrimaryBlueDark, icon = Icons.Rounded.NearMe)
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Impacto del viaje compartido", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Text("M3 Analytics", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(Icons.Rounded.Savings, "$145.00", "Ahorro vs. Taxi/VTC", "-68%", Modifier.weight(1f))
                        MetricCard(Icons.Rounded.Eco, "3.4 kg", "CO₂ evitado hoy", "Huella", Modifier.weight(1f), eco = true)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(Icons.Rounded.Speed, "14.8 km", "Distancia total", null, Modifier.weight(1f))
                        MetricCard(Icons.Rounded.Schedule, "42 min", "Tiempo a bordo", null, Modifier.weight(1f))
                    }
                }
            }

            item {
                AppCard(container = PrimaryContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.Park, size = 40, container = CanvasBackground, tint = PrimaryBlue)
                        Text(
                            "Tu viaje compartido equivale a salvar 1 árbol joven urbano 🌱 ¡Gracias por reducir el tráfico en CDMX!",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryBlueDark
                        )
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AvatarBadge(driver.initials, size = 48, verified = driver.verified)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(driver.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    driver.badge?.let { StatusPill(it, container = SoftContainer, content = PrimaryBlueDark) }
                                }
                                RatingLabel(driver.rating, suffix = "• ${driver.totalTrips} viajes completados")
                            }
                            IconButton(
                                onClick = {
                                    val conv = state.conversations.firstOrNull { it.name.contains(driver.name.substringBefore(' ')) }
                                    if (conv != null) nav.navigate(Route.RideChat(conv.id))
                                    else Toast.makeText(context, "Abriendo chat", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(38.dp).background(SoftContainer, CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "Chat", tint = PrimaryBlueDark, modifier = Modifier.size(18.dp))
                            }
                        }

                        Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                    Text(vehicleLabel, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                }
                                StatusPill(driver.plate, container = PrimaryContainer, content = PrimaryBlueDark)
                            }
                        }

                        RouteTimeline(
                            departure = scheduledItem?.departure ?: "18:35 hrs",
                            origin = "$origin (Parque México)",
                            originDetail = "Av. México esq. Michoacán",
                            arrival = scheduledItem?.arrival ?: "19:17 hrs",
                            destination = "$destination (Torre Arcos)",
                            destinationDetail = "Paseo de los Tamarindos 400"
                        )
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TOTAL COBRADO", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("S/ $price.00 PEN", style = MaterialTheme.typography.headlineSmall, color = PrimaryBlue)
                            StatusPill("Cobro exitoso", container = SuccessContainer, content = SuccessGreen, icon = Icons.Rounded.Check)
                        }
                        Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = TextSecondary)
                                    Column {
                                        Text("Tarjeta de Débito", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        Text("Mastercard •• 8912", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                                Text("Auto-debit", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                        TonalPillButton(
                            text = "Descargar recibo fiscal digital (CFDI / PDF)",
                            icon = Icons.Rounded.Download,
                            onClick = { Toast.makeText(context, "Descargando recibo CFDI/PDF...", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryPillButton(
                        text = "Calificar a ${driver.name}",
                        icon = Icons.Rounded.Star,
                        onClick = { nav.navigate(Route.RateUser(tripId, driver.name, "$vehicleLabel • ${driver.plate}")) }
                    )
                    TonalPillButton(
                        text = "Reportar usuario / incidente",
                        icon = Icons.Rounded.Report,
                        onClick = { nav.navigate(Route.SafetyReports(tripId, initialTab = 1)) }
                    )
                    TonalPillButton(
                        text = "Volver al Inicio",
                        icon = Icons.Rounded.Home,
                        onClick = { nav.resetTo(Route.Home) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    badge: String?,
    modifier: Modifier = Modifier,
    eco: Boolean = false
) {
    AppCard(modifier = modifier, container = SoftContainer, corner = 20) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon, size = 34, container = if (eco) EcoContainer else PrimaryContainer, tint = if (eco) EcoTeal else PrimaryBlue)
                if (badge != null) StatusPill(badge, container = if (eco) EcoContainer else PrimaryContainer, content = if (eco) EcoTeal else PrimaryBlueDark)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
