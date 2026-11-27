package com.oneroute.feature.support

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.MainTab
import com.oneroute.core.components.PrimaryPillButton
import com.oneroute.core.components.ScreenList
import com.oneroute.core.components.StatusPill
import com.oneroute.core.components.TonalPillButton
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryBlueLight
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.data.repository.MockRepository

@Composable
fun FaqArticleDetailScreen(articleId: String, state: AppState, nav: AppNavigator) {
    val article = MockRepository.faqArticles.firstOrNull { it.id == articleId }
    var helpful by rememberSaveable(articleId) { mutableStateOf<Boolean?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Artículo FAQ",
                showBrandLogo = true,
                onBack = { nav.back() },
                onHelp = { nav.navigate(Route.HelpCenter) },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        bottomBar = {
            Surface(color = CardSurface, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("¿Aún tienes dudas?", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Soporte disponible 24/7", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    TonalPillButton("Centro", icon = Icons.Rounded.SupportAgent, modifier = Modifier.weight(0.9f), onClick = { nav.back() })
                    PrimaryPillButton("Iniciar Chat", icon = Icons.AutoMirrored.Rounded.Chat, modifier = Modifier.weight(1.1f), onClick = { nav.navigate(Route.SupportTicketChat("TK-9042")) })
                }
            }
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("Pagos y Reembolsos", container = SoftContainer, content = PrimaryBlueDark, icon = Icons.Rounded.AccountBalanceWallet)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Text("Actualizado hace 2 días • Lectura de 3 min", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Text(
                        article?.question ?: "¿Cómo funciona la política de cancelación y reembolso?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                }
            }

            item {
                AppCard(container = PrimaryContainer, corner = 22) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = PrimaryBlueDark)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Resumen clave", style = MaterialTheme.typography.titleSmall, color = PrimaryBlueDark)
                            Text(
                                "Si cancelas con más de 1 hora de anticipación a la salida, recibes el 100% de tu dinero de vuelta automáticamente en tu Billetera o método de pago original.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PrimaryBlueDark
                            )
                        }
                    }
                }
            }

            item {
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp))) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.verticalGradient(listOf(Color(0xFFDCEBFF), Color(0xFFF3F8FF))))
                        drawRoundRect(PrimaryBlueLight, Offset(size.width * 0.25f, size.height * 0.48f), Size(size.width * 0.5f, size.height * 0.28f), CornerRadius(24f))
                        drawCircle(Color(0xFF1E293B), radius = 18f, center = Offset(size.width * 0.35f, size.height * 0.78f))
                        drawCircle(Color(0xFF1E293B), radius = 18f, center = Offset(size.width * 0.65f, size.height * 0.78f))
                    }
                    StatusPill(
                        "Garantía Comunitaria de Carpooling",
                        container = TextPrimary.copy(alpha = 0.75f),
                        content = Color.White,
                        icon = Icons.Rounded.VerifiedUser,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    )
                }
            }

            item {
                FaqStep(
                    number = 1,
                    title = "Cancelación con más de 60 minutos de anticipación",
                    body = "Si cancelas al menos 60 minutos antes de la hora programada de salida, se procesa un reembolso total del 100% sin penalización ni comisión por gestión. El asiento se libera de inmediato para otro compañero."
                )
            }
            item {
                FaqStep(
                    number = 2,
                    title = "Cancelación con menos de 60 minutos",
                    body = "Para proteger el compromiso y tiempo de nuestros conductores verificados, se genera un reembolso del 50%. El monto restante cubre combustible y disponibilidad de ruta."
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FaqStep(
                        number = 3,
                        title = "Si el conductor cancela el viaje",
                        body = "Tu tranquilidad es prioridad. En este escenario imprevisto la plataforma aplica automáticamente:"
                    )
                    AppCard(container = SoftContainer, corner = 18) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BenefitBullet(Icons.Rounded.CheckCircle, "100% de reembolso íntegro sin demoras.")
                            BenefitBullet(Icons.Rounded.AccountBalanceWallet, "+$30 MXN de bono de cortesía acreditados en tu billetera.")
                            BenefitBullet(Icons.Rounded.Bolt, "Prioridad inteligente en reservas de rutas similares.")
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FaqStep(number = 4, title = "Tiempo de acreditación bancaria", body = "")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppCard(container = SoftContainer, corner = 18, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = PrimaryBlueDark, modifier = Modifier.size(16.dp))
                                    Text("Billetera In-App", style = MaterialTheme.typography.labelMedium, color = PrimaryBlueDark)
                                }
                                Text("Inmediato", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("Disponible al instante para nuevos viajes.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                        AppCard(container = SoftContainer, corner = 18, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    Text("Tarjeta Bancaria", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                }
                                Text("1 a 2 días hábiles", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("BBVA, Banorte, Santander, Hey Banco.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            item {
                Text("Tabla comparativa de escenarios", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScenarioRow(Icons.Rounded.Schedule, "> 60 min previos", "Cancelación voluntaria", "100%")
                    ScenarioRow(Icons.Rounded.Schedule, "< 60 min previos", "Cancelación tardía", "50%")
                    ScenarioRow(Icons.Rounded.DirectionsCar, "Conductor cancela", "Incidencia de servicio", "100% + $30", highlight = true)
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 22) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("¿Te resultó útil este artículo?", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Tus opiniones nos ayudan a mejorar el centro de asistencia.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
                        if (helpful != null) {
                            StatusPill(
                                if (helpful == true) "¡Gracias por valorar esta guía!" else "Tomaremos en cuenta tu comentario",
                                container = PrimaryContainer,
                                content = PrimaryBlueDark
                            )
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(onClick = { helpful = true }, color = CardSurface, shape = CircleShape, modifier = Modifier.weight(1f)) {
                                    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.ThumbUp, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                        Text("  Sí, fue claro", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                                    }
                                }
                                Surface(onClick = { helpful = false }, color = CardSurface, shape = CircleShape, modifier = Modifier.weight(1f)) {
                                    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.ThumbDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                        Text("  No, necesito más", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                    }
                                }
                            }
                        }
                        Text("94% de 1,280 usuarios encontraron útil esta respuesta", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }

            item {
                Text("Artículos relacionados", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RelatedRow(Icons.Rounded.Description, "¿Cómo solicitar la factura CFDI de ...") { nav.navigate(Route.SupportTicketChat("TK-9042")) }
                    RelatedRow(Icons.Rounded.LocationOn, "¿Qué pasa si el pasajero no llega al...") { nav.navigate(Route.FaqArticleDetail("faq2")) }
                }
            }
        }
    }
}

@Composable
private fun FaqStep(number: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(PrimaryContainer), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = PrimaryBlueDark)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun BenefitBullet(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}

@Composable
private fun ScenarioRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    highlight: Boolean = false
) {
    AppCard(container = SoftContainer, corner = 18) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBubble(icon, size = 36, container = CardSurface, tint = PrimaryBlueDark)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            StatusPill(badge, container = if (highlight) PrimaryBlue else PrimaryContainer, content = if (highlight) Color.White else PrimaryBlueDark)
        }
    }
}

@Composable
private fun RelatedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    AppCard(onClick = onClick, container = SoftContainer, corner = 18) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBubble(icon, size = 34, container = PrimaryContainer, tint = PrimaryBlueDark)
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
        }
    }
}
