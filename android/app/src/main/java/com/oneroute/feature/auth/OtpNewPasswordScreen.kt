package com.oneroute.feature.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AppTextField
import com.oneroute.core.components.AppTopBar
import com.oneroute.core.components.IconBubble
import com.oneroute.core.components.PrimaryPillButton
import com.oneroute.core.components.ScreenList
import com.oneroute.core.components.StatusPill
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.Route
import com.oneroute.core.theme.CanvasBackground
import com.oneroute.core.theme.OutlineSoft
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun OtpNewPasswordScreen(contact: String, nav: AppNavigator) {
    val context = LocalContext.current
    var otp by rememberSaveable { mutableStateOf("8492") }
    var secondsLeft by rememberSaveable { mutableIntStateOf(30) }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var showPass by rememberSaveable { mutableStateOf(false) }
    var closeOtherSessions by rememberSaveable { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    val hasLength = newPassword.length >= 8
    val hasNumAndUpper = newPassword.any { it.isDigit() } && newPassword.any { it.isUpperCase() }
    val hasSpecial = newPassword.any { !it.isLetterOrDigit() }
    val matches = newPassword.isNotEmpty() && newPassword == confirmPassword
    val canSave = otp.length == 6 && hasLength && hasNumAndUpper && matches

    Scaffold(
        topBar = { AppTopBar("Verificar código", showBrandLogo = true, onBack = { nav.back() }) },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, modifier = Modifier.imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Text("Verifica tu identidad", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Hemos enviado un código de 6 dígitos a", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(contact, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { nav.back() }.padding(4.dp)
                ) {
                    Text("Cambiar correo", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                }
            }
            item {
                AppCard(container = SoftContainer, corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Código de seguridad", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            StatusPill("Cifrado TLS", container = PrimaryContainer, content = PrimaryBlueDark, icon = Icons.Rounded.Shield)
                        }

                        Box(Modifier.fillMaxWidth().clickable { focusRequester.requestFocus() }) {
                            BasicTextField(
                                value = otp,
                                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otp = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.size(1.dp).focusRequester(focusRequester)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (i in 0 until 6) {
                                    val ch = otp.getOrNull(i)?.toString() ?: "•"
                                    val active = otp.length == i
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (active) Color.White else PrimaryContainer.copy(alpha = 0.7f))
                                            .border(
                                                width = if (active) 2.dp else 1.dp,
                                                color = if (active) PrimaryBlue else OutlineSoft,
                                                shape = RoundedCornerShape(14.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            ch,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = if (otp.getOrNull(i) != null) PrimaryBlueDark else TextMuted
                                        )
                                    }
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                Text("Reenviar en 00:${"%02d".format(secondsLeft)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Text(
                                "Reenviar código",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (secondsLeft == 0) PrimaryBlue else TextMuted,
                                modifier = Modifier.clickable(enabled = secondsLeft == 0) {
                                    secondsLeft = 30
                                    Toast.makeText(context, "Código reenviado a $contact", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
            item {
                AppCard(corner = 24) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconBubble(Icons.Rounded.Key, size = 38, container = SoftContainer, tint = PrimaryBlue)
                            Column {
                                Text("Crea tu nueva contraseña", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("Protege tus viajes y métodos de pago", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }

                        AppTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = "Nueva contraseña",
                            leadingIcon = Icons.Rounded.Lock,
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardType = KeyboardType.Password,
                            trailing = {
                                IconButton(onClick = { showPass = !showPass }) {
                                    Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
                                }
                            }
                        )

                        PasswordStrengthBar(newPassword)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RequirementRow("Al menos 8 caracteres", hasLength)
                            RequirementRow("Incluye números y letras mayúsculas", hasNumAndUpper)
                            RequirementRow("Al menos un carácter especial (!@#$)", hasSpecial)
                        }

                        AppTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = "Confirmar nueva contraseña",
                            leadingIcon = Icons.Rounded.Lock,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardType = KeyboardType.Password,
                            trailing = {
                                if (matches) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = PrimaryBlue)
                            }
                        )
                    }
                }
            }
            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Devices, size = 38, container = PrimaryContainer, tint = PrimaryBlue)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Cerrar otras sesiones activas", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Desconecta teléfonos y navegadores no reconocidos", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = closeOtherSessions,
                            onCheckedChange = { closeOtherSessions = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                        )
                    }
                }
            }
            item {
                PrimaryPillButton(
                    text = "Guardar contraseña y continuar",
                    trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                    enabled = canSave,
                    onClick = {
                        Toast.makeText(context, "Contraseña actualizada correctamente", Toast.LENGTH_SHORT).show()
                        nav.resetTo(Route.Login)
                    }
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Text("¿No recibiste el SMS o correo? Soporte 24/7", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(text: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (met) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (met) PrimaryBlue else TextMuted,
            modifier = Modifier.size(16.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (met) TextPrimary else TextMuted)
    }
}
