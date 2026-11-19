package com.example.edmotor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.edmotor.data.local.AppointmentWorkEntity
import com.example.edmotor.data.local.AppointmentWorkWithMaintenance
import com.example.edmotor.data.local.MaintenancePartEntity
import com.example.edmotor.data.local.MaintenanceRecordEntity
import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentServiceType
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.Vehicle
import com.example.edmotor.model.appointmentDateUtcMillis
import com.example.edmotor.ui.screens.AppointmentsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    fun workshopCanAdjustARequestToAnExistingSlotAndConfirmBoth() {
        val first = sampleAppointment(AppointmentStatus.PENDING)
        val halfHour = 30 * 60_000L
        val second = first.copy(id = 42L, clientName = "Gabriel", scheduledAtMillis = first.scheduledAtMillis + halfHour)
        var appointments by mutableStateOf(listOf(first, second))

        compose.setContent {
            MaterialTheme {
                AppointmentsScreen(
                    isWorkshop = true,
                    appointments = appointments,
                    syncState = AppointmentSyncState.UP_TO_DATE,
                    vehicles = emptyList(),
                    isProcessing = false,
                    message = null,
                    isError = false,
                    onCreateAppointment = { _, _, _ -> },
                    onAccept = { id ->
                        appointments = appointments.map {
                            if (it.id == id) it.copy(status = AppointmentStatus.CONFIRMED) else it
                        }
                    },
                    onReject = {},
                    onCancel = {},
                    onReschedule = { id, date ->
                        appointments = appointments.map {
                            if (it.id == id) it.copy(scheduledAtMillis = date) else it
                        }
                    },
                    onClearMessage = {},
                    onRefresh = {}
                )
            }
        }

        compose.onNodeWithText("Solicitudes").assertIsSelected()
        appointmentList().performScrollToKey(first.id)
        compose.onAllNodesWithText("Ajustar fecha y hora").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Hora: 10:00").performClick()
        compose.onNodeWithText("10:30").performClick()
        compose.onNodeWithText("Guardar horario").assertIsEnabled().performClick()

        listOf(first, second).forEach { appointment ->
            appointmentList().performScrollToKey(appointment.id)
            compose.onAllNodesWithText("Confirmar").onFirst().performScrollTo().performClick()
        }

        compose.runOnIdle {
            assertEquals(listOf(first.id, second.id), appointments.map { it.id })
            assertTrue(appointments.all { it.status == AppointmentStatus.CONFIRMED })
            assertTrue(appointments.all { it.scheduledAtMillis == second.scheduledAtMillis })
        }
    }

    @Test
    fun workshopCalendarShowsBothSameTimeAppointmentsAndLetsItRescheduleFromTheSelectedDay() {
        val day = Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 8)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val first = sampleAppointment(AppointmentStatus.CONFIRMED).copy(scheduledAtMillis = day.timeInMillis)
        val second = first.copy(id = 42L, clientName = "Gabriel")
        day.add(Calendar.DAY_OF_MONTH, 1)
        val followingDay = first.copy(id = 43L, clientName = "Sofía", scheduledAtMillis = day.timeInMillis)
        var changed: Pair<Long, Long>? = null

        compose.setContent {
            MaterialTheme {
                AppointmentsScreen(
                    isWorkshop = true,
                    appointments = listOf(first, second, followingDay),
                    syncState = AppointmentSyncState.UP_TO_DATE,
                    vehicles = emptyList(),
                    isProcessing = false,
                    message = null,
                    isError = false,
                    onCreateAppointment = { _, _, _ -> },
                    onAccept = {},
                    onReject = {},
                    onCancel = {},
                    onReschedule = { id, date -> changed = id to date },
                    onClearMessage = {},
                    onRefresh = {}
                )
            }
        }

        compose.onNodeWithText("Calendario").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Mes siguiente").performClick()
        selectDay(first.scheduledAtMillis, "2 citas")
        appointmentList().performScrollToKey(first.id)
        compose.onNodeWithText(first.clientName, substring = true).assertExists()
        appointmentList().performScrollToKey(second.id)
        compose.onNodeWithText(second.clientName, substring = true).assertExists()
        compose.onAllNodesWithText("Reagendar").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Hora: 10:00").performClick()
        compose.onNodeWithText("08:00").performClick()
        compose.onNodeWithText("Guardar horario").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(second.id to (second.scheduledAtMillis - 2 * 60 * 60_000L), changed)
        }

        appointmentList().performScrollToKey("calendar")
        selectDay(followingDay.scheduledAtMillis, "1 cita")
        appointmentList().performScrollToKey(followingDay.id)
        compose.onNodeWithText(followingDay.clientName, substring = true).assertExists()
        compose.onNodeWithText(first.clientName, substring = true).assertDoesNotExist()
        compose.onNodeWithText(second.clientName, substring = true).assertDoesNotExist()
    }

    @Test
    fun clientStartsInCalendarAndCanRescheduleAndCancel() {
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
                    onCancel = { requested ->
                        assertEquals(appointment, requested)
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

        compose.onNodeWithText("Calendario").assertIsSelected()
        compose.onNodeWithText("Solicitar").assertDoesNotExist()
        compose.onNodeWithText("Próxima cita").assertExists()
        assertAutomaticDay(original.scheduledAtMillis)
        appointmentList().performScrollToKey(original.id)
        compose.onNodeWithText("Reagendar").performScrollTo().performClick()
        compose.onNodeWithText("Hora: 10:00").performClick()
        compose.onNodeWithText("08:00").performClick()
        compose.onNodeWithText("Guardar horario").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(original.scheduledAtMillis - 2 * 60 * 60_000L, appointment.scheduledAtMillis)
            assertEquals(AppointmentStatus.PENDING, appointment.status)
        }

        compose.onNodeWithText("Solicitar").assertDoesNotExist()
        compose.onNodeWithText("Horario: ${formatDateTime(original.scheduledAtMillis - 2 * 60 * 60_000L)}").assertExists()
        appointmentList().performScrollToKey(original.id)
        compose.onNodeWithText("Cancelar cita").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar cancelación").assertIsEnabled().performClick()
        compose.onNodeWithText("Solicitar").assertIsSelected()
        compose.onNodeWithText("Todas las citas").performClick().assertIsSelected()
        compose.onNodeWithText("Todavía no hay citas registradas.").assertExists()
        compose.onNodeWithText("Cancelada").assertDoesNotExist()
        compose.onNodeWithText("Reagendar").assertDoesNotExist()
    }

    @Test
    fun bookingFormReturnsOnlyWhenAllActiveAppointmentsReachTerminalStatus() {
        val earlier = sampleAppointment(AppointmentStatus.PENDING).copy(
            scheduledAtMillis = System.currentTimeMillis() - 24 * 60 * 60_000L
        )
        val later = sampleAppointment(AppointmentStatus.CONFIRMED).copy(id = 42L)
        var appointments by mutableStateOf(emptyList<Appointment>())
        var syncState by mutableStateOf(AppointmentSyncState.LOADING)

        compose.setContent {
            MaterialTheme { RenderAppointments(appointments, syncState) }
        }

        compose.onNodeWithText("Solicitar").assertDoesNotExist()
        compose.onNodeWithText("Solicitar cita").assertDoesNotExist()
        compose.runOnIdle { syncState = AppointmentSyncState.UP_TO_DATE }
        compose.onNodeWithText("Solicitar").assertIsSelected()
        appointmentList().performScrollToKey("request")
        compose.onNodeWithText("Solicitar cita").assertExists()

        compose.runOnIdle { appointments = listOf(earlier) }
        compose.onNodeWithText("Calendario").assertIsSelected()
        compose.onNodeWithText("Solicitar").assertDoesNotExist()
        compose.onNodeWithText("Cita activa").assertExists()
        compose.onNodeWithText("Horario: ${formatDateTime(earlier.scheduledAtMillis)}").assertExists()
        assertAutomaticDay(earlier.scheduledAtMillis)

        compose.runOnIdle { appointments = listOf(later, earlier) }
        compose.onNodeWithText("Próxima cita").assertExists()
        assertAutomaticDay(later.scheduledAtMillis)
        compose.runOnIdle { appointments = listOf(later, earlier.copy(status = AppointmentStatus.CANCELLED)) }
        compose.onNodeWithText("Solicitar").assertDoesNotExist()
        assertAutomaticDay(later.scheduledAtMillis)
        compose.runOnIdle {
            appointments = appointments.map { if (it.id == later.id) it.copy(status = AppointmentStatus.REJECTED) else it }
        }
        compose.onNodeWithText("Solicitar").assertIsSelected()
        compose.onNodeWithText("Todas las citas").performClick().assertIsSelected()
        appointmentList().performScrollToKey(later.id)
        compose.onNodeWithText("Rechazada").assertExists()
        compose.onNodeWithText("Cancelada").assertDoesNotExist()
    }

    @Test
    fun completedDayFromGarageRetainsSelectionWhenActiveAppointmentLoads() {
        val active = sampleAppointment(AppointmentStatus.CONFIRMED)
        val completed = active.copy(
            id = 42L,
            status = AppointmentStatus.COMPLETED,
            scheduledAtMillis = active.scheduledAtMillis - 8 * 24 * 60 * 60_000L
        )
        var requestedDay by mutableStateOf<Long?>(appointmentDateUtcMillis(completed.scheduledAtMillis))
        var appointments by mutableStateOf(emptyList<Appointment>())
        var syncState by mutableStateOf(AppointmentSyncState.LOADING)
        var consumed = 0
        compose.setContent {
            MaterialTheme {
                RenderAppointments(appointments, syncState, requestedDay) {
                    consumed++
                    requestedDay = null
                }
            }
        }

        assertAutomaticDay(completed.scheduledAtMillis)
        compose.runOnIdle {
            assertEquals(1, consumed)
            appointments = listOf(active, completed)
            syncState = AppointmentSyncState.UP_TO_DATE
        }
        compose.onNodeWithText("Solicitar").assertDoesNotExist()
        assertAutomaticDay(completed.scheduledAtMillis)
        appointmentList().performScrollToKey(completed.id)
        compose.onNodeWithText("Cita terminada").assertExists()

        appointmentList().performScrollToKey("nextAppointment")
        compose.onNodeWithText("Ver día de la cita").performScrollTo().performClick()
        assertAutomaticDay(active.scheduledAtMillis)
    }

    @Test
    fun workshopSeesNextAppointmentAndAllVisibleDatesInChronologicalOrder() {
        val next = sampleAppointment(AppointmentStatus.PENDING)
        val previous = next.copy(
            id = 42L,
            status = AppointmentStatus.COMPLETED,
            scheduledAtMillis = next.scheduledAtMillis - 3 * 24 * 60 * 60_000L
        )
        val later = next.copy(
            id = 43L,
            status = AppointmentStatus.CONFIRMED,
            scheduledAtMillis = next.scheduledAtMillis + 24 * 60 * 60_000L
        )
        val cancelled = next.copy(
            id = 44L,
            status = AppointmentStatus.CANCELLED,
            scheduledAtMillis = next.scheduledAtMillis - 60 * 60_000L
        )

        compose.setContent {
            MaterialTheme {
                RenderAppointments(
                    listOf(later, cancelled, previous, next),
                    AppointmentSyncState.UP_TO_DATE,
                    isWorkshop = true
                )
            }
        }

        compose.onNodeWithText("Ver citas agendadas").performClick()
        compose.onNodeWithText("Próxima cita").assertExists()
        compose.onNodeWithText("Horario: ${formatDateTime(next.scheduledAtMillis)}").assertExists()
        compose.onNodeWithText("Pendiente de confirmación").assertExists()
        appointmentList().performScrollToKey("calendar")
        selectDay(next.scheduledAtMillis, "1 cita")

        compose.onNodeWithText("Todas las citas").performClick().assertIsSelected()
        val indexFor = appointmentList().fetchSemanticsNode().config[SemanticsProperties.IndexForKey]
        assertEquals(-1, indexFor(cancelled.id))
        assertTrue(indexFor(previous.id) in 0 until indexFor(next.id))
        assertTrue(indexFor(next.id) in 0 until indexFor(later.id))
        appointmentList().performScrollToKey(later.id)
        compose.onNodeWithText("Terminar cita").assertExists()
    }

    @Test
    fun workshopFinishesConfirmedAppointmentAndCanRegisterMaintenance() {
        val confirmed = sampleAppointment(AppointmentStatus.CONFIRMED)
        val finishTime = confirmed.scheduledAtMillis + 47 * 60_000L
        val completed = confirmed.copy(status = AppointmentStatus.COMPLETED, updatedAtMillis = finishTime)
        var appointment by mutableStateOf(confirmed)
        var finishRequested: Appointment? = null
        var maintenanceRequested: Appointment? = null

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
                    onAccept = {},
                    onReject = {},
                    onCancel = {},
                    onReschedule = { _, _ -> },
                    onClearMessage = {},
                    onRefresh = {},
                    onFinish = {
                        finishRequested = it
                        appointment = completed
                    },
                    onRegisterMaintenance = { maintenanceRequested = it }
                )
            }
        }

        compose.onNodeWithText("Ver citas agendadas").performClick()
        appointmentList().performScrollToKey(confirmed.id)
        compose.onNodeWithText("Terminar cita").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, finishRequested) }
        compose.onNodeWithText("Confirmar finalización").assertIsEnabled().performClick()
        compose.onNodeWithText("Cita terminada").assertExists()
        val formattedFinish = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO")).format(Date(finishTime))
        compose.onNodeWithText("Terminada el $formattedFinish").assertExists()
        compose.onNodeWithText("Terminar cita").assertDoesNotExist()
        compose.onNodeWithText("Cancelar cita").assertDoesNotExist()
        compose.onNodeWithText("Reagendar").assertDoesNotExist()
        compose.onNodeWithText("Registrar mantenimiento").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(confirmed, finishRequested)
            assertEquals(completed, maintenanceRequested)
        }
    }

    @Test
    fun clientSeesPickupStatusAndOnlyTheMatchingMaintenanceReport() {
        val original = sampleAppointment(AppointmentStatus.COMPLETED)
        val completed = original.copy(updatedAtMillis = original.scheduledAtMillis + 47 * 60_000L)
        val report = MaintenanceWithParts(
            record = MaintenanceRecordEntity(
                id = 72,
                vehicleId = 5,
                createdByUserId = 1,
                serviceType = "Cambio de aceite",
                serviceDate = "2026-11-19",
                mileageKm = 42_000,
                description = "Se reemplazó el filtro y se revisaron las fugas."
            ),
            parts = listOf(
                MaintenancePartEntity(maintenanceRecordId = 72, name = "Filtro del motor"),
                MaintenancePartEntity(maintenanceRecordId = 73, name = "Repuesto de otro informe")
            )
        )
        val work = AppointmentWorkEntity(
            clientAuthId = completed.clientAuthId,
            appointmentId = completed.id,
            appointmentCreatedAtMillis = completed.createdAtMillis,
            vehicleId = report.record.vehicleId,
            finishedAtMillis = completed.finishedAtMillis,
            maintenanceRecordId = report.record.id
        )
        var linkedWork by mutableStateOf(
            listOf(
                AppointmentWorkWithMaintenance(work.copy(clientAuthId = "otro-cliente"), report),
                AppointmentWorkWithMaintenance(work.copy(appointmentCreatedAtMillis = work.appointmentCreatedAtMillis - 1), report),
                AppointmentWorkWithMaintenance(work.copy(vehicleId = 6), report)
            )
        )
        var historyVehicle: Int? = null

        compose.setContent {
            MaterialTheme {
                AppointmentsScreen(
                    isWorkshop = false,
                    appointments = listOf(completed),
                    syncState = AppointmentSyncState.UP_TO_DATE,
                    vehicles = emptyList(),
                    isProcessing = false,
                    message = null,
                    isError = false,
                    onCreateAppointment = { _, _, _ -> },
                    onAccept = {},
                    onReject = {},
                    onCancel = {},
                    onReschedule = { _, _ -> },
                    onClearMessage = {},
                    onRefresh = {},
                    appointmentWork = linkedWork,
                    onViewMaintenance = { historyVehicle = it }
                )
            }
        }

        compose.onNodeWithText("Todas las citas").performClick().assertIsSelected()
        appointmentList().performScrollToKey(completed.id)
        compose.onNodeWithText("Listo para recoger").assertExists()
        compose.onNodeWithText("Mantenimiento realizado").assertDoesNotExist()
        compose.onNodeWithText("Registrar mantenimiento").assertDoesNotExist()
        compose.onNodeWithText("Terminar cita").assertDoesNotExist()

        compose.runOnIdle { linkedWork = listOf(AppointmentWorkWithMaintenance(work, report)) }
        compose.onNodeWithText("Mantenimiento realizado").assertExists()
        compose.onNodeWithText(report.record.description).assertExists()
        compose.onNodeWithText("Filtro del motor · 1 unidad").assertExists()
        compose.onNodeWithText("Repuesto de otro informe", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Ver mantenimiento").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(report.record.vehicleId, historyVehicle) }
    }

    private fun appointmentList() = compose.onNode(hasScrollToIndexAction())

    @Composable
    private fun RenderAppointments(
        appointments: List<Appointment>,
        syncState: AppointmentSyncState,
        requestedDay: Long? = null,
        isWorkshop: Boolean = false,
        onDayHandled: () -> Unit = {}
    ) {
        AppointmentsScreen(
            isWorkshop = isWorkshop,
            appointments = appointments,
            syncState = syncState,
            vehicles = listOf(Vehicle(1, "Marca", "Modelo", 2021, "Motor", ownerUserId = 1)),
            isProcessing = false,
            message = null,
            isError = false,
            onCreateAppointment = { _, _, _ -> },
            onAccept = {},
            onReject = {},
            onCancel = {},
            onReschedule = { _, _ -> },
            onClearMessage = {},
            onRefresh = {},
            requestedCalendarDay = requestedDay,
            onCalendarDayHandled = onDayHandled
        )
    }

    private fun assertAutomaticDay(millis: Long) {
        appointmentList().performScrollToKey("calendar")
        val day = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "CO")).format(Date(millis))
        compose.onNodeWithContentDescription(day).performScrollTo().assertIsSelected()
    }

    private fun formatDateTime(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO")).format(Date(millis))

    private fun selectDay(millis: Long, count: String) {
        val date = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "CO")).format(Date(millis))
        compose.onNodeWithContentDescription(date)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, count))
            .performScrollTo().performClick().assertIsSelected()
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
