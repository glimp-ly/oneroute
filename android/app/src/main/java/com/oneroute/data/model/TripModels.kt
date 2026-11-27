package com.oneroute.data.model

data class Driver(
    val id: String,
    val name: String,
    val rating: Double,
    val totalTrips: Int,
    val vehicle: String,
    val color: String,
    val plate: String,
    val badge: String? = null,
    val verified: Boolean = true
) {
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

data class AvailableTrip(
    val id: String,
    val driver: Driver,
    val dateTag: String,
    val departure: String,
    val arrival: String,
    val origin: String,
    val originDetail: String,
    val destination: String,
    val destinationDetail: String,
    val durationMin: Int,
    val price: Int,
    val seatsLeft: Int,
    val via: String,
    val perks: List<String>,
    val autoAccept: Boolean = false,
    val gpsReliable: Boolean = true,
    val distanceKm: Double = 14.2
)

enum class TripRole { PASSENGER, DRIVER }

data class ScheduledTrip(
    val id: String,
    val role: TripRole,
    val dateTag: String,
    val seatInfo: String,
    val status: String,
    val departure: String,
    val arrival: String,
    val origin: String,
    val originDetail: String,
    val destination: String,
    val destinationDetail: String,
    val durationMin: Int,
    val price: Int,
    val driver: Driver? = null,
    val boardingCode: String? = null,
    val paymentCard: String? = null,
    val passengersSummary: String? = null,
    val seatsCount: Int = 1,
    val pickupChangesUsed: Int = 0,
    val destinationChangesUsed: Int = 0,
    val earlyFinished: Boolean = false,
    val driverArrivedAtMillis: Long? = null,
    val waitExtendedMinutes: Int = 0,
    val trackingShareUrl: String? = null,
    val noShowMarked: Boolean = false,
    val acceptsNewBookings: Boolean = true,
    val autoAccept: Boolean = false
)

enum class HistoryStatus { COMPLETED, CANCELLED, AS_DRIVER }

data class HistoryTrip(
    val id: String,
    val month: String,
    val dateLabel: String,
    val status: HistoryStatus,
    val statusLabel: String,
    val amount: Int,
    val origin: String,
    val originDetail: String,
    val destination: String,
    val destinationDetail: String,
    val counterpart: String,
    val counterpartInfo: String,
    val counterpartRating: Double? = null,
    val canRate: Boolean = false,
    val refundNote: String? = null
)

data class TripDraft(
    val origin: String = "Condesa (Parque México)",
    val destination: String = "Santa Fe (Edificio Arcos)",
    val dateLabel: String = "Hoy",
    val time: String = "18:30",
    val seats: Int = 3,
    val pricePerSeat: Int = 65,
    val luggage: String = "Medio (Mochila / Maletín)",
    val rules: Set<String> = setOf("No fumar", "Aire acondicionado"),
    val pickupPoint: String = "Av. México 123",
    val pickupReference: String = "Frente al quiosco del Parque México",
    val acceptsNewBookings: Boolean = true,
    val autoAccept: Boolean = false,
    val distanceKm: Double = 14.8
) {
    val earnings: Int get() = seats * pricePerSeat
}

enum class RideRequestStatus(val label: String) {
    PENDIENTE("Esperando conductor"),
    ACEPTADA_HOLD("Retención activa (60 s)"),
    CONFIRMADA("Confirmada"),
    RECHAZADA("Rechazada"),
    EXPIRADA("Expirada"),
    CANCELADA("Cancelada")
}

data class RideRequest(
    val id: String,
    val tripId: String,
    val tripTitle: String,
    val passengerName: String,
    val driverName: String,
    val pickupPoint: String,
    val destinationPoint: String,
    val seatsRequested: Int,
    val estimatedPrice: Int,
    val paymentMethod: String,
    val status: RideRequestStatus,
    val attemptsUsed: Int,
    val maxAttempts: Int = 3,
    val isIncomingForDriver: Boolean,
    val autoAccepted: Boolean = false,
    val remainingSeconds: Int = 60,
    val rejectionReason: String? = null
)
