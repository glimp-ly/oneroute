package com.onerouteandroid.oneroute.feature.support

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.AvatarBadge
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.DangerRed
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.data.model.ChatMessage
import com.onerouteandroid.oneroute.data.model.ChatMessageType

private val supportQuickReplies = listOf("Sí, por favor", "¿Puedo cambiar mi CLABE?", "Necesito el comprobante")

@Composable
fun SupportTicketChatScreen(ticketId: String, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.supportMessages.size) {
        if (state.supportMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.supportMessages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Chat Soporte Ticket",
                showBrandLogo = true,
                onBack = { nav.back() },
                onHelp = { nav.navigate(Route.HelpCenter) },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        bottomBar = {
            Surface(color = CardSurface, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(top = 8.dp, bottom = 10.dp)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(supportQuickReplies) { reply ->
                            Surface(
                                onClick = { state.sendSupportMessage(reply) },
                                color = SoftContainer,
                                shape = CircleShape
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        if (reply.startsWith("Sí")) Icons.Rounded.ThumbUp else Icons.Rounded.CreditCard,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(reply, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { Toast.makeText(context, "Adjuntar comprobante", Toast.LENGTH_SHORT).show() }) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = "Adjuntar", tint = TextSecondary)
                        }
                        IconButton(onClick = { Toast.makeText(context, "Tomar foto", Toast.LENGTH_SHORT).show() }) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = "Foto", tint = TextSecondary)
                        }
                        TextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text("Escribe un mensaje...", color = TextMuted) },
                            trailingIcon = { Icon(Icons.Rounded.SentimentSatisfied, contentDescription = null, tint = TextSecondary) },
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
                                    state.sendSupportMessage(draft)
                                    draft = ""
                                }
                            },
                            shape = CircleShape,
                            color = PrimaryBlue,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Enviar", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        containerColor = CanvasBackground
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            AppCard(corner = 20, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AvatarBadge("MG", size = 44, verified = true)
                        Column(Modifier.weight(1f)) {
                            Text("Mariana González", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Asesora Especialista M3", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        StatusPill("En curso", container = PrimaryBlue, content = Color.White)
                    }
                    Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Ticket #$ticketId", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                                Text("Consulta sobre Acreditación SPEI", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            }
                            StatusPill("~4 min", container = CardSurface, content = PrimaryBlueDark, icon = Icons.Rounded.Schedule)
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
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sesión iniciada hoy • Cifrado de extremo a extremo", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
                items(state.supportMessages, key = { it.id }) { msg ->
                    SupportBubble(msg)
                }
            }
        }
    }
}

@Composable
private fun SupportBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!msg.fromMe) {
            AvatarBadge("MG", size = 28, container = SoftContainer, content = PrimaryBlueDark)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (msg.fromMe) Alignment.End else Alignment.Start) {
            if (msg.type == ChatMessageType.TRIP_SUMMARY && msg.extra != null) {
                val parts = msg.extra.split('|')
                val id = parts.getOrElse(0) { "VIAJE ID #VF-8821" }
                val date = parts.getOrElse(1) { "23 Octubre • 08:15 hrs" }
                val from = parts.getOrElse(2) { "Condesa" }
                val to = parts.getOrElse(3) { "Santa Fe" }
                val amount = parts.getOrElse(4) { "$195.00" }
                AppCard(corner = 20, modifier = Modifier.widthIn(max = 290.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconBubble(Icons.Rounded.DirectionsCar, size = 36, container = SoftContainer, tint = PrimaryBlueDark)
                            Column(Modifier.weight(1f)) {
                                Text(id, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(date, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(amount, style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
                                Text("MXN", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                        }
                        Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(PrimaryBlue))
                                    Text(from, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(DangerRed))
                                    Text(to, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                }
                            }
                        }
                        Surface(color = PrimaryContainer, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = PrimaryBlueDark, modifier = Modifier.size(16.dp))
                                    Text(msg.text, style = MaterialTheme.typography.labelSmall, color = PrimaryBlueDark)
                                }
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = PrimaryBlueDark, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else {
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
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(msg.time, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                if (msg.fromMe) Icon(Icons.Rounded.DoneAll, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
            }
        }
    }
}
