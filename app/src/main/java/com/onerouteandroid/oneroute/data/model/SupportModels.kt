package com.onerouteandroid.oneroute.data.model

enum class WalletType(val label: String) {
    PASAJERO("Saldo Pasajero"),
    GANANCIAS("Ganancias"),
    COMISIONES("Comisiones")
}

enum class PaymentKind { MASTERCARD, VISA, GOOGLE_PAY, CASH, WALLET, MIXED }

data class PaymentMethod(
    val id: String,
    val name: String,
    val detail: String,
    val kind: PaymentKind
)

data class WalletTransaction(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val statusLabel: String,
    val walletType: WalletType = WalletType.GANANCIAS
)

data class PendingWalletCredit(
    val id: String,
    val tripId: String,
    val description: String,
    val amount: Double,
    val availableInHours: Int = 24
)

data class EmergencyContact(
    val id: String,
    val name: String,
    val relationship: String,
    val phone: String,
    val isPrimary: Boolean = false
)

data class TripIncidentRecord(
    val id: String,
    val tripId: String,
    val category: String,
    val description: String,
    val locationLabel: String,
    val createdAt: String,
    val status: String = "Pendiente"
)

data class UserReportRecord(
    val id: String,
    val tripId: String,
    val reportedUserName: String,
    val categories: List<String>,
    val description: String,
    val createdAt: String,
    val status: String = "Recibido (Inmutable)"
)

data class ReceivedReportCase(
    val id: String,
    val tripLabel: String,
    val category: String,
    val summary: String,
    val notifiedAt: String,
    val remainingCalendarDays: Int,
    val defenseSubmitted: Boolean = false,
    val defenseText: String? = null
)

data class FaqTopic(
    val title: String,
    val subtitle: String,
    val articleCount: Int
)

data class FaqArticle(
    val id: String,
    val category: String,
    val question: String,
    val popular: Boolean = false
)
