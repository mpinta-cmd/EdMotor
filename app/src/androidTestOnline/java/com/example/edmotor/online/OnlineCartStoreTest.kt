package com.example.edmotor.online

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineCartStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val project = "test_${UUID.randomUUID()}"
    private val otherProject = "test_${UUID.randomUUID()}"

    @After
    fun removeTestPreferences() {
        context.deleteSharedPreferences("edmotor_online_cart_$project")
        context.deleteSharedPreferences("edmotor_online_cart_$otherProject")
    }

    @Test
    fun retryUsesTheSavedRequestAcrossStoreInstancesAndKeepsAccountsSeparate() {
        val items = linkedMapOf(9L to 3, 2L to 1)
        val total = 75_000L
        val beforeRestart = OnlineCartStore(context, project).prepareOrder(17L, items, total)
        val reopened = OnlineCartStore(context, project)

        assertEquals(beforeRestart, reopened.pendingOrder(17L))
        assertEquals(beforeRestart, reopened.prepareOrder(17L, linkedMapOf(2L to 1, 9L to 3), total))
        assertThrows(OnlineInputError::class.java) { reopened.prepareOrder(17L, items, 80_000L) }
        assertNull(reopened.pendingOrder(18L))
        assertNotEquals(beforeRestart.requestId, reopened.prepareOrder(18L, items, total).requestId)
        assertNull(OnlineCartStore(context, otherProject).pendingOrder(17L))
    }

    @Test
    fun pendingRequestBlocksAnEditedOrderAndRejectionKeepsCart() {
        val store = OnlineCartStore(context, project)
        val items = mapOf(9L to 3)
        store.save(17L, items)
        val first = store.prepareOrder(17L, items)

        assertThrows(OnlineInputError::class.java) { store.prepareOrder(17L, mapOf(9L to 4)) }
        assertEquals(first, store.pendingOrder(17L))
        store.discardOrder(17L, first)

        assertNull(store.pendingOrder(17L))
        assertEquals(items, store.read(17L))
        assertNotEquals(first.requestId, store.prepareOrder(17L, items).requestId)
    }

    @Test
    fun acknowledgmentClearsOnlySubmittedQuantitiesAndThePendingRequest() {
        val store = OnlineCartStore(context, project)
        val submitted = mapOf(9L to 3, 2L to 1)
        store.save(17L, submitted)
        val request = store.prepareOrder(17L, submitted)
        store.save(null, mapOf(9L to 2, 7L to 1))

        val reopened = OnlineCartStore(context, project)
        reopened.moveGuestCartTo(17L)
        assertEquals(request, reopened.pendingOrder(17L))
        val remaining = mapOf(9L to 2, 7L to 1)
        assertEquals(remaining, reopened.acknowledgeOrder(17L, request))

        val afterAcknowledgment = OnlineCartStore(context, project)
        assertNull(afterAcknowledgment.pendingOrder(17L))
        assertEquals(remaining, afterAcknowledgment.read(17L))
        assertEquals(remaining, afterAcknowledgment.acknowledgeOrder(17L, request))
    }
}
