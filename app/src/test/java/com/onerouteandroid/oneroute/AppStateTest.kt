package com.onerouteandroid.oneroute

import com.onerouteandroid.oneroute.core.components.MainTab
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.data.model.HistoryStatus
import com.onerouteandroid.oneroute.data.model.TripRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {
    private val state = AppState()

    @Test
    fun bookTrip_addsScheduledTripAndConsumesSeat() {
        val before = state.scheduledTrips.size
        val seats = state.availableTrip("at2")!!.seatsLeft

        val booked = state.bookTrip("at2")

        assertNotNull(booked)
        assertEquals(before + 1, state.scheduledTrips.size)
        assertEquals(seats - 1, state.availableTrip("at2")!!.seatsLeft)
        assertEquals(TripRole.PASSENGER, booked!!.role)
    }

    @Test
    fun bookTrip_withoutSeatsReturnsNull() {
        assertNotNull(state.bookTrip("at3"))
        assertNull(state.bookTrip("at3"))
        assertNull(state.bookTrip("unknown"))
    }

    @Test
    fun publishDraftTrip_addsDriverTripAndResetsDraft() {
        state.updateDraft { it.copy(seats = 2, pricePerSeat = 70) }

        val published = state.publishDraftTrip()

        assertEquals(TripRole.DRIVER, published.role)
        assertEquals(140, published.price)
        assertTrue(state.scheduledTrips.any { it.id == published.id })
        assertEquals(3, state.tripDraft.seats)
    }

    @Test
    fun cancelScheduledTrip_movesToHistoryAndRefundsPassenger() {
        val balance = state.walletBalance

        state.cancelScheduledTrip("st1", "Cambio de planes")

        assertNull(state.scheduledTrip("st1"))
        assertEquals(HistoryStatus.CANCELLED, state.historyTrips.first().status)
        assertEquals(balance + 65, state.walletBalance, 0.001)
    }

    @Test
    fun completeActiveTrip_marksHistoryAsRatable() {
        state.completeActiveTrip("st1")

        val done = state.historyTrips.first { it.id == "ht-done-st1" }
        assertTrue(done.canRate)

        state.submitRating("st1", 5, emptySet(), 0, "", false)
        assertFalse(state.historyTrips.first { it.id == "ht-done-st1" }.canRate)
    }

    @Test
    fun sendRideMessage_appendsMessageAndIgnoresBlank() {
        val size = state.conversations.first { it.id == "c1" }.messages.size

        state.sendRideMessage("c1", "   ")
        state.sendRideMessage("c1", "Llego en 2 min")

        val conversation = state.conversations.first { it.id == "c1" }
        assertEquals(size + 1, conversation.messages.size)
        assertEquals("Llego en 2 min", conversation.preview)
        assertEquals(0, conversation.unread)
    }

    @Test
    fun markAllNotificationsRead_clearsUnreadCount() {
        assertTrue(state.unreadNotifications > 0)
        state.markAllNotificationsRead()
        assertEquals(0, state.unreadNotifications)
    }

    @Test
    fun withdrawToBank_validatesAmount() {
        val balance = state.walletBalance

        assertFalse(state.withdrawToBank(0.0))
        assertFalse(state.withdrawToBank(balance + 1))
        assertTrue(state.withdrawToBank(100.0))
        assertEquals(balance - 100, state.walletBalance, 0.001)
    }
}

class AppNavigatorTest {
    @Test
    fun navigateAndBack_trackDirection() {
        val nav = AppNavigator(Route.Home)

        nav.navigate(Route.TripDetail("at1"))
        assertEquals(Route.TripDetail("at1"), nav.current)
        assertTrue(nav.lastWasForward)

        assertTrue(nav.back())
        assertEquals(Route.Home, nav.current)
        assertFalse(nav.lastWasForward)
        assertFalse(nav.back())
    }

    @Test
    fun selectTab_keepsHomeAsSingleBackTarget() {
        val nav = AppNavigator(Route.Home)

        nav.selectTab(MainTab.BUSCAR)
        nav.selectTab(MainTab.PERFIL)

        assertEquals(Route.Profile, nav.current)
        assertEquals(2, nav.depth)
        nav.back()
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun selectTab_onCurrentTabDoesNotChangeEntry() {
        val nav = AppNavigator(Route.Home)
        val entry = nav.entryId

        nav.selectTab(MainTab.INICIO)

        assertEquals(entry, nav.entryId)
    }

    @Test
    fun resetTo_clearsHistory() {
        val nav = AppNavigator(Route.Splash)
        nav.navigate(Route.Onboarding)
        nav.navigate(Route.Login)

        nav.resetTo(Route.Home)

        assertEquals(1, nav.depth)
        assertFalse(nav.canGoBack)
    }
}
