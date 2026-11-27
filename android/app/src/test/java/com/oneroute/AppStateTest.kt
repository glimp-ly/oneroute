package com.oneroute

import com.oneroute.core.components.MainTab
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState
import com.oneroute.core.navigation.Route
import com.oneroute.data.model.HistoryStatus
import com.oneroute.data.model.RideRequestStatus
import com.oneroute.data.model.TripRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {
    private val state = AppState()

    @Test
    fun requestAcceptAndConfirm_followsTwoStageHoldFlow() {
        val beforeScheduled = state.scheduledTrips.size
        val seatsBefore = state.availableTrip("at1")!!.seatsLeft

        val req = state.requestTrip("at1", seats = 1)
        assertNotNull(req)
        assertEquals(RideRequestStatus.PENDIENTE, req!!.status)
        // Pendiente aún no consume asientos
        assertEquals(seatsBefore, state.availableTrip("at1")!!.seatsLeft)

        assertTrue(state.acceptRideRequest(req.id))
        assertEquals(RideRequestStatus.ACEPTADA_HOLD, state.rideRequests.first { it.id == req.id }.status)

        val booked = state.confirmHeldRequest(req.id)
        assertNotNull(booked)
        assertEquals(beforeScheduled + 1, state.scheduledTrips.size)
        assertEquals(seatsBefore - 1, state.availableTrip("at1")!!.seatsLeft)
        assertEquals(TripRole.PASSENGER, booked!!.role)
    }

    @Test
    fun autoAcceptTrip_startsDirectlyInHoldAndCancelsOtherRequestsOnConfirm() {
        val manualReq = state.requestTrip("at1", seats = 1)!!
        val autoReq = state.requestTrip("at2", seats = 1)!!

        assertEquals(RideRequestStatus.ACEPTADA_HOLD, autoReq.status)
        assertTrue(autoReq.autoAccepted)

        assertNotNull(state.confirmHeldRequest(autoReq.id))
        assertEquals(RideRequestStatus.CANCELADA, state.rideRequests.first { it.id == manualReq.id }.status)
    }

    @Test
    fun voluntaryCancelBeforeResponse_doesNotConsumeAttempt_andMaxThreeAttemptsEnforced() {
        val r1 = state.requestTrip("at1")!!
        assertTrue(state.cancelPendingRequestByPassenger(r1.id))
        assertEquals(0, state.attemptsUsedForTrip("at1"))

        repeat(3) {
            val r = state.requestTrip("at1")!!
            assertTrue(state.rejectRideRequest(r.id, "Sin cupo"))
        }
        assertEquals(3, state.attemptsUsedForTrip("at1"))
        assertNull(state.requestTrip("at1"))
    }

    @Test
    fun publishDraftTrip_addsDriverTripWithConductorPriceAndResetsDraft() {
        state.updateDraft { it.copy(seats = 2, pricePerSeat = 35) }

        val published = state.publishDraftTrip()

        assertNotNull(published)
        assertEquals(TripRole.DRIVER, published!!.role)
        assertEquals(70, published.price)
        assertTrue(state.scheduledTrips.any { it.id == published.id })
        assertEquals(3, state.tripDraft.seats)
    }

    @Test
    fun threeWallets_withdrawFromEarnings_rechargeCommissionAndRefundToPassenger() {
        val earningsBefore = state.earningsWalletBalance
        val passengerBefore = state.passengerWalletBalance
        val commissionBefore = state.commissionWalletBalance

        assertFalse(state.withdrawToBank(0.0))
        assertFalse(state.withdrawToBank(earningsBefore + 1))
        assertTrue(state.withdrawToBank(100.0))
        assertEquals(earningsBefore - 100, state.earningsWalletBalance, 0.001)

        assertTrue(state.rechargeCommissionWallet(25.0))
        assertEquals(commissionBefore + 25.0, state.commissionWalletBalance, 0.001)

        state.cancelScheduledTrip("st1", "Cambio de planes")
        assertNull(state.scheduledTrip("st1"))
        assertEquals(HistoryStatus.CANCELLED, state.historyTrips.first().status)
        assertEquals(passengerBefore + 24.0, state.passengerWalletBalance, 0.001)
    }

    @Test
    fun modifyTrip_enforcesSinglePickupDestinationChange_and24hCreditOnSeatReduction() {
        assertTrue(state.modifyTripPickup("st1", "Parque Kennedy cdra. 2"))
        assertFalse(state.modifyTripPickup("st1", "Segundo intento"))

        assertTrue(state.modifyTripDestination("st1", "Av. Rivera Navarrete 120", -4))
        assertFalse(state.modifyTripDestination("st1", "Segundo destino", -2))

        val r = state.requestTrip("at1", seats = 2)!!
        state.acceptRideRequest(r.id)
        val booked = state.confirmHeldRequest(r.id)!!
        assertTrue(state.modifyTripSeats(booked.id, 1, paidAlready = true))
        assertTrue(state.pendingCredits.any { it.tripId == booked.id && it.availableInHours == 24 })
    }

    @Test
    fun earlyFinish_noShow_trackingLink_andEmergencyContacts() {
        val link = state.generateTrackingLink("st1")
        assertTrue(link.startsWith("https://"))
        state.revokeTrackingLink("st1")
        assertNull(state.scheduledTrip("st1")!!.trackingShareUrl)

        val passengerBefore = state.passengerWalletBalance
        assertTrue(state.finishTripEarly("st1", "Av. Javier Prado 400", 16))
        assertEquals(passengerBefore + 8.0, state.passengerWalletBalance, 0.001)

        assertTrue(state.declareNoShow("st2", "No llegó en 5 min"))
        assertNull(state.scheduledTrip("st2"))

        val contactsBefore = state.emergencyContacts.size
        assertTrue(state.addEmergencyContact("Ana Torres", "Amiga", "+51 912 345 678", true))
        assertEquals(contactsBefore + 1, state.emergencyContacts.size)
        assertEquals("Ana Torres", state.emergencyContacts.first { it.isPrimary }.name)
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
