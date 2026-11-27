package com.onerouteandroid.oneroute.core.navigation

sealed interface Route {
    data object Splash : Route
    data object Onboarding : Route
    data object Login : Route
    data object Register : Route
    data object ForgotPassword : Route
    data class OtpNewPassword(val contact: String) : Route

    data object Home : Route
    data object Search : Route
    data class MyTrips(val initialRole: Int = 0) : Route
    data object Profile : Route

    data class TripDetail(val tripId: String) : Route

    data object CreateTrip : Route
    data object MapPointPicker : Route
    data object PublishPreview : Route
    data class PublishConfirmation(val tripCode: String) : Route

    data class MeetingPoint(val tripId: String) : Route
    data class ActiveTrip(val tripId: String) : Route
    data class LiveNavigation(val tripId: String) : Route
    data class TripSummary(val tripId: String) : Route
    data class RateUser(val tripId: String, val targetName: String, val vehicleSummary: String) : Route
    data class CancelTrip(val tripId: String) : Route
    data object TripHistory : Route

    data object MessagesInbox : Route
    data class RideChat(val conversationId: String) : Route
    data object Notifications : Route

    data object WalletPayments : Route
    data object VehicleRegistration : Route
    data object DocumentVerification : Route
    data object EmergencyContacts : Route

    data object HelpCenter : Route
    data class FaqArticleDetail(val articleId: String) : Route
    data class SupportTicketChat(val ticketId: String) : Route
    data class SafetyReports(val tripId: String? = null, val initialTab: Int = 0) : Route
}
