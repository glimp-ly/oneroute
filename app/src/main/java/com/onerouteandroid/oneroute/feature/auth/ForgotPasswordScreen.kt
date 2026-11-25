package com.onerouteandroid.oneroute.feature.auth

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTextField
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SelectableRow
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary

@Composable
fun ForgotPasswordScreen(nav: AppNavigator) {
    var channel by rememberSaveable { mutableIntStateOf(0) }
    var email by rememberSaveable { mutableStateOf("sofia.morales@ejemplo.com") }
    var phone by rememberSaveable { mutableStateOf("+52 55 4892 1047") }

    val contact = if (channel == 0) email.trim() else phone.trim()
    val valid = if (channel == 0) isValidEmail(email) else phone.count { it.isDigit() } >= 10

    Scaffold(
        topBar = { AppTopBar("Recuperar Contraseña", showBrandLogo = true, onBack = { nav.back() }) },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, modifier = Modifier.imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                StatusPill("Seguridad y Cuenta", container = SoftContainer, content = TextSecondary)
            }
            item {
                Box(Modifier.padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    IconBubble(Icons.Rounded.Lock, size = 76, container = PrimaryBlue, tint = androidx.compose.ui.graphics.Color.White)
                }
            }
            item {
                Text("¿Olvidaste tu contraseña?", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "No te preocupes, ingresa tu correo electrónico o teléfono registrado y te enviaremos un código de verificación para restablecerla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChannelOption(
                        selected = channel == 0,
                        icon = Icons.Rounded.Email,
                        title = "Enviar por Correo",
                        subtitle = email,
                        onClick = { channel = 0 }
                    )
                    ChannelOption(
                        selected = channel == 1,
                        icon = Icons.AutoMirrored.Rounded.Chat,
                        title = "Enviar por SMS / WhatsApp",
                        subtitle = phone,
                        onClick = { channel = 1 }
                    )
                }
            }
            item {
                AppTextField(
                    value = if (channel == 0) email else phone,
                    onValueChange = { if (channel == 0) email = it else phone = it },
                    label = if (channel == 0) "Correo electrónico registrado" else "Teléfono registrado",
                    leadingIcon = if (channel == 0) Icons.Rounded.AlternateEmail else Icons.Rounded.Phone,
                    keyboardType = if (channel == 0) KeyboardType.Email else KeyboardType.Phone,
                    supportingText = "Asegúrate de tener acceso a esta bandeja.",
                    trailing = {
                        if (valid) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = PrimaryBlue)
                    }
                )
            }
            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        IconBubble(Icons.Rounded.Schedule, size = 36, container = PrimaryContainer, tint = PrimaryBlue)
                        Column {
                            Text("Vigencia temporal", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                "Por tu seguridad y la de la comunidad de viajes, el código de 6 dígitos expirará en 10 minutos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
            item {
                PrimaryPillButton(
                    text = "Enviar código OTP",
                    trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                    enabled = valid,
                    onClick = { nav.navigate(Route.OtpNewPassword(contact)) }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text("¿Recordaste tu contraseña? ", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("Iniciar sesión", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue, modifier = Modifier.clickable { nav.back() })
                }
            }
        }
    }
}

@Composable
private fun ChannelOption(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    SelectableRow(selected = selected, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(
                icon = icon,
                size = 42,
                container = if (selected) PrimaryBlue else PrimaryContainer,
                tint = if (selected) androidx.compose.ui.graphics.Color.White else TextMuted
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue, unselectedColor = TextMuted)
            )
        }
    }
}
