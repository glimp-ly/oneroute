package com.onerouteandroid.oneroute.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTextField
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary

@Composable
fun RegisterScreen(state: AppState, nav: AppNavigator) {
    var name by rememberSaveable { mutableStateOf("Sofía Morales Valdez") }
    var email by rememberSaveable { mutableStateOf("sofia.morales@ejemplo.com") }
    var phone by rememberSaveable { mutableStateOf("55 4892 1047") }
    var password by rememberSaveable { mutableStateOf("Ruta2024!") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var acceptTerms by rememberSaveable { mutableStateOf(true) }
    var acceptPromos by rememberSaveable { mutableStateOf(true) }

    val emailOk = isValidEmail(email)
    val passwordOk = password.length >= 8 && password.any { it.isDigit() } && password.any { it.isLetter() }
    val canCreate = name.trim().length >= 3 && emailOk && phone.count { it.isDigit() } >= 8 && passwordOk && acceptTerms

    Scaffold(
        topBar = { AppTopBar("Registro de Usuario", showBrandLogo = true, onBack = { nav.back() }) },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, modifier = Modifier.imePadding()) {
            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = PrimaryBlue)
                                Text("COMUNIDAD SEGURA", style = MaterialTheme.typography.labelSmall, color = PrimaryBlueDark)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Viaja acompañado hoy", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("+12,000 conductores verificados", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        StatusPill("+99", container = PrimaryContainer, content = PrimaryBlueDark)
                    }
                }
            }
            item {
                Text("Únete a RutaCompartida", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Ingresa tus datos personales para comenzar a viajar y compartir gastos.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            item {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Nombre completo",
                    leadingIcon = Icons.Rounded.Person,
                    supportingText = "Tal como aparece en tu identificación oficial"
                )
            }
            item {
                AppTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Correo electrónico",
                    leadingIcon = Icons.Rounded.Email,
                    keyboardType = KeyboardType.Email,
                    supportingText = "Te enviaremos la confirmación de viaje aquí",
                    trailing = {
                        if (emailOk) Icon(Icons.Rounded.CheckCircle, contentDescription = "Válido", tint = PrimaryBlue)
                    }
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Surface(color = SoftContainer, shape = RoundedCornerShape(16.dp)) {
                        Text("+52", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp))
                    }
                    AppTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "Teléfono móvil",
                        leadingIcon = Icons.Rounded.Phone,
                        keyboardType = KeyboardType.Phone,
                        supportingText = "Para coordinar puntos de encuentro",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Contraseña",
                        leadingIcon = Icons.Rounded.Lock,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardType = KeyboardType.Password,
                        trailing = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
                            }
                        }
                    )
                    PasswordStrengthBar(password)
                    Text("Mínimo 8 caracteres, incluye letras y números", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { acceptTerms = !acceptTerms }) {
                        Checkbox(checked = acceptTerms, onCheckedChange = { acceptTerms = it }, colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue))
                        Text("Acepto los Términos y Condiciones y la Política de Privacidad.", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { acceptPromos = !acceptPromos }) {
                        Checkbox(checked = acceptPromos, onCheckedChange = { acceptPromos = it }, colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue))
                        Text("Deseo recibir promociones, descuentos y alertas de rutas frecuentes.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
            item {
                PrimaryPillButton(
                    text = "CREAR CUENTA",
                    icon = Icons.Rounded.PersonAdd,
                    enabled = canCreate,
                    onClick = {
                        state.registerUser(name.trim(), email.trim(), phone.trim())
                        nav.resetTo(Route.Home)
                    }
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("¿Ya tienes una cuenta? ", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.width(2.dp))
                    Text("Inicia sesión", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue, modifier = Modifier.clickable { nav.navigate(Route.Login) })
                }
            }
            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.Shield, container = PrimaryContainer, tint = PrimaryBlueDark)
                        Column {
                            Text("Validación de identidad", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Tus datos están protegidos con encriptación de 256 bits y verificación oficial.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
