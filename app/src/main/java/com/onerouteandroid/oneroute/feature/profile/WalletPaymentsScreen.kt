package com.onerouteandroid.oneroute.feature.profile

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.AppCard
import com.onerouteandroid.oneroute.core.components.AppTextField
import com.onerouteandroid.oneroute.core.components.AppTopBar
import com.onerouteandroid.oneroute.core.components.IconBubble
import com.onerouteandroid.oneroute.core.components.MainTab
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
import com.onerouteandroid.oneroute.core.theme.SuccessGreen
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.core.theme.WarningAmber
import com.onerouteandroid.oneroute.core.theme.WarningContainer
import com.onerouteandroid.oneroute.data.model.PaymentKind
import com.onerouteandroid.oneroute.data.model.WalletType
import kotlinx.coroutines.launch

@Composable
fun WalletPaymentsScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedWalletTab by remember { mutableIntStateOf(1) } // 0=Pasajero, 1=Ganancias, 2=Comisiones
    var withdrawDialog by remember { mutableStateOf(false) }
    var withdrawAmount by remember { mutableStateOf("100") }
    var withdrawMethod by remember { mutableStateOf("Transferencia CCI") }
    var rechargeTarget by remember { mutableStateOf<WalletType?>(null) }
    var rechargeAmount by remember { mutableStateOf("50") }

    val activeWallet = when (selectedWalletTab) {
        0 -> WalletType.PASAJERO
        1 -> WalletType.GANANCIAS
        else -> WalletType.COMISIONES
    }
    val activeBalance = when (activeWallet) {
        WalletType.PASAJERO -> state.passengerWalletBalance
        WalletType.GANANCIAS -> state.earningsWalletBalance
        WalletType.COMISIONES -> state.commissionWalletBalance
    }
    val filteredTxs = state.walletTransactions.filter { it.walletType == activeWallet }

    if (withdrawDialog) {
        AlertDialog(
            onDismissRequest = { withdrawDialog = false },
            title = { Text("Retirar de Billetera Ganancias") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Disponible: S/ ${"%.2f".format(state.earningsWalletBalance)} PEN", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Transferencia CCI", "Yape", "Plin").forEach { m ->
                            SelectChip(text = m, selected = withdrawMethod == m, onClick = { withdrawMethod = m })
                        }
                    }
                    AppTextField(value = withdrawAmount, onValueChange = { withdrawAmount = it }, label = "Monto a retirar (PEN)", keyboardType = KeyboardType.Number)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = withdrawAmount.toDoubleOrNull() ?: 0.0
                    if (state.withdrawToBank(amount, "Retiro $withdrawMethod")) {
                        withdrawDialog = false
                        Toast.makeText(context, "Retiro por S/ ${"%.2f".format(amount)} PEN enviado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Monto inválido o saldo insuficiente", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Confirmar retiro") }
            },
            dismissButton = { TextButton(onClick = { withdrawDialog = false }) { Text("Cancelar") } }
        )
    }

    if (rechargeTarget != null) {
        val target = rechargeTarget!!
        AlertDialog(
            onDismissRequest = { rechargeTarget = null },
            title = { Text("Recargar ${target.label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (target == WalletType.COMISIONES) "Cubre las comisiones de tus viajes cobrados en efectivo."
                        else "Usa este saldo interno para pagar total o parcialmente tus reservas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    AppTextField(value = rechargeAmount, onValueChange = { rechargeAmount = it }, label = "Monto a recargar (PEN)", keyboardType = KeyboardType.Number)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = rechargeAmount.toDoubleOrNull() ?: 0.0
                    val ok = if (target == WalletType.COMISIONES) state.rechargeCommissionWallet(amount) else state.rechargePassengerWallet(amount)
                    if (ok) {
                        rechargeTarget = null
                        Toast.makeText(context, "Recarga de S/ ${"%.2f".format(amount)} PEN acreditada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Ingresa un monto válido", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Recargar ahora") }
            },
            dismissButton = { TextButton(onClick = { rechargeTarget = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Billeteras y Pagos",
                showBrandLogo = true,
                onBack = { nav.back() },
                onClose = { nav.back() },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = selectedWalletTab, containerColor = CardSurface, contentColor = PrimaryBlue) {
                Tab(selected = selectedWalletTab == 0, onClick = { selectedWalletTab = 0 }, text = { Text("Pasajero") })
                Tab(selected = selectedWalletTab == 1, onClick = { selectedWalletTab = 1 }, text = { Text("Ganancias") })
                Tab(selected = selectedWalletTab == 2, onClick = { selectedWalletTab = 2 }, text = { Text("Comisiones") })
            }

            ScreenList(
                padding = androidx.compose.foundation.layout.PaddingValues(bottom = padding.calculateBottomPadding()),
                listState = listState
            ) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Brush.linearGradient(listOf(PrimaryBlue, PrimaryBlueDark)))
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(activeWallet.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            Text("S/ ${"%.2f".format(activeBalance)} PEN", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            val subtitle = when (activeWallet) {
                                WalletType.PASAJERO -> "Crédito interno para viajes futuros (no retirable)"
                                WalletType.GANANCIAS -> "Neto de cobros digitales (solo retiro bancario/Yape/Plin)"
                                WalletType.COMISIONES -> "Saldo para cubrir comisiones de viajes en efectivo"
                            }
                            StatusPill(subtitle, container = Color.White.copy(alpha = 0.16f), content = Color.White)

                            when (activeWallet) {
                                WalletType.GANANCIAS -> {
                                    Button(
                                        onClick = { withdrawDialog = true },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = CardSurface, contentColor = PrimaryBlueDark),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("  Retirar fondos (CCI / Yape / Plin)", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                                WalletType.COMISIONES, WalletType.PASAJERO -> {
                                    Button(
                                        onClick = { rechargeTarget = activeWallet },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = CardSurface, contentColor = PrimaryBlueDark),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("  Recargar ${activeWallet.label}", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }

                            Button(
                                onClick = { scope.launch { listState.animateScrollToItem(4) } },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Ver movimientos de ${activeWallet.label}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                if (!state.canPublishTrip()) {
                    item {
                        AppCard(container = DangerContainer, corner = 18) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = DangerRed)
                                Text("Saldo insuficiente en Billetera de Comisiones: recarga para poder publicar nuevos viajes.", style = MaterialTheme.typography.bodySmall, color = DangerRed)
                            }
                        }
                    }
                }

                if (state.pendingCredits.isNotEmpty()) {
                    item {
                        SectionHeader("Créditos retenidos (24 h)", badge = state.pendingCredits.size.toString())
                    }
                    items(state.pendingCredits, key = { it.id }) { credit ->
                        AppCard(container = WarningContainer, corner = 18) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IconBubble(Icons.Rounded.Schedule, size = 36, container = CardSurface, tint = WarningAmber)
                                Column(Modifier.weight(1f)) {
                                    Text(credit.description, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text("Se acredita a Saldo Pasajero en ${credit.availableInHours} h", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Text("+S/ ${"%.2f".format(credit.amount)}", style = MaterialTheme.typography.titleSmall, color = WarningAmber)
                            }
                        }
                    }
                }

                item {
                    AppCard(container = SoftContainer, corner = 22) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Cuenta de Retiro (Ganancias)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                StatusPill("Verificada", container = PrimaryContainer, content = PrimaryBlueDark, icon = Icons.Rounded.Verified)
                            }
                            Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    IconBubble(Icons.Rounded.AccountBalance, size = 40, container = PrimaryContainer, tint = PrimaryBlueDark)
                                    Column(Modifier.weight(1f)) {
                                        Text("BCP Perú ••4109", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        Text("CCI Interbancario / Yape activo", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Métodos de Pago (Digital, Billetera, Efectivo, Mixto)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("El cobro digital se ejecuta recién al confirmar tu abordaje", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.paymentMethods.forEach { method ->
                            val selected = method.id == state.selectedPaymentId
                            AppCard(onClick = { state.selectPayment(method.id) }, container = SoftContainer, corner = 18) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(color = CardSurface, shape = RoundedCornerShape(10.dp)) {
                                        val tag = when (method.kind) {
                                            PaymentKind.MASTERCARD -> "MC"
                                            PaymentKind.VISA -> "VISA"
                                            PaymentKind.GOOGLE_PAY -> "G Pay"
                                            PaymentKind.CASH -> "S/"
                                            PaymentKind.WALLET -> "SALDO"
                                            PaymentKind.MIXED -> "MIXTO"
                                        }
                                        Text(tag, style = MaterialTheme.typography.labelLarge, color = PrimaryBlueDark, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(method.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                            if (selected) StatusPill("Predeterminada", container = PrimaryContainer, content = PrimaryBlueDark)
                                        }
                                        Text(method.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                    if (selected) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Seleccionada", tint = PrimaryBlue)
                                    } else {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TonalPillButton(
                        text = "Agregar nuevo método de pago",
                        icon = Icons.Rounded.AddCard,
                        onClick = { Toast.makeText(context, "Alta de tarjeta disponible próximamente", Toast.LENGTH_SHORT).show() }
                    )
                }

                item {
                    SectionHeader("Movimientos (${activeWallet.label})", badge = filteredTxs.size.toString())
                }

                items(filteredTxs, key = { it.id }) { tx ->
                    AppCard(container = SoftContainer, corner = 18) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val icon = when {
                                tx.amount < 0 && tx.title.contains("Retiro") -> Icons.Rounded.ArrowOutward
                                tx.amount < 0 -> Icons.Rounded.AirlineSeatReclineNormal
                                tx.walletType == WalletType.COMISIONES -> Icons.Rounded.Payments
                                tx.subtitle.contains("pasajeros") -> Icons.Rounded.Groups
                                else -> Icons.Rounded.DirectionsCar
                            }
                            IconBubble(icon, size = 40, container = CardSurface, tint = PrimaryBlueDark)
                            Column(Modifier.weight(1f)) {
                                Text(tx.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(tx.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val formatted = if (tx.amount >= 0) "+ S/ ${"%.2f".format(tx.amount)}" else "- S/ ${"%.2f".format(-tx.amount)}"
                                Text(formatted, style = MaterialTheme.typography.titleSmall, color = if (tx.amount >= 0) PrimaryBlue else TextPrimary)
                                Text(tx.statusLabel, style = MaterialTheme.typography.labelSmall, color = if (tx.statusLabel == "Exitoso" || tx.statusLabel == "Disponible") SuccessGreen else TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
