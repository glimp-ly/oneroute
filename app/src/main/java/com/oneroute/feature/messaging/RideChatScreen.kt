package com.oneroute.feature.messaging

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.AvatarBadge
import com.oneroute.core.components.CallButton
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.MapOverlayPill
import com.oneroute.core.components.PrimaryPillButton
import com.oneroute.core.components.StatusPill
import com.oneroute.core.components.StylizedMapCanvas
import com.oneroute.core.components.TonalPillButton
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.DangerRed
import com.oneroute.core.theme.OutlineSoft
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.data.model.ChatMessage
import com.oneroute.data.model.ChatMessageType

private val quickReplies = listOf("Ya estoy en el punto", "Llego en 2 min", "¿Color del auto?")

@Composable
fun RideChatScreen(conversationId: String, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val conversation = state.conversations.firstOrNull { it.id == conversationId }
    var draft by rememberSaveable { mutableStateOf("") }
    var tripExpanded by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(conversationId) {
        state.markConversationRead(conversationId)
    }

    LaunchedEffect(conversation?.messages?.size) {
        val count = conversation?.messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = conversation?.name ?: "Chat de viaje",
                subtitle = "Conversación del viaje activo",
                showBrandLogo = true,
                onBack = { nav.back() },
                actions = {
                    if (conversation != null) {
                        CallButton(onClick = { Toast.makeText(context, "Llamando a ${conversation.name}", Toast.LENGTH_SHORT).show() })
                    }
                }
            )
        },
        bottomBar = {
            if (conversation != null) {
                Surface(color = CardSurface, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(top = 8.dp, bottom = 10.dp)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(quickReplies) { reply ->
                                Surface(
                                    onClick = { state.sendRideMessage(conversationId, reply) },
                                    color = SoftContainer,
                                    shape = CircleShape
                                ) {
                                    Text(reply, style = MaterialTheme.typography.labelMedium, color = TextPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { Toast.makeText(context, "Adjuntar archivo", Toast.LENGTH_SHORT).show() }) {
                                Icon(Icons.Rounded.AttachFile, contentDescription = "Adjuntar", tint = TextSecondary)
                            }
                            IconButton(onClick = {
                                val tripId = state.scheduledTrips.firstOrNull()?.id ?: "st1"
                                nav.navigate(Route.MeetingPoint(tripId))
                            }) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = "Punto", tint = TextSecondary)
                            }
                            IconButton(onClick = { Toast.makeText(context, "Cámara no disponible", Toast.LENGTH_SHORT).show() }) {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = "Cámara", tint = TextSecondary)
                            }
                            TextField(
                                value = draft,
                                onValueChange = { draft = it },
                                placeholder = { Text("Escribe un mensaje...", color = TextMuted) },
                                singleLine = true,
                                shape = CircleShape,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SoftContainer,
                                    unfocusedContainerColor = SoftContainer,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                onClick = {
                                    if (draft.isNotBlank()) {
                                        state.sendRideMessage(conversationId, draft)
                                        draft = ""
                                    } else {
                                        Toast.makeText(context, "Mantén para grabar nota de voz", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = CircleShape,
                                color = PrimaryBlue,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (draft.isNotBlank()) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Mic,
                                        contentDescription = "Enviar",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = CanvasBackground
    ) { padding ->
        if (conversation == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Conversación no encontrada", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                TonalPillButton("Volver", onClick = { nav.back() })
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            AppCard(corner = 20, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { tripExpanded = !tripExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill("VIAJE HOY • 18:30 HRS • ASIENTO 2A", container = PrimaryContainer, content = PrimaryBlueDark)
                        Icon(if (tripExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = TextSecondary)
                    }
                    if (tripExpanded) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(PrimaryBlue))
                                    Text("Condesa (Parque México)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(DangerRed))
                                    Text("Santa Fe (Torre Arcos)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("18:30", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text("~19:15", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                        Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Punto de encuentro fijado", style = MaterialTheme.typography.labelMedium, color = PrimaryBlueDark)
                                    Text("Frente al Foro Lindbergh (bahía sin tráfico sobre Av. México)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val tripId = state.scheduledTrips.firstOrNull()?.id ?: "st1"
                            OutlinedButton(onClick = { nav.navigate(Route.MeetingPoint(tripId)) }, shape = CircleShape, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ver en Mapa", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(onClick = { nav.navigate(Route.ActiveTrip(tripId)) }, shape = CircleShape, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Detalles del viaje", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = PrimaryBlueDark, modifier = Modifier.size(16.dp))
                            Text(
                                "Chat protegido. Por tu seguridad, no compartas datos bancarios ni contraseñas fuera de la app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StatusPill("Hoy, 24 de Octubre", container = SoftContainer, content = TextSecondary)
                    }
                }
                items(conversation.messages, key = { it.id }) { msg ->
                    ChatBubbleItem(
                        msg = msg,
                        peerInitials = conversation.name.split(' ').take(2).joinToString("") { it.take(1) },
                        onOpenGps = { Toast.makeText(context, "Abriendo punto en navegador GPS...", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(msg: ChatMessage, peerInitials: String, onOpenGps: () -> Unit) {
    var playing by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!msg.fromMe) {
            AvatarBadge(peerInitials, size = 28, container = SoftContainer, content = PrimaryBlueDark)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (msg.fromMe) Alignment.End else Alignment.Start) {
            when (msg.type) {
                ChatMessageType.TEXT -> {
                    Surface(
                        color = if (msg.fromMe) PrimaryBlue else SoftContainer,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (msg.fromMe) 18.dp else 4.dp,
                            bottomEnd = if (msg.fromMe) 4.dp else 18.dp
                        ),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (msg.fromMe) Color.White else TextPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
                ChatMessageType.LOCATION_PIN -> {
                    AppCard(corner = 20, modifier = Modifier.widthIn(max = 280.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StylizedMapCanvas(Modifier.fillMaxWidth().height(110.dp), progress = 0.5f) {
                                MapOverlayPill("Bahía Carlos", container = TextPrimary, content = Color.White, modifier = Modifier.align(Alignment.Center))
                            }
                            Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(msg.text, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(msg.extra ?: "", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                PrimaryPillButton("Abrir en Navegador GPS", icon = Icons.Rounded.Navigation, onClick = onOpenGps)
                            }
                        }
                    }
                }
                ChatMessageType.VOICE_NOTE -> {
                    AppCard(container = SoftContainer, corner = 18, modifier = Modifier.widthIn(max = 280.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(onClick = { playing = !playing }, color = PrimaryBlue, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Reproducir", tint = Color.White)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        listOf(10, 18, 12, 22, 16, 20, 10, 18, 14, 10).forEachIndexed { idx, h ->
                                            Box(
                                                Modifier.width(3.dp).height(h.dp).clip(CircleShape)
                                                    .background(if (playing || idx < 5) PrimaryBlue else OutlineSoft)
                                            )
                                        }
                                    }
                                    Text("Nota de voz • ${msg.extra ?: "0:18"}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                            Text(msg.text, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }
                }
                ChatMessageType.TRIP_SUMMARY -> Unit
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(msg.time, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                if (msg.fromMe) Icon(Icons.Rounded.DoneAll, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
            }
        }
    }
}
