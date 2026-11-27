package com.oneroute.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.oneroute.core.components.SkeletonContentHost
import com.oneroute.core.components.SkeletonVariant
import com.oneroute.feature.auth.ForgotPasswordScreen
import com.oneroute.feature.auth.LoginScreen
import com.oneroute.feature.auth.OnboardingScreen
import com.oneroute.feature.auth.OtpNewPasswordScreen
import com.oneroute.feature.auth.RegisterScreen
import com.oneroute.feature.auth.SplashScreen
import com.oneroute.feature.home.HomeScreen
import com.oneroute.feature.home.SearchTripsScreen
import com.oneroute.feature.home.TripDetailScreen
import com.oneroute.feature.messaging.MessagesInboxScreen
import com.oneroute.feature.messaging.NotificationsScreen
import com.oneroute.feature.messaging.RideChatScreen
import com.oneroute.feature.profile.DocumentVerificationScreen
import com.oneroute.feature.profile.ProfileScreen
import com.oneroute.feature.profile.VehicleRegistrationScreen
import com.oneroute.feature.profile.WalletPaymentsScreen
import com.oneroute.feature.publish.CreateTripScreen
import com.oneroute.feature.publish.MapPointPickerScreen
import com.oneroute.feature.publish.PublishConfirmationScreen
import com.oneroute.feature.publish.PublishPreviewScreen
import com.oneroute.feature.support.FaqArticleDetailScreen
import com.oneroute.feature.support.HelpCenterScreen
import com.oneroute.feature.support.EmergencyContactsScreen
import com.oneroute.feature.support.SafetyReportsScreen
import com.oneroute.feature.support.SupportTicketChatScreen
import com.oneroute.feature.trips.ActiveTripScreen
import com.oneroute.feature.trips.CancelTripScreen
import com.oneroute.feature.trips.LiveNavigationScreen
import com.oneroute.feature.trips.MeetingPointScreen
import com.oneroute.feature.trips.MyTripsScreen
import com.oneroute.feature.trips.RateUserScreen
import com.oneroute.feature.trips.TripHistoryScreen
import com.oneroute.feature.trips.TripSummaryScreen

private const val HOME_LOADING_MS = 900L
private const val DEFAULT_LOADING_MS = 650L

@Composable
fun AppNavHost(state: AppState, nav: AppNavigator) {
    BackHandler(enabled = nav.canGoBack) { nav.back() }

    val route = nav.current
    val variant = route.skeletonVariant()
    val screen: @Composable () -> Unit = { RouteContent(route, state, nav) }

    if (variant == null) {
        screen()
    } else {
        SkeletonContentHost(
            variant = variant,
            contentKey = nav.entryId,
            shouldAnimate = nav.lastWasForward,
            durationMs = if (route == Route.Home) HOME_LOADING_MS else DEFAULT_LOADING_MS,
            content = screen
        )
    }
}

@Composable
private fun RouteContent(route: Route, state: AppState, nav: AppNavigator) {
    when (route) {
        Route.Splash -> SplashScreen(nav)
        Route.Onboarding -> OnboardingScreen(nav)
        Route.Login -> LoginScreen(state, nav)
        Route.Register -> RegisterScreen(state, nav)
        Route.ForgotPassword -> ForgotPasswordScreen(nav)
        is Route.OtpNewPassword -> OtpNewPasswordScreen(route.contact, nav)

        Route.Home -> HomeScreen(state, nav)
        Route.Search -> SearchTripsScreen(state, nav)
        is Route.MyTrips -> MyTripsScreen(route.initialRole, state, nav)
        Route.Profile -> ProfileScreen(state, nav)

        is Route.TripDetail -> TripDetailScreen(route.tripId, state, nav)

        Route.CreateTrip -> CreateTripScreen(state, nav)
        Route.MapPointPicker -> MapPointPickerScreen(state, nav)
        Route.PublishPreview -> PublishPreviewScreen(state, nav)
        is Route.PublishConfirmation -> PublishConfirmationScreen(route.tripCode, state, nav)

        is Route.MeetingPoint -> MeetingPointScreen(route.tripId, state, nav)
        is Route.ActiveTrip -> ActiveTripScreen(route.tripId, state, nav)
        is Route.LiveNavigation -> LiveNavigationScreen(route.tripId, state, nav)
        is Route.TripSummary -> TripSummaryScreen(route.tripId, state, nav)
        is Route.RateUser -> RateUserScreen(route, state, nav)
        is Route.CancelTrip -> CancelTripScreen(route.tripId, state, nav)
        Route.TripHistory -> TripHistoryScreen(state, nav)

        Route.MessagesInbox -> MessagesInboxScreen(state, nav)
        is Route.RideChat -> RideChatScreen(route.conversationId, state, nav)
        Route.Notifications -> NotificationsScreen(state, nav)

        Route.WalletPayments -> WalletPaymentsScreen(state, nav)
        Route.VehicleRegistration -> VehicleRegistrationScreen(state, nav)
        Route.DocumentVerification -> DocumentVerificationScreen(state, nav)
        Route.EmergencyContacts -> EmergencyContactsScreen(state, nav)

        Route.HelpCenter -> HelpCenterScreen(state, nav)
        is Route.FaqArticleDetail -> FaqArticleDetailScreen(route.articleId, state, nav)
        is Route.SupportTicketChat -> SupportTicketChatScreen(route.ticketId, state, nav)
        is Route.SafetyReports -> SafetyReportsScreen(route.tripId, route.initialTab, state, nav)
    }
}

private fun Route.skeletonVariant(): SkeletonVariant? = when (this) {
    Route.Splash, Route.Onboarding -> null
    Route.Login, Route.Register, Route.ForgotPassword, is Route.OtpNewPassword,
    Route.CreateTrip, is Route.RateUser, is Route.CancelTrip,
    Route.VehicleRegistration, is Route.FaqArticleDetail -> SkeletonVariant.FORM
    Route.Home -> SkeletonVariant.HOME
    Route.Search -> SkeletonVariant.SEARCH
    is Route.MyTrips -> SkeletonVariant.TRIPS
    Route.Profile -> SkeletonVariant.PROFILE
    is Route.TripDetail, Route.MapPointPicker, Route.PublishPreview, is Route.PublishConfirmation,
    is Route.MeetingPoint, is Route.ActiveTrip, is Route.LiveNavigation, is Route.TripSummary,
    Route.WalletPayments -> SkeletonVariant.DETAIL
    Route.TripHistory, Route.MessagesInbox, Route.Notifications,
    Route.DocumentVerification, Route.EmergencyContacts, Route.HelpCenter,
    is Route.SafetyReports -> SkeletonVariant.LIST
    is Route.RideChat, is Route.SupportTicketChat -> SkeletonVariant.CHAT
}
