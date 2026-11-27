package com.oneroute.data.model

enum class ChatMessageType { TEXT, LOCATION_PIN, VOICE_NOTE, TRIP_SUMMARY }

data class ChatMessage(
    val id: String,
    val fromMe: Boolean,
    val text: String,
    val time: String,
    val type: ChatMessageType = ChatMessageType.TEXT,
    val extra: String? = null
)

data class Conversation(
    val id: String,
    val name: String,
    val roleLabel: String,
    val rating: Double,
    val preview: String,
    val time: String,
    val unread: Int,
    val routeLabel: String,
    val active: Boolean,
    val finished: Boolean,
    val messages: List<ChatMessage>
)

enum class NotificationCategory(val label: String) {
    TRIPS("Viajes y Rutas"),
    PAYMENTS("Pagos"),
    SYSTEM("Sistema")
}

enum class NotificationKind { TRIP, PAYMENT, RATING, DOCUMENT, ALERT, ACHIEVEMENT }

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val time: String,
    val group: String,
    val category: NotificationCategory,
    val kind: NotificationKind,
    val read: Boolean,
    val actionLabel: String? = null,
    val secondaryActionLabel: String? = null
)
