package com.onerouteandroid.oneroute.feature.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppBottomBar
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.CounterStepper
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SelectChip
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.DangerRed
import com.onerouteandroid.oneroute.core.theme.OutlineSoft
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.SuccessGreen
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary

@Composable
fun SearchTripsScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    var origin by rememberSaveable { mutableStateOf("Metro Insurgentes") }
    var destination by rememberSaveable { mutableStateOf("Centro Comercial Santa Fe") }
    val dates = listOf("Hoy", "Mañana", "Viernes 24")
    var selectedDate by rememberSaveable { mutableIntStateOf(0) }
    var passengers by rememberSaveable { mutableIntStateOf(1) }
    val sortOptions = listOf("Menor precio", "Salida próxima", "Calificación 4.8+")
    var selectedSort by rememberSaveable { mutableIntStateOf(0) }

    val filteredTrips = state.availableTrips
        .filter { it.seatsLeft >= passengers }
        .let { list ->
            when (selectedSort) {
                0 -> list.sortedBy { it.price }
                1 -> list.sortedBy { it.departure }
                else -> list.filter { it.driver.rating >= 4.8 }.sortedByDescending { it.driver.rating }
            }
        }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Buscar",
                showBrandLogo = true,
                onNotifications = { nav.navigate(Route.Notifications) },
                unreadNotifications = state.unreadNotifications,
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        bottomBar = {
            AppBottomBar(MainTab.BUSCAR, onSelect = nav::selectTab, tripBadge = state.scheduledTrips.size)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { Toast.makeText(context, "Vista de mapa general en desarrollo", Toast.LENGTH_SHORT).show() },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                icon = { Icon(Icons.Rounded.Map, contentDescription = null) },
                text = { Text("Ver en mapa", style = MaterialTheme.typography.labelLarge) }
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding) {
            item {
                AppCard(container = SoftContainer, corner = 24) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 10.dp)) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(SuccessGreen))
                                    Box(Modifier.width(2.dp).height(46.dp).background(OutlineSoft))
                                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InlineField("Punto de partida", origin, { origin = it }, Icons.Rounded.MyLocation)
                                    InlineField("Destino", destination, { destination = it })
                                }
                            }
                            Surface(
                                onClick = {
                                    val tmp = origin
                                    origin = destination
                                    destination = tmp
                                },
                                shape = CircleShape,
                                color = PrimaryContainer,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp).size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.SwapVert, contentDescription = "Intercambiar", tint = PrimaryBlueDark, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            dates.forEachIndexed { idx, label ->
                                SelectChip(text = label, selected = selectedDate == idx, onClick = { selectedDate = idx })
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CounterStepper(
                                value = passengers,
                                onChange = { passengers = it },
                                range = 1..3,
                                unit = if (passengers == 1) "pasajero" else "pasajeros",
                                modifier = Modifier.weight(1f)
                            )
                            Surface(color = CardSurface, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f)) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Horario", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        Text("18:00 - 19:30", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    }
                                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sortOptions.indices.toList()) { idx ->
                        SelectChip(
                            text = sortOptions[idx],
                            selected = selectedSort == idx,
                            onClick = { selectedSort = idx }
                        )
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${filteredTrips.size} viajes disponibles", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(PrimaryBlue))
                        Text("Actualizado en vivo", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            if (filteredTrips.isEmpty()) {
                item {
                    AppCard(container = SoftContainer) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconBubble(Icons.Rounded.SearchOff, size = 48, container = PrimaryContainer, tint = PrimaryBlueDark)
                            Text("Sin viajes con $passengers lugares", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Reduce el número de pasajeros o ajusta los filtros.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            } else {
                items(filteredTrips, key = { it.id }) { trip ->
                    AvailableTripCard(trip = trip, onClick = { nav.navigate(Route.TripDetail(trip.id)) })
                }
            }

            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

@Composable
private fun InlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 36.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleSmall.copy(color = TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
