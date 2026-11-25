package com.onerouteandroid.oneroute.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.onerouteandroid.oneroute.core.components.SkeletonContentHost
import com.onerouteandroid.oneroute.core.components.SkeletonVariant
import com.onerouteandroid.oneroute.feature.auth.ForgotPasswordScreen
import com.onerouteandroid.oneroute.feature.auth.LoginScreen
import com.onerouteandroid.oneroute.feature.auth.OnboardingScreen
import com.onerouteandroid.oneroute.feature.auth.OtpNewPasswordScreen
import com.onerouteandroid.oneroute.feature.auth.RegisterScreen
import com.onerouteandroid.oneroute.feature.auth.SplashScreen
import com.onerouteandroid.oneroute.feature.home.HomeScreen
import com.onerouteandroid.oneroute.feature.home.SearchTripsScreen
import com.onerouteandroid.oneroute.feature.home.TripDetailScreen
import com.onerouteandroid.oneroute.feature.messaging.MessagesInboxScreen
import com.onerouteandroid.oneroute.feature.messaging.NotificationsScreen
import com.onerouteandroid.oneroute.feature.messaging.RideChatScreen
import com.onerouteandroid.oneroute.feature.profile.DocumentVerificationScreen
import com.onerouteandroid.oneroute.feature.profile.ProfileScreen
import com.onerouteandroid.oneroute.feature.profile.VehicleRegistrationScreen
import com.onerouteandroid.oneroute.feature.profile.WalletPaymentsScreen
import com.onerouteandroid.oneroute.feature.publish.CreateTripScreen
import com.onerouteandroid.oneroute.feature.publish.MapPointPickerScreen
import com.onerouteandroid.oneroute.feature.publish.PublishConfirmationScreen
import com.onerouteandroid.oneroute.feature.publish.PublishPreviewScreen
import com.onerouteandroid.oneroute.feature.support.FaqArticleDetailScreen
import com.onerouteandroid.oneroute.feature.support.HelpCenterScreen
import com.onerouteandroid.oneroute.feature.support.SupportTicketChatScreen
import com.onerouteandroid.oneroute.feature.trips.ActiveTripScreen
import com.onerouteandroid.oneroute.feature.trips.CancelTripScreen
import com.onerouteandroid.oneroute.feature.trips.LiveNavigationScreen
import com.onerouteandroid.oneroute.feature.trips.MeetingPointScreen
import com.onerouteandroid.oneroute.feature.trips.MyTripsScreen
import com.onerouteandroid.oneroute.feature.trips.RateUserScreen
import com.onerouteandroid.oneroute.feature.trips.TripHistoryScreen
import com.onerouteandroid.oneroute.feature.trips.TripSummaryScreen

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

        Route.HelpCenter -> HelpCenterScreen(state, nav)
        is Route.FaqArticleDetail -> FaqArticleDetailScreen(route.articleId, state, nav)
        is Route.SupportTicketChat -> SupportTicketChatScreen(route.ticketId, state, nav)
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
    Route.DocumentVerification, Route.HelpCenter -> SkeletonVariant.LIST
    is Route.RideChat, is Route.SupportTicketChat -> SkeletonVariant.CHAT
}
