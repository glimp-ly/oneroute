package com.onerouteandroid.oneroute.data.model

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
    val perks: List<String>
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
    val passengersSummary: String? = null
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
    val pickupReference: String = "Frente al quiosco del Parque México"
) {
    val earnings: Int get() = seats * pricePerSeat
}
