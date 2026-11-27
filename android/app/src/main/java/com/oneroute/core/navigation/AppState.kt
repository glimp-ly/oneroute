package com.oneroute.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.oneroute.data.model.*
import com.oneroute.data.repository.MockRepository

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
    var rideRequests by mutableStateOf(MockRepository.incomingDriverRequests)
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
    var passengerWalletBalance by mutableStateOf(MockRepository.INITIAL_PASSENGER_BALANCE)
        private set
    var earningsWalletBalance by mutableStateOf(MockRepository.INITIAL_EARNINGS_BALANCE)
        private set
    var commissionWalletBalance by mutableStateOf(MockRepository.INITIAL_COMMISSION_BALANCE)
        private set
    var pendingCredits by mutableStateOf<List<PendingWalletCredit>>(emptyList())
        private set
    var emergencyContacts by mutableStateOf(MockRepository.emergencyContacts)
        private set
    var reportedIncidents by mutableStateOf<List<TripIncidentRecord>>(emptyList())
        private set
    var userReports by mutableStateOf<List<UserReportRecord>>(emptyList())
        private set
    var receivedReportCases by mutableStateOf(MockRepository.receivedReportCases)
        private set
    var driverModeEnabled by mutableStateOf(true)
        private set

    private var publishedCounter = 0
    private var generatedId = 0

    val walletBalance: Double get() = earningsWalletBalance
    val unreadNotifications: Int get() = notifications.count { !it.read }
    val unreadMessages: Int get() = conversations.sumOf { it.unread }
    val incomingPendingRequests: List<RideRequest>
        get() = rideRequests.filter { it.isIncomingForDriver && it.status == RideRequestStatus.PENDIENTE }
    val passengerActiveRequests: List<RideRequest>
        get() = rideRequests.filter {
            !it.isIncomingForDriver && (it.status == RideRequestStatus.PENDIENTE || it.status == RideRequestStatus.ACEPTADA_HOLD)
        }

    fun canPublishTrip(): Boolean = commissionWalletBalance > 0.0

    fun registerUser(name: String, email: String, phone: String) {
        currentUser = currentUser.copy(name = name, email = email, phone = phone)
    }

    fun updateUserName(name: String) {
        currentUser = currentUser.copy(name = name)
    }

    fun scheduledTrip(id: String): ScheduledTrip? = scheduledTrips.firstOrNull { it.id == id }

    fun availableTrip(id: String): AvailableTrip? = availableTrips.firstOrNull { it.id == id }

    fun attemptsUsedForTrip(tripId: String): Int =
        rideRequests
            .filter { !it.isIncomingForDriver && it.tripId == tripId }
            .maxOfOrNull { it.attemptsUsed } ?: 0

    /**
     * Flujo del Word: Solicitud -> Aceptación (manual o automática) -> Hold 60 s -> Confirmación del pasajero.
     */
    fun requestTrip(
        tripId: String,
        pickupPoint: String? = null,
        destinationPoint: String? = null,
        seats: Int = 1,
        paymentMethodName: String? = null
    ): RideRequest? {
        val trip = availableTrip(tripId) ?: return null
        if (seats <= 0 || seats > trip.seatsLeft) return null

        val hasActiveSameTrip = rideRequests.any {
            !it.isIncomingForDriver && it.tripId == tripId &&
                (it.status == RideRequestStatus.PENDIENTE || it.status == RideRequestStatus.ACEPTADA_HOLD)
        }
        if (hasActiveSameTrip) return null

        val usedAttempts = attemptsUsedForTrip(tripId)
        if (usedAttempts >= 3) return null

        val autoAccepted = trip.autoAccept && trip.gpsReliable
        val initialStatus = if (autoAccepted) RideRequestStatus.ACEPTADA_HOLD else RideRequestStatus.PENDIENTE
        val method = paymentMethodName
            ?: paymentMethods.firstOrNull { it.id == selectedPaymentId }?.name
            ?: "Pago digital"

        val request = RideRequest(
            id = "req-${++generatedId}",
            tripId = trip.id,
            tripTitle = "${trip.origin} → ${trip.destination} (${trip.departure})",
            passengerName = currentUser.name,
            driverName = trip.driver.name,
            pickupPoint = pickupPoint?.takeIf { it.isNotBlank() } ?: trip.originDetail,
            destinationPoint = destinationPoint?.takeIf { it.isNotBlank() } ?: trip.destinationDetail,
            seatsRequested = seats,
            estimatedPrice = trip.price * seats,
            paymentMethod = method,
            status = initialStatus,
            attemptsUsed = usedAttempts,
            isIncomingForDriver = false,
            autoAccepted = autoAccepted,
            remainingSeconds = 60
        )
        rideRequests = listOf(request) + rideRequests

        pushNotification(
            title = if (autoAccepted) "Solicitud aceptada automáticamente" else "Solicitud enviada al conductor",
            body = if (autoAccepted) {
                "Tienes 60 segundos para confirmar tu reserva con ${trip.driver.name}."
            } else {
                "${trip.driver.name} tiene 60 segundos para responder tu solicitud."
            },
            category = NotificationCategory.TRIPS,
            kind = NotificationKind.TRIP
        )
        return request
    }

    fun acceptRideRequest(requestId: String): Boolean {
        val req = rideRequests.firstOrNull { it.id == requestId && it.status == RideRequestStatus.PENDIENTE } ?: return false
        rideRequests = rideRequests.map {
            if (it.id == requestId) it.copy(status = RideRequestStatus.ACEPTADA_HOLD, remainingSeconds = 60) else it
        }
        pushNotification(
            title = "Solicitud aceptada (Hold 60 s)",
            body = "Se retuvo capacidad para ${req.tripTitle}. Confirma dentro de 1 minuto.",
            category = NotificationCategory.TRIPS,
            kind = NotificationKind.TRIP
        )
        return true
    }

    fun rejectRideRequest(requestId: String, reason: String): Boolean {
        val req = rideRequests.firstOrNull { it.id == requestId && it.status == RideRequestStatus.PENDIENTE } ?: return false
        val newAttempts = (req.attemptsUsed + 1).coerceAtMost(req.maxAttempts)
        rideRequests = rideRequests.map {
            if (it.id == requestId) {
                it.copy(
                    status = RideRequestStatus.RECHAZADA,
                    attemptsUsed = newAttempts,
                    rejectionReason = reason.ifBlank { "Sin disponibilidad en el desvío" }
                )
            } else it
        }
        return true
    }

    /**
     * Si el pasajero cancela antes de que el conductor responda (PENDIENTE), no consume intento.
     */
    fun cancelPendingRequestByPassenger(requestId: String): Boolean {
        val req = rideRequests.firstOrNull {
            it.id == requestId &&
                (it.status == RideRequestStatus.PENDIENTE || it.status == RideRequestStatus.ACEPTADA_HOLD)
        } ?: return false
        rideRequests = rideRequests.map {
            if (it.id == requestId) it.copy(status = RideRequestStatus.CANCELADA) else it
        }
        return true
    }

    fun expireRideRequest(requestId: String): Boolean {
        val req = rideRequests.firstOrNull {
            it.id == requestId &&
                (it.status == RideRequestStatus.PENDIENTE || it.status == RideRequestStatus.ACEPTADA_HOLD)
        } ?: return false
        val newAttempts = (req.attemptsUsed + 1).coerceAtMost(req.maxAttempts)
        rideRequests = rideRequests.map {
            if (it.id == requestId) it.copy(status = RideRequestStatus.EXPIRADA, attemptsUsed = newAttempts, remainingSeconds = 0) else it
        }
        return true
    }

    fun confirmHeldRequest(requestId: String): ScheduledTrip? {
        val req = rideRequests.firstOrNull {
            it.id == requestId && !it.isIncomingForDriver && it.status == RideRequestStatus.ACEPTADA_HOLD
        } ?: return null
        val trip = availableTrip(req.tripId)?.takeIf { it.seatsLeft >= req.seatsRequested } ?: return null

        val booked = ScheduledTrip(
            id = "bk-${trip.id}-${++generatedId}",
            role = TripRole.PASSENGER,
            dateTag = trip.dateTag,
            seatInfo = "${req.seatsRequested} ${if (req.seatsRequested == 1) "asiento" else "asientos"}",
            status = "Confirmado",
            departure = trip.departure,
            arrival = trip.arrival,
            origin = trip.origin,
            originDetail = req.pickupPoint,
            destination = trip.destination,
            destinationDetail = req.destinationPoint,
            durationMin = trip.durationMin,
            price = req.estimatedPrice,
            driver = trip.driver,
            boardingCode = "#${4000 + generatedId}",
            paymentCard = req.paymentMethod,
            seatsCount = req.seatsRequested
        )

        availableTrips = availableTrips.map {
            if (it.id == trip.id) it.copy(seatsLeft = (it.seatsLeft - req.seatsRequested).coerceAtLeast(0)) else it
        }
        scheduledTrips = scheduledTrips + booked

        // Al confirmar uno, cancela las otras solicitudes pendientes/aceptadas del pasajero en otros viajes.
        rideRequests = rideRequests.map {
            when {
                it.id == requestId -> it.copy(status = RideRequestStatus.CONFIRMADA)
                !it.isIncomingForDriver &&
                    (it.status == RideRequestStatus.PENDIENTE || it.status == RideRequestStatus.ACEPTADA_HOLD) ->
                    it.copy(status = RideRequestStatus.CANCELADA)
                else -> it
            }
        }

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

    fun publishDraftTrip(): ScheduledTrip? {
        if (!canPublishTrip()) return null
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
            price = draft.earnings,
            seatsCount = draft.seats,
            acceptsNewBookings = draft.acceptsNewBookings,
            autoAccept = draft.autoAccept
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

    fun toggleTripAcceptsBookings(tripId: String) {
        scheduledTrips = scheduledTrips.map {
            if (it.id == tripId) it.copy(acceptsNewBookings = !it.acceptsNewBookings) else it
        }
    }

    fun modifyTripPickup(tripId: String, newPickup: String): Boolean {
        val clean = newPickup.trim()
        val trip = scheduledTrip(tripId) ?: return false
        if (clean.isEmpty() || trip.pickupChangesUsed >= 1) return false
        scheduledTrips = scheduledTrips.map {
            if (it.id == tripId) it.copy(originDetail = clean, pickupChangesUsed = it.pickupChangesUsed + 1) else it
        }
        pushNotification("Recogida actualizada", "Se actualizó tu punto de recogida a $clean.", NotificationCategory.TRIPS, NotificationKind.TRIP)
        return true
    }

    fun modifyTripDestination(tripId: String, newDestination: String, priceDelta: Int): Boolean {
        val clean = newDestination.trim()
        val trip = scheduledTrip(tripId) ?: return false
        if (clean.isEmpty() || trip.destinationChangesUsed >= 1) return false
        val newPrice = (trip.price + priceDelta).coerceAtLeast(0)
        scheduledTrips = scheduledTrips.map {
            if (it.id == tripId) {
                it.copy(
                    destinationDetail = clean,
                    price = newPrice,
                    destinationChangesUsed = it.destinationChangesUsed + 1
                )
            } else it
        }
        if (priceDelta < 0) {
            val credit = (-priceDelta).toDouble()
            passengerWalletBalance += credit
            walletTransactions = listOf(
                WalletTransaction("w-dest-${++generatedId}", "Reducción por cambio de destino", "Acreditado a Saldo Pasajero", credit, "Disponible", WalletType.PASAJERO)
            ) + walletTransactions
        }
        pushNotification("Destino actualizado", "Tu nuevo destino es $clean.", NotificationCategory.TRIPS, NotificationKind.TRIP)
        return true
    }

    fun modifyTripSeats(tripId: String, newSeats: Int, paidAlready: Boolean): Boolean {
        val trip = scheduledTrip(tripId) ?: return false
        if (newSeats !in 1..4 || newSeats == trip.seatsCount) return false
        val perSeat = (trip.price / trip.seatsCount.coerceAtLeast(1)).coerceAtLeast(1)
        val newTotal = perSeat * newSeats
        val diff = newTotal - trip.price

        scheduledTrips = scheduledTrips.map {
            if (it.id == tripId) {
                it.copy(
                    seatsCount = newSeats,
                    seatInfo = "$newSeats ${if (newSeats == 1) "asiento" else "asientos"}",
                    price = newTotal
                )
            } else it
        }

        if (diff < 0 && paidAlready) {
            val heldAmount = (-diff).toDouble()
            pendingCredits = listOf(
                PendingWalletCredit("pc-${++generatedId}", tripId, "Reducción de asientos (${trip.origin} → ${trip.destination})", heldAmount, 24)
            ) + pendingCredits
            pushNotification(
                "Crédito retenido 24 h",
                "Tras aprobación del conductor, S/ ${"%.2f".format(heldAmount)} se acreditarán en 24 horas a tu Saldo Pasajero.",
                NotificationCategory.PAYMENTS,
                NotificationKind.PAYMENT
            )
        }
        return true
    }

    fun finishTripEarly(tripId: String, dropoffPoint: String, recalculatedPrice: Int): Boolean {
        val trip = scheduledTrip(tripId) ?: return false
        val finalPrice = recalculatedPrice.coerceIn(0, trip.price)
        val savings = (trip.price - finalPrice).toDouble()
        if (savings > 0) {
            passengerWalletBalance += savings
            walletTransactions = listOf(
                WalletTransaction("w-early-${++generatedId}", "Saldo por descenso anticipado", dropoffPoint, savings, "Disponible", WalletType.PASAJERO)
            ) + walletTransactions
        }
        scheduledTrips = scheduledTrips.filterNot { it.id == tripId }
        historyTrips = listOf(
            HistoryTrip(
                id = "ht-done-$tripId",
                month = "Noviembre 2026",
                dateLabel = "Hoy (Finalizado antes)",
                status = HistoryStatus.COMPLETED,
                statusLabel = "Descenso anticipado",
                amount = finalPrice,
                origin = trip.origin,
                originDetail = "${trip.departure} • ${trip.originDetail}",
                destination = dropoffPoint,
                destinationDetail = "Finalización anticipada confirmada",
                counterpart = trip.driver?.name ?: "Pasajeros",
                counterpartInfo = trip.driver?.let { "${it.vehicle} ${it.color}" } ?: "Viaje compartido",
                counterpartRating = trip.driver?.rating,
                canRate = true
            )
        ) + historyTrips
        return true
    }

    fun markDriverArrived(tripId: String) {
        scheduledTrips = scheduledTrips.map {
            if (it.id == tripId) it.copy(driverArrivedAtMillis = System.currentTimeMillis()) else it
        }
    }

    fun extendNoShowWait(tripId: String, extraMinutes: Int = 3) {
        scheduledTrips = scheduledTrips.map {
            if (it.id == tripId) it.copy(waitExtendedMinutes = it.waitExtendedMinutes + extraMinutes) else it
        }
        pushNotification("Espera ampliada", "El conductor amplió la espera en +$extraMinutes min.", NotificationCategory.TRIPS, NotificationKind.TRIP)
    }

    fun declareNoShow(tripId: String, observations: String): Boolean {
        val trip = scheduledTrip(tripId) ?: return false
        scheduledTrips = scheduledTrips.filterNot { it.id == tripId }
        historyTrips = listOf(
            HistoryTrip(
                id = "ht-noshow-${++generatedId}",
                month = "Noviembre 2026",
                dateLabel = "Hoy",
                status = HistoryStatus.CANCELLED,
                statusLabel = "No-show registrado",
                amount = 0,
                origin = trip.origin,
                originDetail = trip.originDetail,
                destination = trip.destination,
                destinationDetail = trip.destinationDetail,
                counterpart = trip.driver?.name ?: "Pasajero",
                counterpartInfo = observations.ifBlank { "Espera inicial de 5 min vencida" },
                refundNote = "Sin cobro digital • Capacidad de tramos liberada"
            )
        ) + historyTrips
        return true
    }

    fun generateTrackingLink(tripId: String): String {
        val code = "rt-${tripId.takeLast(4)}-${++generatedId}"
        val url = "https://oneroute.pe/seguir/$code"
        scheduledTrips = scheduledTrips.map {
            if (it.id == tripId) it.copy(trackingShareUrl = url) else it
        }
        return url
    }

    fun revokeTrackingLink(tripId: String) {
        scheduledTrips = scheduledTrips.map {
            if (it.id == tripId) it.copy(trackingShareUrl = null) else it
        }
    }

    fun cancelScheduledTrip(tripId: String, reason: String) {
        val trip = scheduledTrip(tripId) ?: return
        scheduledTrips = scheduledTrips.filterNot { it.id == tripId }
        historyTrips = listOf(
            HistoryTrip(
                id = "ht-cancel-${++generatedId}",
                month = "Noviembre 2026",
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
                refundNote = if (trip.role == TripRole.PASSENGER) "Reembolso del 100% • S/ ${trip.price}.00 PEN acreditados a Saldo Pasajero" else null
            )
        ) + historyTrips
        if (trip.role == TripRole.PASSENGER) {
            passengerWalletBalance += trip.price
            walletTransactions = listOf(
                WalletTransaction("w-refund-$generatedId", "Reembolso ${trip.origin} a ${trip.destination}", "Cancelación • Hoy", trip.price.toDouble(), "Reembolsado", WalletType.PASAJERO)
            ) + walletTransactions
        }
    }

    fun completeActiveTrip(tripId: String) {
        val trip = scheduledTrip(tripId) ?: return
        scheduledTrips = scheduledTrips.filterNot { it.id == tripId }
        historyTrips = listOf(
            HistoryTrip(
                id = "ht-done-$tripId",
                month = "Noviembre 2026",
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
        if (tip > 0) {
            walletTransactions = listOf(
                WalletTransaction("w-tip-${++generatedId}", "Propina enviada", "Calificación ${if (anonymous) "anónima" else "pública"}", -tip.toDouble(), "Exitoso", WalletType.PASAJERO)
            ) + walletTransactions
        }
        pushNotification(
            title = "Calificación enviada",
            body = if (tip > 0) "Gracias por calificar con $stars estrellas y enviar S/ $tip PEN de propina." else "Gracias por calificar tu viaje con $stars estrellas.",
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

    fun withdrawToBank(amount: Double, methodLabel: String = "Retiro CCI a BCP ••4109"): Boolean {
        if (amount <= 0 || amount > earningsWalletBalance) return false
        earningsWalletBalance -= amount
        walletTransactions = listOf(
            WalletTransaction("w-out-${++generatedId}", methodLabel, "Billetera Ganancias • Hoy", -amount, "Exitoso", WalletType.GANANCIAS)
        ) + walletTransactions
        return true
    }

    fun rechargeCommissionWallet(amount: Double): Boolean {
        if (amount <= 0) return false
        commissionWalletBalance += amount
        walletTransactions = listOf(
            WalletTransaction("w-rec-com-${++generatedId}", "Recarga Yape / Plin", "Billetera Comisiones • Hoy", amount, "Exitoso", WalletType.COMISIONES)
        ) + walletTransactions
        return true
    }

    fun rechargePassengerWallet(amount: Double): Boolean {
        if (amount <= 0) return false
        passengerWalletBalance += amount
        walletTransactions = listOf(
            WalletTransaction("w-rec-pas-${++generatedId}", "Recarga Saldo Pasajero", "Pago digital • Hoy", amount, "Exitoso", WalletType.PASAJERO)
        ) + walletTransactions
        return true
    }

    fun addEmergencyContact(name: String, relationship: String, phone: String, isPrimary: Boolean): Boolean {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        if (cleanName.isEmpty() || cleanPhone.isEmpty()) return false
        val base = if (isPrimary) emergencyContacts.map { it.copy(isPrimary = false) } else emergencyContacts
        emergencyContacts = base + EmergencyContact("ec-${++generatedId}", cleanName, relationship.trim().ifBlank { "Contacto" }, cleanPhone, isPrimary || base.isEmpty())
        return true
    }

    fun removeEmergencyContact(id: String) {
        val remaining = emergencyContacts.filterNot { it.id == id }
        emergencyContacts = if (remaining.isNotEmpty() && remaining.none { it.isPrimary }) {
            remaining.mapIndexed { idx, item -> if (idx == 0) item.copy(isPrimary = true) else item }
        } else remaining
    }

    fun setPrimaryEmergencyContact(id: String) {
        emergencyContacts = emergencyContacts.map { it.copy(isPrimary = it.id == id) }
    }

    fun reportIncident(tripId: String, category: String, description: String): Boolean {
        val clean = description.trim()
        if (clean.isEmpty()) return false
        reportedIncidents = listOf(
            TripIncidentRecord("inc-${++generatedId}", tripId, category, clean, "GPS actual registrado", "Ahora")
        ) + reportedIncidents
        pushNotification("Incidente registrado", "Tu reporte de '$category' fue enviado a revisión.", NotificationCategory.SYSTEM, NotificationKind.ALERT)
        return true
    }

    fun submitUserReport(tripId: String, reportedUserName: String, categories: List<String>, description: String): Boolean {
        val cleanName = reportedUserName.trim()
        val cleanDesc = description.trim()
        if (cleanName.isEmpty() || cleanDesc.isEmpty() || categories.isEmpty()) return false
        userReports = listOf(
            UserReportRecord("rep-${++generatedId}", tripId, cleanName, categories, cleanDesc, "Ahora")
        ) + userReports
        pushNotification("Reporte inmutable registrado", "Se registró tu reporte contra $cleanName.", NotificationCategory.SYSTEM, NotificationKind.ALERT)
        return true
    }

    fun submitReportDefense(caseId: String, defenseText: String): Boolean {
        val clean = defenseText.trim()
        if (clean.isEmpty()) return false
        receivedReportCases = receivedReportCases.map {
            if (it.id == caseId) it.copy(defenseSubmitted = true, defenseText = clean) else it
        }
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
