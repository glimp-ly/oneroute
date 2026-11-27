package com.onerouteandroid.oneroute.feature.support

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTextField
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.ScreenList
import com.onerouteandroid.oneroute.core.components.SectionHeader
import com.onerouteandroid.oneroute.core.components.SelectChip
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.components.TonalPillButton
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.DangerContainer
import com.onerouteandroid.oneroute.core.theme.DangerRed
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.SuccessContainer
import com.onerouteandroid.oneroute.core.theme.SuccessGreen
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.core.theme.WarningAmber
import com.onerouteandroid.oneroute.core.theme.WarningContainer
import com.onerouteandroid.oneroute.data.repository.MockRepository

@Composable
fun EmergencyContactsScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+51 ") }
    var isPrimary by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Agregar Contacto de Emergencia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(value = name, onValueChange = { name = it }, label = "Nombre completo", leadingIcon = Icons.Rounded.Person)
                    AppTextField(value = relationship, onValueChange = { relationship = it }, label = "Parentesco (ej. Madre, Hermano)")
                    AppTextField(value = phone, onValueChange = { phone = it }, label = "Teléfono", leadingIcon = Icons.Rounded.Phone, keyboardType = KeyboardType.Phone)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Marcar como contacto principal", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isPrimary, onCheckedChange = { isPrimary = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (state.addEmergencyContact(name, relationship, phone, isPrimary)) {
                        name = ""
                        relationship = ""
                        phone = "+51 "
                        isPrimary = false
                        showAddDialog = false
                        Toast.makeText(context, "Contacto agregado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Completa nombre y teléfono", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Contactos de Emergencia",
                showBrandLogo = true,
                onBack = { nav.back() },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Agregar contacto") }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding) {
            item {
                AppCard(container = SoftContainer, corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.Shield, size = 40, container = PrimaryContainer, tint = PrimaryBlueDark)
                        Text(
                            "Al activar el botón SOS durante un viaje activo, la plataforma notificará automáticamente a tus contactos configurados junto con tu ubicación GPS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            items(state.emergencyContacts, key = { it.id }) { contact ->
                AppCard(corner = 20) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(Icons.Rounded.Phone, size = 42, container = if (contact.isPrimary) PrimaryBlue else SoftContainer, tint = if (contact.isPrimary) Color.White else PrimaryBlueDark)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(contact.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                if (contact.isPrimary) StatusPill("Principal", container = PrimaryContainer, content = PrimaryBlueDark)
                            }
                            Text("${contact.relationship} • ${contact.phone}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        if (!contact.isPrimary) {
                            IconButton(onClick = { state.setPrimaryEmergencyContact(contact.id) }) {
                                Icon(Icons.Rounded.Star, contentDescription = "Hacer principal", tint = TextMuted)
                            }
                        }
                        IconButton(onClick = { state.removeEmergencyContact(contact.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Eliminar", tint = DangerRed)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SafetyReportsScreen(tripId: String?, initialTab: Int, state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    var tabIndex by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    // Tab 0: Incidentes
    var incidentCat by remember { mutableStateOf(MockRepository.incidentCategories.first()) }
    var incidentDesc by remember { mutableStateOf("") }

    // Tab 1: Reporte a usuario (inmutable)
    var reportedName by remember { mutableStateOf("Carlos Mendoza") }
    val selectedReportCats = remember { mutableStateListOf("Incumplimiento") }
    var reportDesc by remember { mutableStateOf("") }

    // Tab 2: Descargos
    var defenseCaseId by remember { mutableStateOf<String?>(null) }
    var defenseText by remember { mutableStateOf("") }

    if (defenseCaseId != null) {
        AlertDialog(
            onDismissRequest = { defenseCaseId = null },
            title = { Text("Presentar Descargo (Plazo 7 días)") },
            text = {
                AppTextField(
                    value = defenseText,
                    onValueChange = { defenseText = it },
                    label = "Tu versión de los hechos",
                    singleLine = false,
                    minLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = defenseCaseId!!
                    if (state.submitReportDefense(id, defenseText)) {
                        defenseText = ""
                        defenseCaseId = null
                        Toast.makeText(context, "Descargo registrado", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Enviar descargo") }
            },
            dismissButton = { TextButton(onClick = { defenseCaseId = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Seguridad, Incidentes y Reportes",
                showBrandLogo = true,
                onBack = { nav.back() },
                avatarInitials = state.currentUser.initials
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = tabIndex, containerColor = CardSurface, contentColor = PrimaryBlue) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Incidentes") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Reportar") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("Descargos (${state.receivedReportCases.size})") })
            }

            ScreenList(
                padding = androidx.compose.foundation.layout.PaddingValues(bottom = padding.calculateBottomPadding()),
                modifier = Modifier.imePadding()
            ) {
                when (tabIndex) {
                    0 -> {
                        item {
                            AppCard(corner = 22) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = WarningAmber)
                                        Text("Reportar Incidente en Viaje", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    }
                                    Text("Registra fallas mecánicas, desvíos, conflictos o acoso (distinto al botón SOS 911).", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MockRepository.incidentCategories.forEach { cat ->
                                            SelectChip(text = cat, selected = incidentCat == cat, onClick = { incidentCat = cat })
                                        }
                                    }
                                    AppTextField(value = incidentDesc, onValueChange = { incidentDesc = it }, label = "Descripción del incidente", singleLine = false, minLines = 3)
                                    StatusPill("Se adjuntará tu ubicación GPS y hora actual", container = SoftContainer, content = PrimaryBlueDark, icon = Icons.Rounded.LocationOn)
                                    PrimaryPillButton(
                                        text = "Registrar incidente",
                                        onClick = {
                                            if (state.reportIncident(tripId ?: "st1", incidentCat, incidentDesc)) {
                                                incidentDesc = ""
                                                Toast.makeText(context, "Incidente registrado correctamente", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Describe el incidente", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        if (state.reportedIncidents.isNotEmpty()) {
                            item { SectionHeader("Incidentes enviados", badge = state.reportedIncidents.size.toString()) }
                            items(state.reportedIncidents, key = { it.id }) { inc ->
                                AppCard(container = SoftContainer, corner = 18) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(inc.category, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                            StatusPill(inc.status, container = WarningContainer, content = WarningAmber)
                                        }
                                        Text(inc.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        Text("${inc.locationLabel} • ${inc.createdAt}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        item {
                            AppCard(corner = 22) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.Report, contentDescription = null, tint = DangerRed)
                                        Text("Reportar Usuario (Inmutable)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    }
                                    StatusPill("El reporte original no podrá editarse ni eliminarse", container = DangerContainer, content = DangerRed)
                                    AppTextField(value = reportedName, onValueChange = { reportedName = it }, label = "Persona reportada")
                                    Text("Motivos (puedes elegir varios):", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MockRepository.reportCategories.forEach { cat ->
                                            val sel = cat in selectedReportCats
                                            SelectChip(text = cat, selected = sel, onClick = { if (sel) selectedReportCats.remove(cat) else selectedReportCats.add(cat) })
                                        }
                                    }
                                    AppTextField(value = reportDesc, onValueChange = { reportDesc = it }, label = "Detalle de lo ocurrido", singleLine = false, minLines = 3)
                                    PrimaryPillButton(
                                        text = "Enviar reporte formal",
                                        onClick = {
                                            if (state.submitUserReport(tripId ?: "st1", reportedName, selectedReportCats.toList(), reportDesc)) {
                                                reportDesc = ""
                                                Toast.makeText(context, "Reporte formal registrado", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Completa usuario, motivos y detalle", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        if (state.userReports.isNotEmpty()) {
                            item { SectionHeader("Reportes enviados", badge = state.userReports.size.toString()) }
                            items(state.userReports, key = { it.id }) { rep ->
                                AppCard(container = SoftContainer, corner = 18) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Contra: ${rep.reportedUserName}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                            StatusPill(rep.status, container = PrimaryContainer, content = PrimaryBlueDark)
                                        }
                                        Text("Motivos: ${rep.categories.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                                        Text(rep.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        item {
                            AppCard(container = SoftContainer, corner = 18) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IconBubble(Icons.Rounded.Gavel, size = 38, container = PrimaryContainer, tint = PrimaryBlueDark)
                                    Text(
                                        "Tienes 7 días calendario desde la notificación formal para presentar descargos. No responder no implica culpabilidad.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                        items(state.receivedReportCases, key = { it.id }) { caseItem ->
                            AppCard(corner = 20) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(caseItem.tripLabel, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        StatusPill(
                                            if (caseItem.defenseSubmitted) "Descargo enviado" else "${caseItem.remainingCalendarDays} días restantes",
                                            container = if (caseItem.defenseSubmitted) SuccessContainer else WarningContainer,
                                            content = if (caseItem.defenseSubmitted) SuccessGreen else WarningAmber
                                        )
                                    }
                                    Text("Motivo: ${caseItem.category}", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                                    Text(caseItem.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    if (caseItem.defenseSubmitted && caseItem.defenseText != null) {
                                        Text("Tu descargo: \"${caseItem.defenseText}\"", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                    } else {
                                        TonalPillButton(
                                            text = "Presentar descargo",
                                            onClick = { defenseCaseId = caseItem.id }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
