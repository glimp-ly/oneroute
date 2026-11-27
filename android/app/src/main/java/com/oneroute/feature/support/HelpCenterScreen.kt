package com.oneroute.feature.support

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTextField
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.AvatarBadge
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.MainTab
import com.oneroute.core.components.ScreenList
import com.oneroute.core.components.SectionHeader
import com.oneroute.core.components.StatusPill
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.DangerContainer
import com.oneroute.core.theme.DangerRed
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.data.repository.MockRepository

@Composable
fun HelpCenterScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var expandedFaqId by rememberSaveable { mutableStateOf<String?>(null) }
    var helpfulState by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showSosDialog by remember { mutableStateOf(false) }

    val topics = MockRepository.faqTopics.filter {
        query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true)
    }
    val faqs = MockRepository.faqArticles.filter {
        query.isBlank() || it.question.contains(query, true) || it.category.contains(query, true)
    }

    if (showSosDialog) {
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            title = { Text("Línea Crítica SOS 911") },
            text = { Text("Esta línea atiende emergencias reales durante un trayecto activo con prioridad inmediata.") },
            confirmButton = {
                TextButton(onClick = {
                    showSosDialog = false
                    Toast.makeText(context, "Conectando con línea crítica SOS...", Toast.LENGTH_SHORT).show()
                }) { Text("Llamar 911", color = DangerRed) }
            },
            dismissButton = { TextButton(onClick = { showSosDialog = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Centro de Ayuda",
                showBrandLogo = true,
                onBack = { nav.back() },
                onHelp = { Toast.makeText(context, "Soporte disponible 24/7", Toast.LENGTH_SHORT).show() },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Route.SupportTicketChat("TK-9042")) },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                icon = { Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null) },
                text = { Text("Nuevo Ticket de Soporte", style = MaterialTheme.typography.labelLarge) }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, modifier = Modifier.imePadding()) {
            item {
                AppTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "¿En qué te ayudamos hoy?",
                    leadingIcon = Icons.Rounded.Search,
                    trailing = { Icon(Icons.Rounded.Mic, contentDescription = null, tint = TextSecondary) }
                )
            }

            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(PrimaryContainer), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(PrimaryBlue))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Todos los servicios operando normal", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Tiempo promedio de respuesta: ~3 minutos", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = PrimaryBlue)
                    }
                }
            }

            item {
                AppCard(onClick = { nav.navigate(Route.SupportTicketChat("TK-9042")) }, container = SoftContainer, corner = 22) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill("Ticket #TK-9042", container = PrimaryBlue, content = Color.White)
                            Text("Comprobante electrónico", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                            Text("• En revisión", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AvatarBadge("MG", size = 34, verified = true)
                            Text("Mariana G. está atendiendo tu...", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Ver chat", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("Atención Inmediata")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ImmediateCard(
                        icon = Icons.AutoMirrored.Rounded.Chat,
                        badge = "24/7",
                        title = "Chat en Vivo",
                        subtitle = "Asesor en línea",
                        container = SoftContainer,
                        iconBg = PrimaryBlue,
                        iconTint = Color.White,
                        onClick = { nav.navigate(Route.SupportTicketChat("TK-9042")) },
                        modifier = Modifier.weight(1f)
                    )
                    ImmediateCard(
                        icon = Icons.Rounded.Warning,
                        badge = "SOS",
                        title = "Línea Crítica",
                        subtitle = "Prioridad 911",
                        container = DangerContainer,
                        iconBg = DangerRed,
                        iconTint = Color.White,
                        titleColor = DangerRed,
                        onClick = { showSosDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    ImmediateCard(
                        icon = Icons.Rounded.Warning,
                        badge = null,
                        title = "Incidente",
                        subtitle = "En tu viaje",
                        container = SoftContainer,
                        iconBg = CardSurface,
                        iconTint = TextSecondary,
                        onClick = { nav.navigate(Route.SafetyReports(initialTab = 0)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 22) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("COMUNIDAD PROTEGIDA", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                            Text("Seguro de Viaje AXA Incluido", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Cobertura médica y contra pérdidas activa en cada trayecto reservado.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        IconBubble(Icons.Rounded.Shield, size = 56, container = PrimaryContainer, tint = PrimaryBlueDark)
                    }
                }
            }

            item {
                SectionHeader("Explorar por Temas", action = "35 guías oficiales")
            }

            items(topics, key = { it.title }) { topic ->
                val icon = when {
                    topic.title.contains("Pagos") -> Icons.Rounded.AccountBalanceWallet
                    topic.title.contains("Reservas") -> Icons.Rounded.DirectionsCar
                    topic.title.contains("Seguridad") -> Icons.Rounded.Shield
                    topic.title.contains("Cuenta") -> Icons.Rounded.Badge
                    else -> Icons.Rounded.Pets
                }
                AppCard(onClick = { nav.navigate(Route.FaqArticleDetail("faq1")) }, container = SoftContainer, corner = 18) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(icon, size = 40, container = PrimaryContainer, tint = PrimaryBlueDark)
                        Column(Modifier.weight(1f)) {
                            Text(topic.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("${topic.subtitle} • ${topic.articleCount} artículos", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Text("Preguntas Frecuentes", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    Text("Top semana", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }

            items(faqs, key = { it.id }) { faq ->
                val expanded = expandedFaqId == faq.id
                AppCard(onClick = { expandedFaqId = if (expanded) null else faq.id }, container = SoftContainer, corner = 18) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (faq.popular) StatusPill("Popular", container = PrimaryContainer, content = PrimaryBlueDark)
                                    Text(faq.category, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                                Text(faq.question, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            }
                            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = TextSecondary)
                        }
                        if (expanded) {
                            Text(
                                "Si cancelas con más de 60 minutos de anticipación recibes 100% de reembolso automático. Toca abajo para ver toda la guía.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                "Leer artículo completo →",
                                style = MaterialTheme.typography.labelLarge,
                                color = PrimaryBlue,
                                modifier = Modifier.clickable { nav.navigate(Route.FaqArticleDetail(faq.id)) }
                            )
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 22) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconBubble(Icons.Rounded.ThumbUp, size = 44, container = PrimaryContainer, tint = PrimaryBlueDark)
                        Text("¿Encontraste lo que buscabas?", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Tus valoraciones nos ayudan a mejorar las guías para todos", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
                        if (helpfulState != null) {
                            StatusPill(
                                if (helpfulState == true) "¡Gracias por tu opinión!" else "Abriendo soporte para ayudarte...",
                                container = PrimaryContainer,
                                content = PrimaryBlueDark
                            )
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(onClick = { helpfulState = true }, color = CardSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.SentimentSatisfied, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                        Text("  Sí, gracias", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                    }
                                }
                                Surface(
                                    onClick = {
                                        helpfulState = false
                                        nav.navigate(Route.SupportTicketChat("TK-9042"))
                                    },
                                    color = CardSurface,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.SentimentDissatisfied, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                        Text("  Necesito ayuda", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

@Composable
private fun ImmediateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String?,
    title: String,
    subtitle: String,
    container: Color,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = TextPrimary
) {
    AppCard(onClick = onClick, container = container, corner = 20, modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon, size = 34, container = iconBg, tint = iconTint)
                if (badge != null) {
                    StatusPill(
                        badge,
                        container = if (badge == "SOS") DangerRed else PrimaryContainer,
                        content = if (badge == "SOS") Color.White else PrimaryBlueDark
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (titleColor == DangerRed) DangerRed else TextSecondary)
        }
    }
}
