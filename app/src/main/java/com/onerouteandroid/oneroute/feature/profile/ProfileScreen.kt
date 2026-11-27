package com.onerouteandroid.oneroute.feature.profile

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppBottomBar
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTextField
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.AvatarBadge
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.RatingLabel
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SectionHeader
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.DangerRed
import com.onerouteandroid.oneroute.core.theme.EcoContainer
import com.onerouteandroid.oneroute.core.theme.EcoTeal
import com.onerouteandroid.oneroute.core.theme.OutlineSoft
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.SuccessGreen
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.core.theme.WarningAmber
import com.onerouteandroid.oneroute.core.theme.WarningContainer

@Composable
fun ProfileScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val user = state.currentUser
    val vehicle = state.vehicle
    var editDialog by remember { mutableStateOf(false) }
    var editedName by remember(user.name) { mutableStateOf(user.name) }
    var logoutDialog by remember { mutableStateOf(false) }

    if (editDialog) {
        AlertDialog(
            onDismissRequest = { editDialog = false },
            title = { Text("Editar perfil") },
            text = { AppTextField(value = editedName, onValueChange = { editedName = it }, label = "Nombre completo") },
            confirmButton = {
                TextButton(onClick = {
                    if (editedName.trim().length >= 3) state.updateUserName(editedName.trim())
                    editDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editDialog = false }) { Text("Cancelar") } }
        )
    }

    if (logoutDialog) {
        AlertDialog(
            onDismissRequest = { logoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Deseas cerrar tu sesión en este dispositivo?") },
            confirmButton = {
                TextButton(onClick = {
                    logoutDialog = false
                    nav.resetTo(Route.Login)
                }) { Text("Cerrar sesión", color = DangerRed) }
            },
            dismissButton = { TextButton(onClick = { logoutDialog = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Perfil y Vehículo",
                showBrandLogo = true,
                onHelp = { nav.navigate(Route.HelpCenter) },
                avatarInitials = user.initials,
                actions = {
                    IconButton(onClick = { Toast.makeText(context, "Ajustes generales", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Ajustes", tint = TextSecondary)
                    }
                }
            )
        },
        bottomBar = {
            AppBottomBar(MainTab.PERFIL, onSelect = nav::selectTab, tripBadge = state.scheduledTrips.size)
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarBadge(user.initials, size = 86, verified = true)
                    Text(user.name, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    StatusPill(user.level, container = PrimaryContainer, content = PrimaryBlueDark)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = SoftContainer, shape = CircleShape) {
                            RatingLabel(user.rating, suffix = "(${user.reviewCount})", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            Text("Desde ${user.memberSince}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                    Surface(onClick = { editDialog = true }, color = PrimaryContainer, shape = CircleShape) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = PrimaryBlueDark, modifier = Modifier.size(16.dp))
                            Text("Editar perfil", style = MaterialTheme.typography.labelLarge, color = PrimaryBlueDark)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Icons.Rounded.DirectionsCar, user.sharedTrips.toString(), "Viajes\ncompartidos", Modifier.weight(1f))
                    StatTile(Icons.Rounded.Schedule, "${user.punctuality}%", "Índice\npuntual", Modifier.weight(1f))
                    StatTile(Icons.Rounded.Eco, user.co2SavedKg.toString(), "kg CO₂\nsalvados", Modifier.weight(1f), eco = true)
                }
            }

            item {
                SectionHeader("Insignias de Confianza", action = "4 de 4 activas")
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        TrustBadgeCard(Icons.Rounded.VerifiedUser, "Identidad INE", "Validada oficialmente", PrimaryBlue, PrimaryContainer)
                    }
                    item {
                        TrustBadgeCard(Icons.Rounded.MilitaryTech, "Top Conductor", "+200 viajes 5★", WarningAmber, WarningContainer)
                    }
                }
            }

            item {
                SectionHeader("Mi Vehículo Registrado")
                Spacer(Modifier.height(8.dp))
                AppCard(onClick = { nav.navigate(Route.VehicleRegistration) }, corner = 24) {
                    Column {
                        VehicleBannerCanvas(badge = "Verificado y Aprobado")
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${vehicle.model} ${vehicle.year}", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(vehicle.colorName.substringBefore(" Metalizado"), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    StatusPill(vehicle.plate, container = SoftContainer, content = TextPrimary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.AirlineSeatReclineNormal, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    Text("${vehicle.seats} plazas autorizadas", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                                }
                            }
                            IconBubble(Icons.AutoMirrored.Rounded.KeyboardArrowRight, size = 40, container = SoftContainer, tint = TextPrimary)
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.AlternateEmail, size = 42, container = PrimaryBlue, tint = Color.White)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Modo Conductor Activo", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Recibir solicitudes de viaje en ruta en tiempo real", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = state.driverModeEnabled,
                            onCheckedChange = state::toggleDriverMode,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                        )
                    }
                }
            }

            item {
                SectionHeader("Configuración y Seguridad")
                Spacer(Modifier.height(8.dp))
                AppCard(container = SoftContainer, corner = 22) {
                    Column {
                        SettingsRow(
                            icon = Icons.Rounded.AccountBalanceWallet,
                            title = "Billeteras y Pagos (3 cuentas)",
                            subtitle = "Ganancias S/ ${"%.0f".format(state.earningsWalletBalance)} • Pasajero S/ ${"%.0f".format(state.passengerWalletBalance)} • Comisiones S/ ${"%.0f".format(state.commissionWalletBalance)}",
                            onClick = { nav.navigate(Route.WalletPayments) }
                        )
                        HorizontalDivider(color = OutlineSoft)
                        SettingsRow(
                            icon = Icons.Rounded.Description,
                            title = "Documentos y Certificaciones",
                            subtitle = "${state.documents.size} de ${state.documents.size} vigentes",
                            subtitleColor = SuccessGreen,
                            onClick = { nav.navigate(Route.DocumentVerification) }
                        )
                        HorizontalDivider(color = OutlineSoft)
                        SettingsRow(
                            icon = Icons.Rounded.Tune,
                            title = "Preferencias de Viaje",
                            subtitle = "Música suave • No fumar • Mascotas sí",
                            onClick = { Toast.makeText(context, "Preferencias actualizadas", Toast.LENGTH_SHORT).show() }
                        )
                        HorizontalDivider(color = OutlineSoft)
                        SettingsRow(
                            icon = Icons.Rounded.Shield,
                            title = "Seguridad y Emergencia",
                            subtitle = "${state.emergencyContacts.size} contacto(s) de confianza asignados",
                            onClick = { nav.navigate(Route.EmergencyContacts) }
                        )
                        HorizontalDivider(color = OutlineSoft)
                        SettingsRow(
                            icon = Icons.Rounded.Gavel,
                            title = "Incidentes, Reportes y Descargos",
                            subtitle = "${state.receivedReportCases.size} caso(s) para revisión • Plazo 7 días",
                            onClick = { nav.navigate(Route.SafetyReports(initialTab = 2)) }
                        )
                    }
                }
            }

            item {
                Surface(onClick = { logoutDialog = true }, color = Color.Transparent, shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Text("Cerrar sesión en este dispositivo", style = MaterialTheme.typography.labelLarge, color = DangerRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier, eco: Boolean = false) {
    AppCard(modifier = modifier, container = SoftContainer, corner = 20) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconBubble(icon, size = 34, container = if (eco) EcoContainer else PrimaryContainer, tint = if (eco) EcoTeal else PrimaryBlue)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun TrustBadgeCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: Color, bg: Color) {
    AppCard(container = SoftContainer, corner = 18, modifier = Modifier.width(210.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBubble(icon, size = 38, container = bg, tint = tint)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
internal fun VehicleBannerCanvas(badge: String, bottomText: String? = null) {
    Box(Modifier.fillMaxWidth().height(128.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF0F2149), Color(0xFF1E3A8A), Color(0xFF334155))))
            drawRect(Color(0xFF1E293B), Offset(0f, size.height * 0.72f), Size(size.width, size.height * 0.28f))
            drawRoundRect(PrimaryBlueDark, Offset(size.width * 0.18f, size.height * 0.42f), Size(size.width * 0.64f, size.height * 0.3f), CornerRadius(30f))
            drawRoundRect(PrimaryBlue, Offset(size.width * 0.28f, size.height * 0.26f), Size(size.width * 0.42f, size.height * 0.2f), CornerRadius(24f))
            drawCircle(Color(0xFF0F172A), radius = 24f, center = Offset(size.width * 0.31f, size.height * 0.72f))
            drawCircle(Color(0xFF0F172A), radius = 24f, center = Offset(size.width * 0.69f, size.height * 0.72f))
            drawCircle(Color(0xFFCBD5E1), radius = 10f, center = Offset(size.width * 0.31f, size.height * 0.72f))
            drawCircle(Color(0xFFCBD5E1), radius = 10f, center = Offset(size.width * 0.69f, size.height * 0.72f))
        }
        StatusPill(
            badge,
            container = CardSurface,
            content = SuccessGreen,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
        )
        if (bottomText != null) {
            Text(
                bottomText,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color = TextSecondary,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().let { it }.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(icon, size = 38, container = PrimaryContainer, tint = PrimaryBlueDark)
        Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
    }
}
