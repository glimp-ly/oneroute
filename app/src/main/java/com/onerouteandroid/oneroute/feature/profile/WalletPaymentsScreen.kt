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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.components.TonalPillButton
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.SuccessGreen
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary
import com.onerouteandroid.oneroute.data.model.PaymentKind
import kotlinx.coroutines.launch

@Composable
fun WalletPaymentsScreen(state: AppState, nav: AppNavigator) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var withdrawDialog by remember { mutableStateOf(false) }
    var withdrawAmount by remember { mutableStateOf("200") }

    if (withdrawDialog) {
        AlertDialog(
            onDismissRequest = { withdrawDialog = false },
            title = { Text("Transferir a BBVA ••4109 (SPEI)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Disponible: $${"%.2f".format(state.walletBalance)} MXN", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    AppTextField(value = withdrawAmount, onValueChange = { withdrawAmount = it }, label = "Monto a transferir (MXN)", keyboardType = KeyboardType.Number)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = withdrawAmount.toDoubleOrNull() ?: 0.0
                    if (state.withdrawToBank(amount)) {
                        withdrawDialog = false
                        Toast.makeText(context, "Transferencia SPEI por $${"%.2f".format(amount)} MXN enviada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Monto inválido o saldo insuficiente", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Transferir") }
            },
            dismissButton = { TextButton(onClick = { withdrawDialog = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Billetera",
                showBrandLogo = true,
                onBack = { nav.back() },
                onClose = { nav.back() },
                avatarInitials = state.currentUser.initials,
                onAvatarClick = { nav.selectTab(MainTab.PERFIL) }
            )
        },
        containerColor = CanvasBackground
    ) { padding ->
        ScreenList(padding, listState = listState) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(PrimaryBlue, PrimaryBlueDark)))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("SALDO ACUMULADO DISPONIBLE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        Text("$${"%.2f".format(state.walletBalance)} MXN", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                        StatusPill("+$420.00 MXN esta semana", container = Color.White.copy(alpha = 0.16f), content = Color.White)
                        Button(
                            onClick = { withdrawDialog = true },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = CardSurface, contentColor = PrimaryBlueDark),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Transferir a mi banco (SPEI)", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = { scope.launch { listState.animateScrollToItem(4) } },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Ver movimientos", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 22) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Depósitos de Conductor", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            StatusPill("Verificada", container = PrimaryContainer, content = PrimaryBlueDark, icon = Icons.Rounded.Verified)
                        }
                        Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconBubble(Icons.Rounded.AccountBalance, size = 40, container = PrimaryContainer, tint = PrimaryBlueDark)
                                Column(Modifier.weight(1f)) {
                                    Text("BBVA México ••4109", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text("CLABE Interbancaria activa", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Text("Transferencias automáticas sin comisión cada lunes a las 06:00.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Métodos para Pagar Viajes", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Tarjetas activas para tus traslados como pasajero", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
                                } else if (method.kind == PaymentKind.GOOGLE_PAY) {
                                    StatusPill("Vinculado", container = PrimaryContainer, content = PrimaryBlueDark)
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
                SectionHeader("Movimientos Recientes", action = "Ver todos")
            }

            items(state.walletTransactions, key = { it.id }) { tx ->
                AppCard(container = SoftContainer, corner = 18) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val icon = when {
                            tx.amount < 0 && tx.title.contains("SPEI") -> Icons.Rounded.ArrowOutward
                            tx.amount < 0 -> Icons.Rounded.AirlineSeatReclineNormal
                            tx.subtitle.contains("pasajeros") -> Icons.Rounded.Groups
                            else -> Icons.Rounded.DirectionsCar
                        }
                        IconBubble(icon, size = 40, container = CardSurface, tint = PrimaryBlueDark)
                        Column(Modifier.weight(1f)) {
                            Text(tx.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(tx.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val formatted = if (tx.amount >= 0) "+ $${"%.2f".format(tx.amount)}" else "- $${"%.2f".format(-tx.amount)}"
                            Text(formatted, style = MaterialTheme.typography.titleSmall, color = if (tx.amount >= 0) PrimaryBlue else TextPrimary)
                            Text(tx.statusLabel, style = MaterialTheme.typography.labelSmall, color = if (tx.statusLabel == "Exitoso") SuccessGreen else TextMuted)
                        }
                    }
                }
            }

            item {
                AppCard(container = SoftContainer, corner = 22) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconBubble(Icons.Rounded.Description, size = 38, container = PrimaryBlue, tint = Color.White)
                            Text("Facturación y CFDI 4.0", style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                            StatusPill("RFC: CACX9108•••", container = CardSurface, content = TextSecondary)
                        }
                        Text("Descarga tus facturas mensuales de comisiones y cobros con validez ante el SAT.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(onClick = { Toast.makeText(context, "Descargando PDF / XML...", Toast.LENGTH_SHORT).show() }, color = CardSurface, shape = CircleShape) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    Text("PDF / XML Octubre", style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                                }
                            }
                            Surface(onClick = { Toast.makeText(context, "Datos fiscales verificados", Toast.LENGTH_SHORT).show() }, color = CardSurface, shape = CircleShape) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    Text("Datos fiscales", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
