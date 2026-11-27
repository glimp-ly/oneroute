package com.oneroute.feature.profile

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.MainTab
import com.oneroute.core.components.PrimaryPillButton
import com.oneroute.core.components.ScreenList
import com.oneroute.core.components.SectionHeader
import com.oneroute.core.components.StatusPill
import com.oneroute.core.components.TonalPillButton
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.DangerContainer
import com.oneroute.core.theme.DangerRed
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.SuccessContainer
import com.oneroute.core.theme.SuccessGreen
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.data.model.DocumentStatus
import com.oneroute.data.model.VerificationDocument

@Composable
fun DocumentVerificationScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Documentos",
                showBrandLogo = true,
                onBack = { nav.back() },
                onClose = { nav.back() },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(PrimaryBlue, PrimaryBlueDark)))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Conductor 100% Verificado", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Text(
                            "Tu identidad y auto cumplen con la normativa de seguridad activa de la comunidad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("Próxima renovación preventiva:", style = MaterialTheme.typography.bodySmall, color = Color.White)
                            }
                            StatusPill("Julio 2025", container = Color.White.copy(alpha = 0.2f), content = Color.White)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppCard(container = SoftContainer, corner = 18, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconBubble(Icons.Rounded.CheckCircle, size = 36, container = PrimaryContainer, tint = PrimaryBlueDark)
                            Column {
                                Text("Documentos", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("${state.documents.size} / ${state.documents.size} Activos", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            }
                        }
                    }
                    AppCard(container = SoftContainer, corner = 18, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconBubble(Icons.Rounded.MilitaryTech, size = 36, container = PrimaryContainer, tint = PrimaryBlueDark)
                            Column {
                                Text("Score Conductor", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("+45 pts Karma", style = MaterialTheme.typography.titleSmall, color = PrimaryBlue)
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Expediente Obligatorio", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Revisado periódicamente por el Centro de Confianza", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = PrimaryBlue)
                }
            }

            items(state.documents, key = { it.id }) { doc ->
                DocumentCard(doc) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Insignias de Confianza", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Aumentan tu visibilidad en búsquedas", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = TextSecondary)
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppCard(container = SoftContainer, corner = 18) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBubble(Icons.Rounded.VerifiedUser, size = 40, container = PrimaryContainer, tint = PrimaryBlueDark)
                            Column(Modifier.weight(1f)) {
                                Text("No Antecedentes Penales", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text("Emitida por OADPRS • Vigente", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            StatusPill("+15 pts", container = PrimaryContainer, content = PrimaryBlueDark)
                        }
                    }
                    AppCard(container = SoftContainer, corner = 18) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBubble(Icons.Rounded.School, size = 40, container = PrimaryContainer, tint = PrimaryBlueDark)
                            Column(Modifier.weight(1f)) {
                                Text("Curso Manejo M3 Defensivo", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text("Aprobado con mención honorífica", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Icon(Icons.Rounded.MilitaryTech, contentDescription = null, tint = PrimaryBlue)
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.Lock, size = 38, container = PrimaryContainer, tint = PrimaryBlueDark)
                        Column {
                            Text("Privacidad y resguardo confidencial", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                "Tus archivos están encriptados y protegidos conforme a la Ley Federal de Protección de Datos Personales. Ningún pasajero tiene acceso a tus identificaciones.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPillButton(
                        text = "Subir nuevo documento o endoso",
                        icon = Icons.Rounded.AddCircleOutline,
                        onClick = { Toast.makeText(context, "Selector de documentos PDF/Imagen", Toast.LENGTH_SHORT).show() }
                    )
                    Text("Tiempo promedio de revisión: menos de 2 horas en horario hábil", style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun DocumentCard(doc: VerificationDocument, onAction: (String) -> Unit) {
    val (pillBg, pillFg) = when (doc.status) {
        DocumentStatus.APPROVED, DocumentStatus.VALID -> SuccessContainer to SuccessGreen
        DocumentStatus.EXPIRING -> DangerContainer to DangerRed
        DocumentStatus.VERIFIED -> PrimaryContainer to PrimaryBlueDark
    }
    val docIcon = when (doc.id) {
        "doc1" -> Icons.Rounded.Badge
        "doc2" -> Icons.Rounded.DirectionsCar
        "doc3" -> Icons.Rounded.Shield
        else -> Icons.Rounded.VerifiedUser
    }

    AppCard(corner = 22) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBubble(docIcon, size = 40, container = if (doc.status == DocumentStatus.EXPIRING) DangerContainer else SoftContainer, tint = if (doc.status == DocumentStatus.EXPIRING) DangerRed else TextPrimary)
                Column(Modifier.weight(1f)) {
                    Text(doc.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(doc.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                StatusPill(
                    doc.status.label,
                    container = pillBg,
                    content = pillFg,
                    icon = if (doc.status == DocumentStatus.EXPIRING) Icons.Rounded.NotificationsActive else Icons.Rounded.CheckCircle
                )
            }

            Surface(color = SoftContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(doc.primaryLabel, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(doc.primaryValue, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    }
                    if (doc.secondaryLabel != null && doc.secondaryValue != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(doc.secondaryLabel, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                doc.secondaryValue,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (doc.status == DocumentStatus.EXPIRING) DangerRed else TextPrimary
                            )
                        }
                    }
                }
            }

            if (doc.id == "doc1") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPillButton("Ver copia", icon = Icons.Rounded.Visibility, modifier = Modifier.weight(1f), onClick = { onAction("Mostrando ${doc.title}") })
                    TonalPillButton("Actualizar", icon = Icons.Rounded.Refresh, modifier = Modifier.weight(1f), onClick = { onAction("Subir nueva copia") })
                }
            } else if (doc.status == DocumentStatus.EXPIRING) {
                PrimaryPillButton(
                    text = "Renovar o Subir Endoso",
                    icon = Icons.Rounded.Refresh,
                    onClick = { onAction("Renovación de póliza iniciada") }
                )
            }
        }
    }
}
