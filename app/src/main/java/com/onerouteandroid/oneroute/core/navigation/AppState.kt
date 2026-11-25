package com.onerouteandroid.oneroute.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onerouteandroid.oneroute.data.model.*
import com.onerouteandroid.oneroute.data.repository.MockRepository

class AppState {
    var currentUser by mutableStateOf(MockRepository.user)
        private set
    var vehicle by mutableStateOf(MockRepository.vehicle)
        private set
    var documents by mutableStateOf(MockRepository.documents)
        private set
    var availableTrips by mutableStateOf(MockRepository.availableTrips)
        private set
    var scheduledTrips by mutableStateOf(MockRepository.scheduledTrips)
        private set
    var historyTrips by mutableStateOf(MockRepository.historyTrips)
        private set
    var tripDraft by mutableStateOf(TripDraft())
        private set
    var conversations by mutableStateOf(MockRepository.conversations)
        private set
    var supportMessages by mutableStateOf(MockRepository.supportMessages)
        private set
    var notifications by mutableStateOf(MockRepository.notifications)
        private set
    var paymentMethods by mutableStateOf(MockRepository.paymentMethods)
        private set
    var selectedPaymentId by mutableStateOf(MockRepository.paymentMethods.first().id)
        private set
    var walletTransactions by mutableStateOf(MockRepository.walletTransactions)
        private set
    var walletBalance by mutableStateOf(MockRepository.INITIAL_BALANCE)
        private set
    var driverModeEnabled by mutableStateOf(true)
        private set

    private var publishedCounter = 0
    private var generatedId = 0

    val unreadNotifications: Int get() = notifications.count { !it.read }
    val unreadMessages: Int get() = conversations.sumOf { it.unread }

    fun registerUser(name: String, email: String, phone: String) {
        currentUser = currentUser.copy(name = name, email = email, phone = phone)
    }

    fun updateUserName(name: String) {
        currentUser = currentUser.copy(name = name)
    }

    fun scheduledTrip(id: String): ScheduledTrip? = scheduledTrips.firstOrNull { it.id == id }

    fun availableTrip(id: String): AvailableTrip? = availableTrips.firstOrNull { it.id == id }

    fun bookTrip(tripId: String): ScheduledTrip? {
        val trip = availableTrip(tripId)?.takeIf { it.seatsLeft > 0 } ?: return null
        val booked = ScheduledTrip(
            id = "bk-${trip.id}-${++generatedId}",
            role = TripRole.PASSENGER,
            dateTag = trip.dateTag,
            seatInfo = "Asiento ${trip.seatsLeft}A",
            status = "Confirmado",
            departure = trip.departure,
            arrival = trip.arrival,
            origin = trip.origin,
            originDetail = trip.originDetail,
            destination = trip.destination,
            destinationDetail = trip.destinationDetail,
            durationMin = trip.durationMin,
            price = trip.price,
            driver = trip.driver,
            boardingCode = "#${4000 + generatedId}",
            paymentCard = paymentMethods.first { it.id == selectedPaymentId }.let { "${it.name} ${it.detail}" }
        )
        availableTrips = availableTrips.map { if (it.id == tripId) it.copy(seatsLeft = it.seatsLeft - 1) else it }
        scheduledTrips = scheduledTrips + booked
        pushNotification(
            title = "Reserva confirmada",
            body = "Tu lugar con ${trip.driver.name} hacia ${trip.destination} a las ${trip.departure} está asegurado.",
            category = NotificationCategory.TRIPS,
            kind = NotificationKind.TRIP
        )
        return booked
    }

    fun updateDraft(transform: (TripDraft) -> TripDraft) {
        tripDraft = transform(tripDraft)
    }

    fun publishDraftTrip(): ScheduledTrip {
        val draft = tripDraft
        val published = ScheduledTrip(
            id = "RT-${84920 + publishedCounter++}",
            role = TripRole.DRIVER,
            dateTag = draft.dateLabel.uppercase(),
            seatInfo = "0 de ${draft.seats} asientos ocupados",
            status = "Publicado por ti",
            departure = draft.time,
            arrival = draft.time.arrivalAfter(45),
            origin = draft.origin.substringBefore(" ("),
            originDetail = draft.origin.substringAfter("(", draft.pickupPoint).removeSuffix(")"),
            destination = draft.destination.substringBefore(" ("),
            destinationDetail = draft.destination.substringAfter("(", "").removeSuffix(")"),
            durationMin = 45,
            price = draft.earnings
        )
        scheduledTrips = scheduledTrips + published
        pushNotification(
            title = "Viaje publicado",
            body = "Tu ruta ${published.origin} → ${published.destination} ya es visible para los pasajeros.",
            category = NotificationCategory.TRIPS,
            kind = NotificationKind.TRIP
        )
        tripDraft = TripDraft()
        return published
    }

    fun cancelScheduledTrip(tripId: String, reason: String) {
        val trip = scheduledTrip(tripId) ?: return
        scheduledTrips = scheduledTrips.filterNot { it.id == tripId }
        historyTrips = listOf(
            HistoryTrip(
                id = "ht-cancel-${++generatedId}",
                month = "Noviembre 2024",
                dateLabel = "Hoy",
                status = HistoryStatus.CANCELLED,
                statusLabel = "Cancelado por ti",
                amount = trip.price,
                origin = trip.origin,
                originDetail = trip.originDetail,
                destination = trip.destination,
                destinationDetail = trip.destinationDetail,
                counterpart = "Sistema",
                counterpartInfo = reason,
                refundNote = if (trip.role == TripRole.PASSENGER) "Reembolso del 100% • $${trip.price}.00 MXN acreditados" else null
            )
        ) + historyTrips
        if (trip.role == TripRole.PASSENGER) {
            walletBalance += trip.price
            walletTransactions = listOf(
                WalletTransaction("w-refund-$generatedId", "Reembolso ${trip.origin} a ${trip.destination}", "Cancelación • Hoy", trip.price.toDouble(), "Reembolsado")
            ) + walletTransactions
        }
    }

    fun completeActiveTrip(tripId: String) {
        val trip = scheduledTrip(tripId) ?: return
        scheduledTrips = scheduledTrips.filterNot { it.id == tripId }
        historyTrips = listOf(
            HistoryTrip(
                id = "ht-done-$tripId",
                month = "Noviembre 2024",
                dateLabel = "Hoy",
                status = HistoryStatus.COMPLETED,
                statusLabel = "Completado",
                amount = trip.price,
                origin = trip.origin,
                originDetail = "${trip.departure} • ${trip.originDetail}",
                destination = trip.destination,
                destinationDetail = "${trip.arrival} • ${trip.destinationDetail}",
                counterpart = trip.driver?.name ?: "Pasajeros",
                counterpartInfo = trip.driver?.let { "${it.vehicle} ${it.color}" } ?: "Viaje compartido",
                counterpartRating = trip.driver?.rating,
                canRate = true
            )
        ) + historyTrips
    }

    fun submitRating(tripId: String?, stars: Int, tags: Set<String>, tip: Int, comment: String, anonymous: Boolean) {
        require(stars in 1..5)
        historyTrips = historyTrips.map {
            if (it.id == tripId || it.id == "ht-done-$tripId") it.copy(canRate = false) else it
        }
        pushNotification(
            title = "Calificación enviada",
            body = if (tip > 0) "Gracias por calificar con $stars estrellas y enviar \$$tip MXN de propina." else "Gracias por calificar tu viaje con $stars estrellas.",
            category = NotificationCategory.SYSTEM,
            kind = NotificationKind.RATING
        )
    }

    fun sendRideMessage(conversationId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        conversations = conversations.map {
            if (it.id != conversationId) it
            else it.copy(
                messages = it.messages + ChatMessage("msg-${++generatedId}", true, clean, "Ahora"),
                preview = clean,
                time = "Ahora",
                unread = 0
            )
        }
    }

    fun markConversationRead(conversationId: String) {
        conversations = conversations.map { if (it.id == conversationId) it.copy(unread = 0) else it }
    }

    fun sendSupportMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        supportMessages = supportMessages + ChatMessage("sm-${++generatedId}", true, clean, "Ahora")
    }

    fun markAllNotificationsRead() {
        notifications = notifications.map { it.copy(read = true) }
    }

    fun markNotificationRead(id: String) {
        notifications = notifications.map { if (it.id == id) it.copy(read = true) else it }
    }

    fun updateVehicle(updated: VehicleInfo) {
        vehicle = updated
    }

    fun toggleDriverMode(enabled: Boolean) {
        driverModeEnabled = enabled
    }

    fun selectPayment(id: String) {
        if (paymentMethods.any { it.id == id }) selectedPaymentId = id
    }

    fun withdrawToBank(amount: Double): Boolean {
        if (amount <= 0 || amount > walletBalance) return false
        walletBalance -= amount
        walletTransactions = listOf(
            WalletTransaction("w-out-${++generatedId}", "Retiro SPEI a BBVA", "Cuenta ••4109 • Hoy", -amount, "Exitoso")
        ) + walletTransactions
        return true
    }

    private fun pushNotification(title: String, body: String, category: NotificationCategory, kind: NotificationKind) {
        notifications = listOf(
            NotificationItem("n-${++generatedId}", title, body, "Ahora", "Hoy", category, kind, read = false)
        ) + notifications
    }
}

private fun String.arrivalAfter(minutes: Int): String {
    val (h, m) = split(':').map { it.toInt() }
    val total = h * 60 + m + minutes
    return "%02d:%02d".format(total / 60 % 24, total % 60)
}
