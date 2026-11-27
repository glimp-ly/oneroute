package com.oneroute.feature.publish

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppTextField
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.MapOverlayPill
import com.oneroute.core.components.PrimaryPillButton
import com.oneroute.core.components.SelectChip
import com.oneroute.core.components.StatusPill
import com.oneroute.core.components.StylizedMapCanvas
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import kotlin.math.roundToInt

private data class SuggestedPickup(val name: String, val address: String, val detail: String, val lat: Double, val lon: Double)

private val suggestedPickups = listOf(
    SuggestedPickup("Parque México", "Av. México 123", "Hipódromo Condesa, Cuauhtémoc, 06100, CDMX", 19.4124, 99.1691),
    SuggestedPickup("Glorieta Popocatépetl", "Plaza Popocatépetl 42", "Hipódromo, Cuauhtémoc, 06100, CDMX", 19.4102, 99.1715),
    SuggestedPickup("Metro Chilpancingo", "Av. Insurgentes Sur 360", "Roma Sur, Cuauhtémoc, 06760, CDMX", 19.4061, 99.1684)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapPointPickerScreen(state: AppState, nav: AppNavigator) {
    val draft = state.tripDraft
    var selectedPreset by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val preset = suggestedPickups[selectedPreset]
    val lat = preset.lat + dragY * 0.0001
    val lon = preset.lon + dragX * 0.0001

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
                        dragX = (dragX + drag.x).coerceIn(-60f, 60f)
                        dragY = (dragY + drag.y).coerceIn(-60f, 60f)
                    }
                }
        ) {
            StylizedMapCanvas(Modifier.fillMaxSize(), progress = 0.5f)

            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(onClick = { nav.back() }, color = CardSurface, shape = CircleShape, shadowElevation = 3.dp) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        Text("Fijar punto de recogida", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    }
                }
                StatusPill("Paso 1 de 2", container = CardSurface, content = PrimaryBlueDark)
            }

            Column(
                Modifier.align(Alignment.Center).offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapOverlayPill("Mueve el mapa para fijar el punto exacto", container = TextPrimary, content = Color.White)
                IconBubble(Icons.Rounded.LocationOn, size = 48, container = PrimaryBlue, tint = Color.White)
            }

            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(color = CardSurface, shape = CircleShape, shadowElevation = 3.dp) {
                    IconButton(onClick = { dragX = 0f; dragY = 0f }) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = "Centrar", tint = PrimaryBlue)
                    }
                }
                Surface(color = CardSurface, shape = CircleShape, shadowElevation = 3.dp) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.Traffic, contentDescription = null, tint = PrimaryBlueDark)
                    }
                }
            }

            MapOverlayPill(
                text = "GPS: ${"%.4f".format(lat)}° N, ${"%.4f".format(lon)}° O",
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
            )
        }

        Surface(
            color = CardSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBubble(Icons.Rounded.LocationOn, size = 42, container = PrimaryContainer, tint = PrimaryBlue)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("RECOGIDA DE PASAJEROS", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                            StatusPill("Auto-detectado", container = SoftContainer, content = TextSecondary)
                        }
                        Text(draft.pickupPoint, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(preset.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    IconBubble(Icons.Rounded.Search, size = 38, container = SoftContainer, tint = TextSecondary)
                }

                AppTextField(
                    value = draft.pickupReference,
                    onValueChange = { ref -> state.updateDraft { it.copy(pickupReference = ref) } },
                    label = "Instrucciones o referencia para el pasajero",
                    trailing = {
                        if (draft.pickupReference.isNotEmpty()) {
                            IconButton(onClick = { state.updateDraft { it.copy(pickupReference = "") } }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Limpiar")
                            }
                        }
                    }
                )

                Text("Puntos sugeridos cercanos sin tráfico:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestedPickups.forEachIndexed { idx, item ->
                        SelectChip(
                            text = item.name,
                            selected = selectedPreset == idx,
                            onClick = {
                                selectedPreset = idx
                                dragX = 0f
                                dragY = 0f
                                state.updateDraft { it.copy(pickupPoint = item.address) }
                            }
                        )
                    }
                }

                Surface(color = SoftContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = PrimaryBlueDark, modifier = Modifier.size(18.dp))
                        Text(
                            "Zona permitida para ascenso rápido según normativa de movilidad CDMX. Sin fotomultas por detención momentánea.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                PrimaryPillButton(
                    text = "Fijar este punto de recogida",
                    icon = Icons.Rounded.CheckCircle,
                    onClick = { nav.navigate(Route.PublishPreview) }
                )
            }
        }
    }
}
