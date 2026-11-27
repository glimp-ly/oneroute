package com.oneroute.data.model

data class UserProfile(
    val name: String,
    val email: String,
    val phone: String,
    val rating: Double,
    val reviewCount: Int,
    val level: String,
    val memberSince: String,
    val sharedTrips: Int,
    val punctuality: Int,
    val co2SavedKg: Int
) {
    val firstName: String get() = name.substringBefore(' ')
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

data class VehicleInfo(
    val model: String,
    val year: Int,
    val plate: String,
    val colorIndex: Int,
    val vin: String,
    val seats: Int,
    val luggage: String,
    val amenities: Set<String>
) {
    companion object {
        val colorNames = listOf("Azul Marino Metalizado", "Gris Plata", "Blanco", "Negro", "Rojo")
    }

    val colorName: String get() = colorNames[colorIndex]
}

enum class DocumentStatus(val label: String) {
    APPROVED("Aprobada"),
    VALID("Vigente"),
    EXPIRING("Vence pronto"),
    VERIFIED("Cotejado")
}

data class VerificationDocument(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: DocumentStatus,
    val primaryLabel: String,
    val primaryValue: String,
    val secondaryLabel: String? = null,
    val secondaryValue: String? = null
)
