package com.example.edmotor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.edmotor.model.AppointmentServiceType
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.Vehicle
import com.example.edmotor.ui.screens.AppointmentsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppointmentRequestScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun clientCanSubmitWhileAgendaReadIsUnavailable() {
        val vehicle = Vehicle(1, "Marca", "Modelo", 2021, "Motor", ownerUserId = 1)
        val service = AppointmentServiceType.options.first()
        var submitted: Triple<Vehicle, Long, String>? = null

        compose.setContent {
            MaterialTheme {
                AppointmentsScreen(
                    isWorkshop = false,
                    appointments = emptyList(),
                    syncState = AppointmentSyncState.UNAVAILABLE,
                    vehicles = listOf(vehicle),
                    isProcessing = false,
                    message = null,
                    isError = false,
                    onCreateAppointment = { selected, start, type -> submitted = Triple(selected, start, type) },
                    onAccept = {},
                    onReject = {},
                    onClearMessage = {},
                    onRefresh = {}
                )
            }
        }

        compose.onNodeWithText("Seleccionar tipo de servicio").performScrollTo().performClick()
        compose.onNodeWithText(service).performClick()
        compose.onNodeWithText("Solicitar cita").performScrollTo().assertIsEnabled().performClick()

        compose.runOnIdle {
            val (selected, start, type) = requireNotNull(submitted)
            assertEquals(vehicle, selected)
            assertEquals(service, type)
            assertTrue(start > System.currentTimeMillis())
        }
    }
}
