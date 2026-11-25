package com.onerouteandroid.oneroute.feature.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SectionHeader
import com.onerouteandroid.oneroute.core.components.SelectChip
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.components.TonalPillButton
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.OutlineSoft
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.data.model.VehicleInfo

private val exteriorColors = listOf(
    Color(0xFF0E234E),
    Color(0xFF6B7280),
    Color(0xFFF8FAFC),
    Color(0xFF0F172A),
    Color(0xFF991B1B)
)

private val vehicleAmenities = listOf(
    "Aire Acondicionado (A/C)",
    "Cargador USB-C",
    "Audio Bluetooth",
    "Cajuela Amplia",
    "Asientos de Piel",
    "Sanitizado Diario",
    "Acepta Mascotas"
)

private val luggageLimits = listOf(
    "Pequeño (1 mochila por viajero)",
    "Mediano (1 carry-on por viajero)",
    "Grande (1 maleta por viajero)"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleRegistrationScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val initial = state.vehicle
    var model by rememberSaveable { mutableStateOf(initial.model) }
    var year by rememberSaveable { mutableIntStateOf(initial.year) }
    var plate by rememberSaveable { mutableStateOf(initial.plate) }
    var colorIndex by rememberSaveable { mutableIntStateOf(initial.colorIndex) }
    var showVin by rememberSaveable { mutableStateOf(false) }
    var seats by rememberSaveable { mutableIntStateOf(initial.seats) }
    var luggageIdx by rememberSaveable { mutableIntStateOf(luggageLimits.indexOf(initial.luggage).coerceAtLeast(1)) }
    val selectedAmenities = remember { mutableStateListOf<String>().apply { addAll(initial.amenities) } }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Registro de Vehículo",
                showBrandLogo = true,
                onBack = { nav.back() },
                onClose = { nav.back() },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, modifier = Modifier.imePadding()) {
            item {
                AppCard(corner = 24) {
                    VehicleBannerCanvas(
                        badge = "Aprobado para circulación en CDMX",
                        bottomText = "${model.substringAfter(' ')} • $year"
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = PrimaryBlue)
                        Text("Ficha Técnica", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    StatusPill("Oficial CDMX", container = SoftContainer, content = PrimaryBlueDark)
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(value = model, onValueChange = { model = it }, label = "Marca y Modelo", leadingIcon = Icons.Rounded.DirectionsCar)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            onClick = { year = if (year >= 2025) 2020 else year + 1 },
                            color = SoftContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Año", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(year.toString(), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            }
                        }
                        AppTextField(
                            value = plate,
                            onValueChange = { plate = it.uppercase() },
                            label = "Placas",
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    AppCard(container = SoftContainer, corner = 18) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Color Exterior", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text(VehicleInfo.colorNames[colorIndex], style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                exteriorColors.forEachIndexed { idx, color ->
                                    val selected = idx == colorIndex
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(if (selected) 3.dp else 1.dp, if (selected) PrimaryBlue else OutlineSoft, CircleShape)
                                            .clickable { colorIndex = idx },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(Icons.Rounded.Check, contentDescription = null, tint = if (idx == 2) TextPrimary else Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AppCard(container = SoftContainer, corner = 18) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Número de Serie / VIN", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                val masked = if (showVin) initial.vin else "${initial.vin.take(7)} •••• ${initial.vin.takeLast(4)}"
                                Text(masked, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            }
                            IconButton(onClick = { showVin = !showVin }) {
                                Icon(if (showVin) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Mostrar VIN", tint = TextSecondary)
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("Plazas y Habitáculo", badge = "$seats Pasajeros máx.")
                Spacer(Modifier.height(10.dp))
                AppCard(container = SoftContainer, corner = 18) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Plazas ofertadas", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Confort garantizado (sin apretujones)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        CounterStepper(value = seats, onChange = { seats = it }, range = 1..4)
                    }
                }
                Spacer(Modifier.height(10.dp))
                AppCard(container = SoftContainer, corner = 22) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Parabrisas Delantero", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text("Vista Cenital", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CabinSeatTile(state.currentUser.firstName, "Conductor", active = true, icon = Icons.Rounded.AirlineSeatReclineNormal, modifier = Modifier.weight(1f))
                            CabinSeatTile("Copiloto", if (seats >= 1) "Disponible" else "Cerrado", active = seats >= 1, icon = Icons.Rounded.AirlineSeatReclineNormal, modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CabinSeatTile("Ventana Izq.", if (seats >= 2) "Plaza 2" else "Cerrado", active = seats >= 2, icon = Icons.Rounded.AirlineSeatReclineNormal, modifier = Modifier.weight(1f))
                            CabinSeatTile("Central", if (seats >= 4) "Plaza 4" else "Libre", active = seats >= 4, icon = if (seats >= 4) Icons.Rounded.AirlineSeatReclineNormal else Icons.Rounded.Block, modifier = Modifier.weight(0.9f))
                            CabinSeatTile("Ventana Der.", if (seats >= 3) "Plaza 3" else "Cerrado", active = seats >= 3, icon = Icons.Rounded.AirlineSeatReclineNormal, modifier = Modifier.weight(1f))
                        }
                        Text(
                            "Mantener el asiento central despejado incrementa tu calificación promedio un +18%.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                AppCard(onClick = { luggageIdx = (luggageIdx + 1) % luggageLimits.size }, container = SoftContainer, corner = 18) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Luggage, contentDescription = null, tint = PrimaryBlue)
                        Column(Modifier.weight(1f)) {
                            Text("Límite de Equipaje Sugerido", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(luggageLimits[luggageIdx], style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        }
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = TextSecondary)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = PrimaryBlue)
                        Text("Comodidades del Viaje", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    Text("${selectedAmenities.size} seleccionadas", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vehicleAmenities.forEach { amenity ->
                        val selected = amenity in selectedAmenities
                        SelectChip(
                            text = amenity,
                            selected = selected,
                            onClick = { if (selected) selectedAmenities.remove(amenity) else selectedAmenities.add(amenity) }
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryPillButton(
                        text = "Guardar Cambios del Vehículo",
                        icon = Icons.Rounded.Save,
                        enabled = model.isNotBlank() && plate.isNotBlank(),
                        onClick = {
                            state.updateVehicle(
                                initial.copy(
                                    model = model.trim(),
                                    year = year,
                                    plate = plate.trim(),
                                    colorIndex = colorIndex,
                                    seats = seats,
                                    luggage = luggageLimits[luggageIdx],
                                    amenities = selectedAmenities.toSet()
                                )
                            )
                            Toast.makeText(context, "Vehículo actualizado", Toast.LENGTH_SHORT).show()
                            nav.back()
                        }
                    )
                    TonalPillButton(
                        text = "Agregar otro vehículo",
                        icon = Icons.Rounded.AddCircleOutline,
                        onClick = { Toast.makeText(context, "Solo puedes registrar 1 vehículo activo por ahora", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CabinSeatTile(
    title: String,
    subtitle: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (active) PrimaryContainer else CardSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = if (active) PrimaryBlueDark else TextMuted, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = if (active) PrimaryBlue else TextMuted)
        }
    }
}
