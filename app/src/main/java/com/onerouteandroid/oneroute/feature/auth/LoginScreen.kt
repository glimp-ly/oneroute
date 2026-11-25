package com.onerouteandroid.oneroute.feature.auth

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppTextField
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.OutlinedPillButton
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.OutlineSoft
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary

@Composable
fun LoginScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    var account by rememberSaveable { mutableStateOf(state.currentUser.email) }
    var password by rememberSaveable { mutableStateOf("Ruta2024!") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var rememberMe by rememberSaveable { mutableStateOf(true) }

    val canSubmit = isValidAccountInput(account) && password.length >= 6

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Iniciar Sesión",
                showBrandLogo = true,
                onBack = { nav.back() }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, modifier = Modifier.imePadding()) {
            item {
                StatusPill("COMUNIDAD DE VIAJEROS", container = PrimaryContainer, content = PrimaryBlueDark, icon = Icons.Rounded.DirectionsCar)
            }
            item {
                Text("¡Qué bueno verte de nuevo!", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ingresa tus credenciales para continuar tus viajes compartidos y ahorrar en cada trayecto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            item {
                AppTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = "Correo electrónico o teléfono",
                    leadingIcon = Icons.Rounded.Email,
                    keyboardType = KeyboardType.Email
                )
            }
            item {
                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Contraseña",
                    leadingIcon = Icons.Rounded.Lock,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    trailing = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Mostrar contraseña")
                        }
                    }
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { rememberMe = !rememberMe }) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                        )
                        Text("Recordarme", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                    Text(
                        "¿Olvidaste tu contraseña?",
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryBlue,
                        modifier = Modifier.clickable { nav.navigate(Route.ForgotPassword) }.padding(4.dp)
                    )
                }
            }
            item {
                PrimaryPillButton(
                    text = "Ingresar",
                    trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                    enabled = canSubmit,
                    onClick = { nav.resetTo(Route.Home) }
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Surface(
                        onClick = {
                            Toast.makeText(context, "Acceso biométrico verificado", Toast.LENGTH_SHORT).show()
                            nav.resetTo(Route.Home)
                        },
                        shape = CircleShape,
                        color = SoftContainer
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            Text("Ingreso biométrico", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = OutlineSoft)
                    Text("  O ingresa con  ", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    HorizontalDivider(Modifier.weight(1f), color = OutlineSoft)
                }
            }
            item {
                OutlinedPillButton(
                    text = "Continuar con Google",
                    onClick = {
                        Toast.makeText(context, "Sesión iniciada con Google", Toast.LENGTH_SHORT).show()
                        nav.resetTo(Route.Home)
                    }
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("¿No tienes una cuenta? ", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "Regístrate aquí",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier.clickable { nav.navigate(Route.Register) }
                    )
                }
            }
        }
    }
}
