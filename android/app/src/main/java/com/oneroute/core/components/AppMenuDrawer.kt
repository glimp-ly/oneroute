package com.oneroute.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
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

/**
 * Contenido del menú lateral (hamburguesa) accesible desde el logo circular superior izquierdo.
 */
@Composable
fun AppMenuDrawerContent(
    state: AppState,
    nav: AppNavigator,
    onClose: () -> Unit
) {
    val user = state.currentUser
    val current = nav.current
    var logoutDialog by remember { mutableStateOf(false) }

    if (logoutDialog) {
        AlertDialog(
            onDismissRequest = { logoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Deseas cerrar tu sesión en este dispositivo?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        logoutDialog = false
                        onClose()
                        nav.resetTo(Route.Login)
                    }
                ) {
                    Text("Cerrar sesión", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { logoutDialog = false }) { Text("Cancelar") }
            }
        )
    }

    ModalDrawerSheet(drawerContainerColor = CardSurface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BrandLogo(size = 52)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "One Route",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlueDark
                        )
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(user.level, container = PrimaryContainer, content = PrimaryBlueDark)
                    RatingLabel(user.rating)
                }
            }

            Surface(
                color = SoftContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = PrimaryBlueDark
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Modo conductor",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Publica rutas y acepta pasajeros",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = state.driverModeEnabled,
                        onCheckedChange = state::toggleDriverMode
                    )
                }
            }

            DrawerSectionTitle("Principal")
            DrawerMenuItem(
                label = "Inicio",
                icon = Icons.Rounded.Home,
                selected = current == Route.Home,
                onClick = { onClose(); nav.selectTab(MainTab.INICIO) }
            )
            DrawerMenuItem(
                label = "Buscar viajes",
                icon = Icons.Rounded.Search,
                selected = current == Route.Search,
                onClick = { onClose(); nav.selectTab(MainTab.BUSCAR) }
            )
            DrawerMenuItem(
                label = "Mis viajes",
                icon = Icons.Rounded.DirectionsCar,
                selected = current is Route.MyTrips,
                badgeCount = state.scheduledTrips.size,
                onClick = { onClose(); nav.selectTab(MainTab.MIS_VIAJES) }
            )
            DrawerMenuItem(
                label = "Publicar viaje",
                icon = Icons.Rounded.AddCircleOutline,
                selected = current == Route.CreateTrip,
                onClick = { onClose(); nav.navigate(Route.CreateTrip) }
            )
            DrawerMenuItem(
                label = "Mensajes",
                icon = Icons.AutoMirrored.Rounded.Chat,
                selected = current == Route.MessagesInbox,
                badgeCount = state.unreadMessages,
                onClick = { onClose(); nav.navigate(Route.MessagesInbox) }
            )
            DrawerMenuItem(
                label = "Notificaciones",
                icon = Icons.Rounded.Notifications,
                selected = current == Route.Notifications,
                badgeCount = state.unreadNotifications,
                onClick = { onClose(); nav.navigate(Route.Notifications) }
            )
            DrawerMenuItem(
                label = "Historial de viajes",
                icon = Icons.Rounded.History,
                selected = current == Route.TripHistory,
                onClick = { onClose(); nav.navigate(Route.TripHistory) }
            )

            HorizontalDivider(color = OutlineSoft, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            DrawerSectionTitle("Configuración y cuenta")
            DrawerMenuItem(
                label = "Configuración y perfil",
                icon = Icons.Rounded.Settings,
                selected = current == Route.Profile,
                onClick = { onClose(); nav.selectTab(MainTab.PERFIL) }
            )
            DrawerMenuItem(
                label = "Billeteras y pagos",
                icon = Icons.Rounded.AccountBalanceWallet,
                selected = current == Route.WalletPayments,
                onClick = { onClose(); nav.navigate(Route.WalletPayments) }
            )
            DrawerMenuItem(
                label = "Documentos y DNI",
                icon = Icons.Rounded.VerifiedUser,
                selected = current == Route.DocumentVerification,
                onClick = { onClose(); nav.navigate(Route.DocumentVerification) }
            )
            DrawerMenuItem(
                label = "Mi vehículo",
                icon = Icons.Rounded.DirectionsCar,
                selected = current == Route.VehicleRegistration,
                onClick = { onClose(); nav.navigate(Route.VehicleRegistration) }
            )

            HorizontalDivider(color = OutlineSoft, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            DrawerSectionTitle("Seguridad y soporte")
            DrawerMenuItem(
                label = "Contactos de emergencia",
                icon = Icons.Rounded.Shield,
                selected = current == Route.EmergencyContacts,
                onClick = { onClose(); nav.navigate(Route.EmergencyContacts) }
            )
            DrawerMenuItem(
                label = "Reportes y descargos",
                icon = Icons.Rounded.Gavel,
                selected = current is Route.SafetyReports,
                onClick = { onClose(); nav.navigate(Route.SafetyReports()) }
            )
            DrawerMenuItem(
                label = "Centro de ayuda",
                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                selected = current == Route.HelpCenter,
                onClick = { onClose(); nav.navigate(Route.HelpCenter) }
            )

            HorizontalDivider(color = OutlineSoft, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            DrawerMenuItem(
                label = "Cerrar sesión",
                icon = Icons.AutoMirrored.Rounded.ExitToApp,
                selected = false,
                tint = DangerRed,
                onClick = { logoutDialog = true }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DrawerSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerMenuItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    tint: Color? = null
) {
    val defaultTint = tint ?: if (selected) PrimaryBlueDark else TextSecondary
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = tint ?: if (selected) PrimaryBlueDark else TextPrimary
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = defaultTint
            )
        },
        badge = if (badgeCount > 0) {
            {
                Badge(containerColor = PrimaryBlue, contentColor = Color.White) {
                    Text(badgeCount.toString())
                }
            }
        } else null,
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = PrimaryContainer,
            selectedIconColor = PrimaryBlueDark,
            selectedTextColor = PrimaryBlueDark,
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextPrimary
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
