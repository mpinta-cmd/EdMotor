package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.AppointmentWorkWithMaintenance
import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.repository.MaintenanceRepository
import com.example.edmotor.model.MaintenanceDraft
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class MaintenanceEditorState(
    val record: MaintenanceWithParts? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class MaintenanceViewModel(
    private val repository: MaintenanceRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private class EditRequest(val vehicleId: Int, val recordId: Int, val user: UserEntity?)
    private data class EditResult(val request: EditRequest, val state: MaintenanceEditorState)
    private data class Feedback(val user: UserEntity?, val vehicleId: Int, val message: String)

    private val editing = MutableStateFlow<EditResult?>(null)
    private val savingUser = MutableStateFlow<UserEntity?>(null)
    private val feedback = MutableStateFlow<Feedback?>(null)
    private var loadRecordJob: Job? = null
    private var mutationJob: Job? = null

    val isSaving = combine(sessionManager.currentUser, savingUser) { user, saving ->
        user != null && user === saving
    }.distinctUntilChanged()

    fun observeOperationMessage(vehicleId: Int): Flow<String?> =
        combine(sessionManager.currentUser, feedback) { user, result ->
            result?.takeIf { it.user === user && it.vehicleId == vehicleId }?.message
        }.distinctUntilChanged()

    init {
        viewModelScope.launch {
            sessionManager.currentUser.collect {
                val user = sessionManager.currentUser.value
                if (editing.value?.request?.user !== user) clearEditingRecord()
                if (savingUser.value !== user) mutationJob?.cancel()
                if (feedback.value?.user !== user) clearMessage()
            }
        }
    }

    fun observeRecords(vehicleId: Int, userId: Int, role: String): Flow<List<MaintenanceWithParts>> =
        repository.observeForVehicle(vehicleId, userId, role)

    fun observeAppointmentWork(userId: Int, role: String): Flow<List<AppointmentWorkWithMaintenance>> =
        repository.observeAppointmentWork(userId, role)

    fun observeEditor(vehicleId: Int, recordId: Int): Flow<MaintenanceEditorState?> =
        combine(sessionManager.currentUser, editing) { user, result ->
            result?.takeIf {
                it.request.user === user && it.request.vehicleId == vehicleId && it.request.recordId == recordId
            }?.state
        }.distinctUntilChanged()

    fun loadRecord(recordId: Int, vehicleId: Int) {
        val user = sessionManager.currentUser.value
        val currentEdit = editing.value
        if (currentEdit != null && currentEdit.request.user === user &&
            currentEdit.request.vehicleId == vehicleId && currentEdit.request.recordId == recordId &&
            (currentEdit.state.isLoading || currentEdit.state.record != null)
        ) return

        clearEditingRecord()
        val request = EditRequest(vehicleId, recordId, user)
        if (user?.role != UserRole.WORKSHOP.name) {
            editing.value = EditResult(request, MaintenanceEditorState(error = "No tienes permisos para editar mantenimientos."))
            return
        }
        if (recordId <= 0 || vehicleId <= 0) {
            editing.value = EditResult(request, MaintenanceEditorState(error = "Los datos del mantenimiento no son válidos."))
            return
        }
        editing.value = EditResult(request, MaintenanceEditorState(isLoading = true))
        loadRecordJob = viewModelScope.launch {
            val state = try {
                val record = repository.findWithParts(recordId, vehicleId, user.id)
                if (record == null) MaintenanceEditorState(error = "No se encontró el mantenimiento para este vehículo.")
                else MaintenanceEditorState(record = record)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                MaintenanceEditorState(error = error.message ?: "No fue posible cargar el mantenimiento.")
            }

            if (editing.value?.request === request && sessionManager.currentUser.value === user) {
                editing.value = EditResult(request, state)
            }
        }
    }

    fun clearEditingRecord() {
        loadRecordJob?.cancel()
        loadRecordJob = null
        editing.value = null
    }

    fun saveMaintenance(
        vehicleId: Int,
        recordId: Int?,
        draft: MaintenanceDraft,
        appointmentWorkId: Long? = null,
        onResult: (Boolean, String?) -> Unit
    ) = mutate(
        vehicleId = vehicleId,
        permissionMessage = "Solo el taller puede registrar o modificar mantenimientos.",
        successMessage = "Mantenimiento guardado correctamente.",
        failureMessage = "No fue posible guardar el mantenimiento.",
        onResult = { success, message ->
            if (success && recordId != null) clearEditorFor(vehicleId, recordId)
            onResult(success, message)
        }
    ) { userId ->
        repository.saveMaintenance(
            workshopUserId = userId,
            vehicleId = vehicleId,
            recordId = recordId,
            draft = draft,
            appointmentWorkId = appointmentWorkId
        )
    }

    fun deleteMaintenance(vehicleId: Int, recordId: Int) = mutate(
        vehicleId = vehicleId,
        permissionMessage = "Solo el taller puede eliminar mantenimientos.",
        successMessage = "Mantenimiento eliminado correctamente.",
        failureMessage = "No fue posible eliminar el mantenimiento.",
        onResult = { success, _ -> if (success) clearEditorFor(vehicleId, recordId) }
    ) { userId -> repository.deleteMaintenance(recordId, vehicleId, userId) }

    private fun clearEditorFor(vehicleId: Int, recordId: Int) {
        val request = editing.value?.request ?: return
        if (request.vehicleId == vehicleId && request.recordId == recordId) clearEditingRecord()
    }

    fun clearMessage() {
        feedback.value = null
    }

    private fun mutate(
        vehicleId: Int,
        permissionMessage: String,
        successMessage: String,
        failureMessage: String,
        onResult: ((Boolean, String?) -> Unit)? = null,
        action: suspend (Int) -> Unit
    ) {
        val user = sessionManager.currentUser.value
        if (user?.role != UserRole.WORKSHOP.name) {
            feedback.value = Feedback(user, vehicleId, permissionMessage)
            onResult?.invoke(false, permissionMessage)
            return
        }
        if (savingUser.value === user) {
            onResult?.invoke(false, "La operación ya está en proceso.")
            return
        }
        mutationJob?.cancel()
        savingUser.value = user
        clearMessage()
        mutationJob = viewModelScope.launch {
            try {
                if (sessionManager.currentUser.value !== user) return@launch
                action(user.id)
                if (sessionManager.currentUser.value === user) {
                    feedback.value = Feedback(user, vehicleId, successMessage)
                    onResult?.invoke(true, null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (sessionManager.currentUser.value === user) {
                    val message = error.message ?: failureMessage
                    feedback.value = Feedback(user, vehicleId, message)
                    onResult?.invoke(false, message)
                }
            } finally {
                if (savingUser.value === user) savingUser.value = null
            }
        }
    }
}
