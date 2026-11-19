package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.repository.AppointmentAccount
import com.example.edmotor.data.repository.AppointmentRepository
import com.example.edmotor.data.repository.AppointmentSnapshot
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentOperationResult
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle
import com.example.edmotor.session.PickupEvent
import com.example.edmotor.session.SessionManager
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentViewModel(
    private val repository: AppointmentRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    private val currentAccount = sessionManager.currentUser
        .map(repository::accountFor)
        .distinctUntilChanged()

    private val cachedSnapshots = currentAccount.flatMapLatest { account ->
        if (account == null) {
            flowOf(null)
        } else {
            repository.observeAppointments(account).map { ScopedSnapshot(account, it) }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0),
        null
    )

    private val visibleSnapshot = combine(currentAccount, cachedSnapshots) { _, cached ->
        val account = repository.accountFor(sessionManager.currentUser.value)
        if (cached != null && cached.account == account) {
            cached.snapshot
        } else {
            AppointmentSnapshot(
                appointments = emptyList(),
                syncState = if (account == null) AppointmentSyncState.NOT_CONFIGURED else AppointmentSyncState.LOADING
            )
        }
    }

    val appointments = visibleSnapshot.map { it.appointments }.distinctUntilChanged()
    val syncState = visibleSnapshot.map { it.syncState }.distinctUntilChanged()
    val pendingCount = appointments.map { list ->
        list.count { it.status == AppointmentStatus.PENDING }
    }.distinctUntilChanged()

    val pickupNotice: Flow<Appointment?> = sessionManager.currentUser.flatMapLatest { user ->
        val account = repository.accountFor(user)
        if (account?.role != UserRole.CLIENT) {
            flowOf(null)
        } else {
            combine(cachedSnapshots, sessionManager.observePickupDismissal(account.profileKey)) { cached, dismissed ->
                if (sessionManager.currentUser.value === user) latestPickup(account, cached, dismissed)
                else null
            }.onStart { emit(null) }.retryWhen { error, _ ->
                if (error !is IOException) return@retryWhen false
                emit(null)
                delay(1_000)
                true
            }
        }
    }.buffer(0).distinctUntilChanged()

    private val operation = MutableStateFlow(OperationState())
    private val visibleOperation = combine(currentAccount, operation) { _, state ->
        if (state.account == repository.accountFor(sessionManager.currentUser.value)) state else OperationState()
    }

    val isProcessing = visibleOperation.map { it.isProcessing }.distinctUntilChanged()
    val message = visibleOperation.map { it.message }.distinctUntilChanged()
    val isError = visibleOperation.map { it.isError }.distinctUntilChanged()

    init {
        viewModelScope.launch {
            currentAccount.collect {
                operation.update { current ->
                    if (current.account == repository.accountFor(sessionManager.currentUser.value)) current
                    else OperationState()
                }
            }
        }
    }

    fun refreshAppointments() = repository.refreshAppointments()

    fun dismissPickup(appointment: Appointment) {
        val user = sessionManager.currentUser.value ?: return
        val account = repository.accountFor(user)?.takeIf { it.role == UserRole.CLIENT } ?: return
        if (appointment.clientAuthId != account.profileKey) return
        val event = PickupEvent.from(appointment) ?: return

        viewModelScope.launch {
            if (sessionManager.currentUser.value !== user) return@launch
            val latest = latestPickup(account, cachedSnapshots.value) ?: return@launch
            if (PickupEvent.from(latest) != event) return@launch
            try {
                sessionManager.dismissPickup(user, event)
            } catch (_: IOException) {
                if (sessionManager.currentUser.value === user) {
                    showMessage(account, "No se pudo guardar el cierre del aviso. Inténtalo de nuevo.", isError = true)
                }
            }
        }
    }

    private fun latestPickup(
        account: AppointmentAccount,
        cached: ScopedSnapshot?,
        dismissed: PickupEvent? = null
    ): Appointment? {
        if (cached?.account != account) return null
        return latestPickupNotice(cached.snapshot.appointments, account.profileKey, dismissed)
    }

    fun createAppointment(vehicle: Vehicle, scheduledAtMillis: Long, serviceType: String) {
        val user = sessionManager.currentUser.value
        val account = repository.accountFor(user)
        if (user == null || account == null || account.role != UserRole.CLIENT) {
            showMessage(account, "Selecciona un perfil de cliente en línea para solicitar una cita.", isError = true)
            return
        }
        execute(account) { repository.createAppointment(user, vehicle, scheduledAtMillis, serviceType) }
    }

    fun acceptAppointment(appointmentId: Long) = executeAppointmentChange(workshopOnly = true) { user ->
        repository.acceptAppointment(user, appointmentId)
    }

    fun finishAppointment(appointment: Appointment, onFinished: (Long?) -> Unit) = executeAppointmentChange(
        workshopOnly = true,
        onSuccess = { onFinished(it.workId) }
    ) { user ->
        repository.finishAppointment(user, appointment.id, expected = appointment)
    }

    fun rejectAppointment(appointmentId: Long) = executeAppointmentChange(workshopOnly = true) { user ->
        repository.rejectAppointment(user, appointmentId)
    }

    fun cancelAppointment(appointment: Appointment) = executeAppointmentChange { user ->
        repository.cancelAppointment(user, appointment)
    }

    fun rescheduleAppointment(appointmentId: Long, scheduledAtMillis: Long) = executeAppointmentChange { user ->
        repository.rescheduleAppointment(user, appointmentId, scheduledAtMillis)
    }

    private fun executeAppointmentChange(
        workshopOnly: Boolean = false,
        onSuccess: (AppointmentOperationResult.Success) -> Unit = {},
        request: suspend (UserEntity) -> AppointmentOperationResult
    ) {
        val user = sessionManager.currentUser.value
        val account = repository.accountFor(user)
        if (user == null || account == null) {
            showMessage(account, "Entra con tu perfil para gestionar las citas.", isError = true)
            return
        }
        if (workshopOnly && account.role != UserRole.WORKSHOP) {
            showMessage(account, "Entra como dueño para revisar las solicitudes de cita.", isError = true)
            return
        }
        execute(account, onSuccess) { request(user) }
    }

    private fun execute(
        account: AppointmentAccount,
        onSuccess: (AppointmentOperationResult.Success) -> Unit = {},
        request: suspend () -> AppointmentOperationResult
    ) {
        val activeUser = sessionManager.currentUser.value
        val currentOperation = operation.value
        if (currentOperation.account == account && currentOperation.isProcessing) return

        val processing = OperationState(account, isProcessing = true)
        operation.value = processing
        viewModelScope.launch {
            val result = try {
                request()
            } catch (e: CancellationException) {
                operation.update { if (it === processing) OperationState() else it }
                throw e
            } catch (_: Exception) {
                AppointmentOperationResult.Error("No fue posible completar la operación.")
            }

            val completed = if (sessionManager.currentUser.value !== activeUser) {
                OperationState()
            } else {
                when (result) {
                    is AppointmentOperationResult.Success -> OperationState(account, message = result.message)
                    is AppointmentOperationResult.Error -> OperationState(account, message = result.message, isError = true)
                }
            }
            val applied = operation.updateAndGet { if (it === processing) completed else it }
            if (applied === completed && result is AppointmentOperationResult.Success &&
                sessionManager.currentUser.value === activeUser
            ) {
                onSuccess(result)
            }
        }
    }

    private fun showMessage(account: AppointmentAccount?, message: String, isError: Boolean) {
        operation.update { current ->
            if (current.account == account && current.isProcessing) current
            else OperationState(account, message = message, isError = isError)
        }
    }

    fun clearMessage() {
        operation.update { if (it.message == null) it else it.copy(message = null, isError = false) }
    }

    private data class ScopedSnapshot(val account: AppointmentAccount, val snapshot: AppointmentSnapshot)

    private data class OperationState(
        val account: AppointmentAccount? = null,
        val isProcessing: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false
    )
}

internal fun latestPickupNotice(
    appointments: List<Appointment>,
    clientAuthId: String,
    dismissed: PickupEvent? = null
): Appointment? {
    val (appointment, event) = appointments.asSequence()
        .filter { it.clientAuthId == clientAuthId }
        .mapNotNull { appointment -> PickupEvent.from(appointment)?.let { appointment to it } }
        .maxByOrNull { it.second } ?: return null

    return appointment.takeUnless {
        dismissed != null && (event <= dismissed || event.sameAppointment(dismissed))
    }
}
