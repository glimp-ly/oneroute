package com.oneroute.feature.trips

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTextField
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.AvatarBadge
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.PrimaryPillButton
import com.oneroute.core.components.ScreenList
import com.oneroute.core.components.SelectChip
import com.oneroute.core.components.StatusPill
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.OutlineSoft
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.StarYellow
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.core.theme.WarningAmber
import com.oneroute.core.theme.WarningContainer

private val ratingTags = listOf(
    "Conducción segura",
    "Muy puntual",
    "Auto limpio y cómodo",
    "Conversación agradable",
    "Buena música",
    "Excelente ruta / Sin tráfico",
    "Mascota bienvenida"
)

private val tipOptions = listOf("No", "+S/ 5", "+S/ 10", "+S/ 20", "Otro")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RateUserScreen(route: Route.RateUser, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val firstName = route.targetName.substringBefore(' ')
    var stars by rememberSaveable { mutableIntStateOf(5) }
    val selectedTags = remember { mutableStateListOf("Conducción segura", "Muy puntual", "Auto limpio y cómodo") }
    var tipIndex by rememberSaveable { mutableIntStateOf(2) }
    var customTip by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("$firstName fue muy puntual, el auto estaba impecable y el viaje fue muy tranquilo por Constituyentes.") }
    var anonymous by rememberSaveable { mutableStateOf(false) }

    val tipAmount = when (tipIndex) {
        1 -> 5
        2 -> 10
        3 -> 20
        4 -> customTip.toIntOrNull() ?: 0
        else -> 0
    }

    val ratingMood = when (stars) {
        5 -> "¡Excelente viaje! (5.0)"
        4 -> "Muy buen viaje (4.0)"
        3 -> "Viaje normal (3.0)"
        2 -> "Viaje regular (2.0)"
        else -> "Mala experiencia (1.0)"
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Calificar Viaje",
                onBack = { nav.back() },
                onClose = { nav.back() },
                avatarInitials = state.currentUser.initials
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, modifier = Modifier.imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val initials = route.targetName.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
                    AvatarBadge(initials, size = 80, verified = true)
                    StatusPill(route.vehicleSummary, container = SoftContainer, content = TextSecondary, icon = Icons.Rounded.DirectionsCar)
                    Text("¿Cómo calificarías tu viaje con $firstName?", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, textAlign = TextAlign.Center)
                    Text("Tu valoración fomenta una comunidad segura y de confianza", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (i in 1..5) {
                                Icon(
                                    imageVector = if (i <= stars) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                    contentDescription = "$i estrellas",
                                    tint = if (i <= stars) StarYellow else OutlineSoft,
                                    modifier = Modifier.size(38.dp).clickable { stars = i }
                                )
                            }
                        }
                        StatusPill(ratingMood, container = WarningContainer, content = WarningAmber, icon = Icons.Rounded.AutoAwesome)
                    }
                }
            }

            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("¿Qué fue lo mejor?", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Multiselección", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ratingTags.forEach { tag ->
                            val selected = tag in selectedTags
                            SelectChip(
                                text = tag,
                                selected = selected,
                                onClick = { if (selected) selectedTags.remove(tag) else selectedTags.add(tag) }
                            )
                        }
                    }
                }
            }

            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconBubble(Icons.Rounded.VolunteerActivism, size = 38, container = SoftContainer, tint = PrimaryBlue)
                            Column {
                                Text("Reconoce su buen servicio con una propina opcional", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text("El 100% de la propina se transfiere íntegro a $firstName", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            tipOptions.forEachIndexed { idx, label ->
                                val selected = tipIndex == idx
                                Surface(
                                    onClick = { tipIndex = idx },
                                    color = if (selected) PrimaryBlue else SoftContainer,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else TextPrimary)
                                    }
                                }
                            }
                        }
                        if (tipIndex == 4) {
                            AppTextField(
                                value = customTip,
                                onValueChange = { if (it.all(Char::isDigit)) customTip = it },
                                label = "Monto personalizado (PEN)",
                                keyboardType = KeyboardType.Number
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            Text("Se cargará automáticamente a tu tarjeta ••8912", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }

            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppTextField(
                        value = comment,
                        onValueChange = { if (it.length <= 300) comment = it },
                        label = "Escribe un comentario o agradecimiento (opcional)",
                        singleLine = false,
                        minLines = 3
                    )
                    Text("${comment.length} / 300", style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 18) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                        Text("Publicar reseña de manera anónima", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                        Switch(
                            checked = anonymous,
                            onCheckedChange = { anonymous = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                        )
                    }
                }
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val buttonText = if (tipAmount > 0) "Enviar calificación y propina (S/ $tipAmount PEN)" else "Enviar calificación"
                    PrimaryPillButton(
                        text = buttonText,
                        icon = Icons.AutoMirrored.Rounded.Send,
                        onClick = {
                            state.submitRating(route.tripId, stars, selectedTags.toSet(), tipAmount, comment, anonymous)
                            Toast.makeText(context, "¡Muchas gracias por tu calificación!", Toast.LENGTH_LONG).show()
                            nav.resetTo(Route.Home)
                        }
                    )
                    TextButton(onClick = { nav.back() }) {
                        Text("Omitir por ahora", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    }
                }
            }
        }
    }
}
