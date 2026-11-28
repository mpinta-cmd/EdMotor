package com.example.edmotor.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineStateTest {
    @Test
    fun anInterruptedReadRetainsDataAndOnlyMarksItsOwnResourceUnavailable() {
        val ready = OnlineSyncStatus().result(success = true)
        val firstFailure = ready.result(success = false)
        assertTrue(firstFailure.loaded)
        assertTrue(firstFailure.retrying)
        assertFalse(firstFailure.unavailable)

        val state = OnlineState(sync = mapOf(
            OnlineResource.CATALOG to ready,
            OnlineResource.APPOINTMENTS to firstFailure.result(success = false)
        ))
        assertTrue(state.syncStatus(OnlineResource.APPOINTMENTS).unavailable)
        assertFalse(state.syncStatus(OnlineResource.CATALOG).unavailable)
        assertEquals(ready, state.syncStatus(OnlineResource.APPOINTMENTS).result(success = true))
    }

    @Test
    fun anAlreadyRecoveredOrderCannotBeResubmittedFromTheOldCheckoutButton() {
        val items = mapOf(12L to 1)
        val pending = PendingOnlineOrder("6c50ca55-f9e0-4b32-9054-053e008d126c", items)
        val before = OnlineState(cart = mapOf(12L to 2), pendingOrder = pending)
        assertEquals(items, before.checkoutItems(items, pending.requestId))

        val after = before.copy(cart = items, pendingOrder = null)
        assertThrows(OnlineInputError::class.java) { after.checkoutItems(items, pending.requestId) }
        assertEquals(items, after.checkoutItems(items, null))
    }
}
