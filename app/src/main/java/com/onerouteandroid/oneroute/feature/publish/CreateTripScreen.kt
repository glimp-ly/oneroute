package com.onerouteandroid.oneroute.feature.publish

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.onerouteandroid.oneroute.core.components.AppTextField
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.CounterStepper
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.MapOverlayPill
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SelectChip
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.components.StylizedMapCanvas
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
import kotlin.math.roundToInt

private val luggageOptions = listOf(
    "Pequeño (Mochila)",
    "Medio (Mochila / Maletín)",
    "Grande (Maleta)"
)

private val ruleOptions = listOf(
    "Solo mujeres",
    "No fumar",
    "Mascotas bienvenidas",
    "Aire acondicionado",
    "Música a bordo"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTripScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val draft = state.tripDraft
    val valid = draft.origin.isNotBlank() && draft.destination.isNotBlank()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Publicar Ruta",
                showBrandLogo = true,
                onClose = { nav.back() },
                onHelp = { nav.navigate(Route.HelpCenter) },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        bottomBar = {
            Surface(color = CardSurface, shadowElevation = 6.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    PrimaryPillButton(
                        text = "Siguiente: Ajustar ruta en mapa",
                        trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                        enabled = valid,
                        onClick = { nav.navigate(Route.MapPointPicker) }
                    )
                }
            }
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, modifier = Modifier.imePadding()) {
            item {
                StylizedMapCanvas(
                    modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(22.dp)),
                    progress = 0.2f
                ) {
                    Row(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MapOverlayPill("18.4 km • ~38 min", container = TextPrimary.copy(alpha = 0.78f), content = Color.White)
                        MapOverlayPill("Tráfico habitual", container = TextPrimary.copy(alpha = 0.78f), content = Color.White)
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Trayecto del Viaje", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                Text("Rutas usuales", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                            }
                        }

                        AppTextField(
                            value = draft.origin,
                            onValueChange = { v -> state.updateDraft { it.copy(origin = v) } },
                            label = "Punto de partida",
                            leadingIcon = Icons.Rounded.MyLocation
                        )
                        AppTextField(
                            value = draft.destination,
                            onValueChange = { v -> state.updateDraft { it.copy(destination = v) } },
                            label = "Destino final",
                            leadingIcon = Icons.Rounded.LocationOn
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = { Toast.makeText(context, "Parada intermedia disponible próximamente", Toast.LENGTH_SHORT).show() },
                                color = SoftContainer,
                                shape = CircleShape
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    Text("Añadir parada intermedia", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                                }
                            }
                            Surface(
                                onClick = { state.updateDraft { it.copy(origin = it.destination, destination = it.origin) } },
                                color = PrimaryContainer,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Rounded.SwapVert, contentDescription = "Intercambiar", tint = PrimaryBlueDark, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Fecha y Horario de Salida", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                onClick = {
                                    state.updateDraft { it.copy(dateLabel = if (it.dateLabel == "Hoy") "Mañana" else "Hoy") }
                                },
                                color = SoftContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("FECHA", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    }
                                    Text(draft.dateLabel, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                }
                            }
                            Surface(color = SoftContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("HORA EXACTA", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    }
                                    Text("${draft.time} hrs", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip("En 15 min", draft.dateLabel == "Hoy" && draft.time == "18:45", {
                                state.updateDraft { it.copy(dateLabel = "Hoy", time = "18:45") }
                            })
                            SelectChip("19:00 hrs", draft.dateLabel == "Hoy" && draft.time == "19:00", {
                                state.updateDraft { it.copy(dateLabel = "Hoy", time = "19:00") }
                            })
                            SelectChip("Mañana 07:30", draft.dateLabel == "Mañana" && draft.time == "07:30", {
                                state.updateDraft { it.copy(dateLabel = "Mañana", time = "07:30") }
                            })
                        }
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Plazas Disponibles", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("Espacio cómodo en asientos traseros", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            CounterStepper(value = draft.seats, onChange = { s -> state.updateDraft { it.copy(seats = s) } }, range = 1..4)
                        }

                        Surface(
                            onClick = {
                                val next = (luggageOptions.indexOf(draft.luggage) + 1) % luggageOptions.size
                                state.updateDraft { it.copy(luggage = luggageOptions[next]) }
                            },
                            color = SoftContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Luggage, contentDescription = null, tint = PrimaryBlueDark)
                                Spacer(Modifier.width(10.dp))
                                Text("Espacio de equipaje", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                                Text(draft.luggage, style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("APORTE POR PASAJERO", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("$${draft.pricePerSeat} MXN / asiento", style = MaterialTheme.typography.titleLarge, color = PrimaryBlue)
                            }
                            StatusPill("Recomendado", container = PrimaryContainer, content = PrimaryBlueDark, icon = Icons.Rounded.AutoAwesome)
                        }

                        Slider(
                            value = draft.pricePerSeat.toFloat(),
                            onValueChange = { v -> state.updateDraft { it.copy(pricePerSeat = (v / 5f).roundToInt() * 5) } },
                            valueRange = 40f..110f,
                            steps = 13,
                            colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$40 (Mín)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text("$50 - $90 (Sugerido)", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                            Text("$110 (Máx)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }

                        Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.LocalGasStation, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                Text("Tarifa calculada para amortizar 2 casetas y 1.2 L de combustible promedio en tu ruta.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Preferencias a Bordo", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Tus reglas para una ruta agradable", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ruleOptions.forEach { rule ->
                                val selected = rule in draft.rules
                                SelectChip(
                                    text = rule,
                                    selected = selected,
                                    onClick = {
                                        state.updateDraft {
                                            val updated = if (selected) it.rules - rule else it.rules + rule
                                            it.copy(rules = updated)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Payments, size = 40, container = PrimaryContainer, tint = PrimaryBlueDark)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Aporte potencial (${draft.seats} pasajeros)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text("$${draft.earnings} MXN", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        }
                        Text("100% libre\ncomisiones", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                    }
                }
            }
        }
    }
}
