package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.repository.AppointmentRepository
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentOperationResult
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentViewModel(
    private val repository: AppointmentRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    val appointments = sessionManager.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList<Appointment>())
            } else {
                repository.observeForCurrentUser(
                    isWorkshop = user.role == UserRole.WORKSHOP.name
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val pendingCount = appointments
        .map { list -> list.count { it.status == AppointmentStatus.PENDING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError = _isError.asStateFlow()

    fun createAppointment(
        vehicle: Vehicle,
        scheduledAtMillis: Long,
        description: String
    ) {
        val user = sessionManager.currentUser.value
        if (user == null || user.role != UserRole.CLIENT.name) {
            showMessage("Solo los clientes pueden solicitar citas.", true)
            return
        }
        execute {
            repository.createAppointment(
                client = user,
                vehicle = vehicle,
                scheduledAtMillis = scheduledAtMillis,
                description = description
            )
        }
    }

    fun acceptAppointment(appointmentId: Long) {
        val user = workshopUser() ?: return
        execute { repository.acceptAppointment(user, appointmentId) }
    }

    fun rejectAppointment(appointmentId: Long) {
        val user = workshopUser() ?: return
        execute { repository.rejectAppointment(user, appointmentId) }
    }

    private fun workshopUser(): UserEntity? {
        val user = sessionManager.currentUser.value
        if (user == null || user.role != UserRole.WORKSHOP.name) {
            showMessage("Solo EdMotor puede administrar las citas.", true)
            return null
        }
        return user
    }

    private fun execute(operation: suspend () -> AppointmentOperationResult) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _message.value = null
            try {
                when (val result = operation()) {
                    is AppointmentOperationResult.Success -> showMessage(result.message)
                    is AppointmentOperationResult.Error -> showMessage(result.message, true)
                }
            } catch (e: Exception) {
                showMessage(e.message ?: "No fue posible completar la operación.", true)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun showMessage(message: String, error: Boolean = false) {
        _message.value = message
        _isError.value = error
    }

    fun clearMessage() {
        _message.value = null
        _isError.value = false
    }
}
