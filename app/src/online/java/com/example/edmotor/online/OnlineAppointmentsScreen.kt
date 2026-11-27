package com.example.edmotor.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.local.AppointmentWorkEntity
import com.example.edmotor.data.local.AppointmentWorkWithMaintenance
import com.example.edmotor.data.local.MaintenanceRecordEntity
import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.Vehicle
import com.example.edmotor.ui.screens.AppointmentsScreen
import java.util.TimeZone

internal val onlineWorkshopTimeZone: TimeZone = TimeZone.getTimeZone("America/Bogota")

@Composable
internal fun OnlineAppointmentsScreen(
    vm: OnlineViewModel,
    state: OnlineState,
    requestedCalendarDay: Long? = null,
    onOpenGarage: (() -> Unit)? = null,
    onCalendarDayHandled: () -> Unit = {}
) {
    val user = state.user ?: return
    val isWorkshop = user.role == "WORKSHOP"
    val visibleAppointments = remember(state.appointments, user.id, isWorkshop) {
        state.appointments.filter { isWorkshop || it.clientId == user.id }
    }
    val visibleVehicles = remember(state.vehicles, user.id, isWorkshop) {
        state.vehicles.filter { isWorkshop || it.clientId == user.id }
    }
    val appointmentsById = remember(visibleAppointments) { visibleAppointments.associateBy { it.id } }
    val appointments = remember(visibleAppointments) { visibleAppointments.map(OnlineAppointment::toAppointment) }

    // The shared form uses an Int for its selected vehicle. Keep UI keys stable without narrowing online IDs.
    var knownVehicleIds by rememberSaveable(user.id) { mutableStateOf(longArrayOf()) }
    val vehicleIds = remember(knownVehicleIds, visibleVehicles) {
        (knownVehicleIds.asList() + visibleVehicles.map { it.id }).distinct()
    }
    SideEffect {
        if (vehicleIds.size != knownVehicleIds.size) knownVehicleIds = vehicleIds.toLongArray()
    }
    val vehicleKeys = remember(vehicleIds) { vehicleIds.mapIndexed { index, id -> id to (index + 1) }.toMap() }
    val vehiclesByKey = remember(visibleVehicles, vehicleKeys) {
        visibleVehicles.associateBy { vehicleKeys.getValue(it.id) }
    }
    val selectionVehicles = remember(vehiclesByKey, isWorkshop) {
        if (isWorkshop) emptyList() else vehiclesByKey.map { (key, vehicle) ->
            Vehicle(key, vehicle.brand, vehicle.model, vehicle.year, engine = "", plate = vehicle.plate)
        }
    }

    val appointmentWork = remember(state.maintenance, appointmentsById, vehicleKeys) {
        state.maintenance.mapIndexedNotNull { index, maintenance ->
            val appointment = appointmentsById[maintenance.appointmentId]?.takeIf {
                it.status == AppointmentStatus.COMPLETED && it.vehicleId == maintenance.vehicleId
            } ?: return@mapIndexedNotNull null
            val vehicleKey = vehicleKeys[maintenance.vehicleId] ?: return@mapIndexedNotNull null
            val recordId = index + 1
            AppointmentWorkWithMaintenance(
                work = AppointmentWorkEntity(
                    clientAuthId = appointment.clientId.toString(),
                    appointmentId = appointment.id,
                    appointmentCreatedAtMillis = appointment.createdAtMillis,
                    vehicleId = vehicleKey,
                    finishedAtMillis = appointment.finishedAtMillis ?: appointment.updatedAtMillis,
                    maintenanceRecordId = recordId
                ),
                maintenance = MaintenanceWithParts(
                    record = MaintenanceRecordEntity(
                        id = recordId,
                        vehicleId = vehicleKey,
                        createdByUserId = 0,
                        serviceType = appointment.serviceType,
                        serviceDate = "",
                        mileageKm = maintenance.mileageKm ?: 0,
                        description = maintenance.summary,
                        createdAtMillis = maintenance.createdAtMillis
                    ),
                    parts = emptyList()
                )
            )
        }
    }
    var reportAppointmentId by rememberSaveable(user.id) { mutableStateOf<Long?>(null) }
    val reportAlreadySaved = state.maintenance.any { it.appointmentId == reportAppointmentId }
    val reportAppointment = reportAppointmentId?.let(appointmentsById::get)?.takeIf {
        isWorkshop && it.status == AppointmentStatus.COMPLETED && !reportAlreadySaved
    }
    LaunchedEffect(reportAlreadySaved) {
        if (reportAlreadySaved) reportAppointmentId = null
    }
    fun resolveAppointment(appointment: Appointment): OnlineAppointment? =
        appointmentsById[appointment.id]?.takeIf {
            it.clientId.toString() == appointment.clientAuthId && it.createdAtMillis == appointment.createdAtMillis
        }

    AppointmentsScreen(
        isWorkshop = isWorkshop,
        appointments = appointments,
        syncState = when {
            state.configurationError != null -> AppointmentSyncState.NOT_CONFIGURED
            state.loading && state.lastSyncAtMillis == null -> AppointmentSyncState.LOADING
            state.isStale || state.lastSyncAtMillis == null -> AppointmentSyncState.UNAVAILABLE
            else -> AppointmentSyncState.UP_TO_DATE
        },
        vehicles = selectionVehicles,
        isProcessing = state.busy,
        message = state.message?.text,
        isError = state.message?.isError == true,
        onCreateAppointment = { vehicle, time, service ->
            vehiclesByKey[vehicle.id]?.let { vm.createAppointment(it.id, time, service) }
        },
        onAccept = { id -> appointmentsById[id]?.let { vm.changeAppointment(it, "CONFIRM") } },
        onReject = { id -> appointmentsById[id]?.let { vm.changeAppointment(it, "REJECT") } },
        onCancel = { appointment -> resolveAppointment(appointment)?.let(vm::cancelAppointment) },
        onReschedule = { id, time -> appointmentsById[id]?.let { vm.changeAppointment(it, "RESCHEDULE", time) } },
        onClearMessage = vm::clearMessage,
        onRefresh = vm::refresh,
        appointmentWork = appointmentWork,
        onFinish = { appointment ->
            resolveAppointment(appointment)?.let {
                reportAppointmentId = it.id
                vm.changeAppointment(it, "FINISH")
            }
        },
        onRegisterMaintenance = { appointment -> reportAppointmentId = resolveAppointment(appointment)?.id },
        requestedCalendarDay = requestedCalendarDay,
        timeZone = onlineWorkshopTimeZone,
        onOpenGarage = onOpenGarage,
        onCalendarDayHandled = onCalendarDayHandled
    )

    if (reportAppointment != null) {
        MaintenanceReportDialog(
            appointment = reportAppointment,
            saving = state.busy,
            message = state.message?.takeIf { it.isError }?.text,
            onDismiss = { reportAppointmentId = null },
            onSave = { summary, mileageKm ->
                vm.clearMessage()
                vm.recordMaintenance(reportAppointment.id, summary, mileageKm) { reportAppointmentId = null }
            }
        )
    }
}

@Composable
private fun MaintenanceReportDialog(
    appointment: OnlineAppointment,
    saving: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onSave: (String, Int?) -> Unit
) {
    var summary by rememberSaveable(appointment.id) { mutableStateOf("") }
    var mileage by rememberSaveable(appointment.id) { mutableStateOf("") }
    val kilometers = mileage.toIntOrNull()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Registrar mantenimiento") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("${appointment.vehicleBrand} ${appointment.vehicleModel} · ${appointment.vehicleYear}")
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it.take(4_000) },
                    label = { Text("Informe del trabajo") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    minLines = 3
                )
                OutlinedTextField(
                    value = mileage,
                    onValueChange = { mileage = it.filter { char -> char in '0'..'9' }.take(10) },
                    label = { Text("Kilometraje (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = mileage.isNotBlank() && kilometers == null,
                    enabled = !saving
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(summary.trim(), kilometers) },
                enabled = !saving && summary.isNotBlank() && (mileage.isBlank() || kilometers != null)
            ) { Text(if (saving) "Guardando informe" else "Guardar informe") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cerrar") } }
    )
}

private fun OnlineAppointment.toAppointment() = Appointment(
    id = id,
    clientAuthId = clientId.toString(),
    clientName = clientName,
    clientUsername = "",
    clientPhone = null,
    vehicleBrand = vehicleBrand,
    vehicleModel = vehicleModel,
    vehicleYear = vehicleYear,
    vehiclePlate = vehiclePlate,
    scheduledAtMillis = scheduledAtMillis,
    description = serviceType,
    status = status,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = finishedAtMillis ?: updatedAtMillis
)
