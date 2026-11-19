package com.example.edmotor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentServiceType
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.Vehicle
import com.example.edmotor.ui.screens.AppointmentsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

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
                    onCancel = {},
                    onReschedule = { _, _ -> },
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

    @Test
    fun workshopCanAdjustTheExactTimeOfAPendingRequest() {
        val appointment = sampleAppointment(AppointmentStatus.PENDING)
        var changed: Pair<Long, Long>? = null
        var confirmed: Long? = null

        compose.setContent {
            MaterialTheme {
                AppointmentsScreen(
                    isWorkshop = true,
                    appointments = listOf(appointment),
                    syncState = AppointmentSyncState.UP_TO_DATE,
                    vehicles = emptyList(),
                    isProcessing = false,
                    message = null,
                    isError = false,
                    onCreateAppointment = { _, _, _ -> },
                    onAccept = { confirmed = it },
                    onReject = {},
                    onCancel = {},
                    onReschedule = { id, date -> changed = id to date },
                    onClearMessage = {},
                    onRefresh = {}
                )
            }
        }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText("Ajustar fecha y hora").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Minutos")).performTextReplacement("45")
        compose.onNodeWithText("Guardar horario").assertIsEnabled().performClick()
        compose.onNodeWithText("Confirmar").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(appointment.id to (appointment.scheduledAtMillis + 45 * 60_000L), changed)
            assertEquals(appointment.id, confirmed)
        }
    }

    @Test
    fun clientCanOpenAgendaRescheduleAndCancel() {
        val original = sampleAppointment(AppointmentStatus.CONFIRMED)
        var appointment by mutableStateOf(original)

        compose.setContent {
            MaterialTheme {
                AppointmentsScreen(
                    isWorkshop = false,
                    appointments = listOf(appointment),
                    syncState = AppointmentSyncState.UP_TO_DATE,
                    vehicles = emptyList(),
                    isProcessing = false,
                    message = null,
                    isError = false,
                    onCreateAppointment = { _, _, _ -> },
                    onAccept = {},
                    onReject = {},
                    onCancel = { id ->
                        assertEquals(original.id, id)
                        appointment = appointment.copy(status = AppointmentStatus.CANCELLED)
                    },
                    onReschedule = { id, date ->
                        assertEquals(original.id, id)
                        appointment = appointment.copy(scheduledAtMillis = date, status = AppointmentStatus.PENDING)
                    },
                    onClearMessage = {},
                    onRefresh = {}
                )
            }
        }

        compose.onNodeWithText("Ver citas agendadas").performClick()
        compose.onNodeWithText("Citas agendadas").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText("Reagendar").performScrollTo().performClick()
        compose.onNodeWithText("Hora: 10:00").performClick()
        compose.onNodeWithText("08:00").performClick()
        compose.onNodeWithText("Guardar horario").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(original.scheduledAtMillis - 2 * 60 * 60_000L, appointment.scheduledAtMillis)
            assertEquals(AppointmentStatus.PENDING, appointment.status)
        }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Volver a solicitar cita").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        compose.onNodeWithText("Cancelar cita").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar cancelación").assertIsEnabled().performClick()
        compose.onNodeWithText("Cancelada").assertExists()
        compose.onNodeWithText("Reagendar").assertDoesNotExist()
    }

    private fun sampleAppointment(status: String): Appointment {
        val scheduledAtMillis = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        return Appointment(
            id = 41L,
            clientAuthId = "cliente41",
            clientName = "Andrea",
            clientUsername = "andrea",
            clientPhone = null,
            vehicleBrand = "Marca",
            vehicleModel = "Modelo",
            vehicleYear = 2021,
            vehiclePlate = "",
            scheduledAtMillis = scheduledAtMillis,
            description = AppointmentServiceType.options.first(),
            status = status,
            createdAtMillis = now,
            updatedAtMillis = now
        )
    }
}
