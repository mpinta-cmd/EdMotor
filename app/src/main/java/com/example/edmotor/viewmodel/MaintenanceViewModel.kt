
package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.data.repository.MaintenanceRepository
import com.example.edmotor.model.MaintenanceDraft
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager

import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModel(
    private val repository: MaintenanceRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val selectedVehicleId =
        MutableStateFlow<Int?>(null)

    private var loadRecordJob: Job? = null


    // =====================================================
    // HISTORIAL
    // =====================================================

    val records = combine(
        selectedVehicleId,
        sessionManager.currentUser
    ) { vehicleId, user ->

        vehicleId to user

    }.flatMapLatest { (vehicleId, user) ->

        if (vehicleId == null || user == null) {

            flowOf(emptyList<MaintenanceWithParts>())

        } else {

            repository.observeForVehicle(
                vehicleId = vehicleId,
                requestingUserId = user.id
            )
        }

    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )


    // =====================================================
    // REGISTRO EN EDICIÓN
    // =====================================================

    private val _editingRecord =
        MutableStateFlow<MaintenanceWithParts?>(null)

    val editingRecord = _editingRecord.asStateFlow()


    // =====================================================
    // ESTADOS DE CARGA
    // =====================================================

    private val _isLoadingRecord =
        MutableStateFlow(false)

    val isLoadingRecord = _isLoadingRecord.asStateFlow()

    private val _isSaving =
        MutableStateFlow(false)

    val isSaving = _isSaving.asStateFlow()


    // =====================================================
    // ERRORES Y MENSAJES
    // =====================================================

    private val _editingRecordError =
        MutableStateFlow<String?>(null)

    val editingRecordError = _editingRecordError.asStateFlow()

    private val _operationMessage =
        MutableStateFlow<String?>(null)

    val operationMessage = _operationMessage.asStateFlow()


    // =====================================================
    // SELECCIONAR VEHÍCULO
    // =====================================================

    fun selectVehicle(vehicleId: Int) {

        if (selectedVehicleId.value != vehicleId) {
            selectedVehicleId.value = vehicleId
        }

        clearEditingRecord()
    }


    // =====================================================
    // CARGAR MANTENIMIENTO
    // =====================================================

    fun loadRecord(
        recordId: Int,
        vehicleId: Int
    ) {

        loadRecordJob?.cancel()

        val user = sessionManager.currentUser.value

        if (
            user == null ||
            user.role != UserRole.WORKSHOP.name
        ) {

            _editingRecordError.value =
                "No tienes permisos para editar mantenimientos."

            _isLoadingRecord.value = false

            return
        }

        if (recordId <= 0 || vehicleId <= 0) {

            _editingRecordError.value =
                "Los datos del mantenimiento no son válidos."

            return
        }

        _isLoadingRecord.value = true
        _editingRecord.value = null
        _editingRecordError.value = null

        loadRecordJob = viewModelScope.launch {

            try {

                val result = repository.findWithParts(
                    recordId = recordId,
                    vehicleId = vehicleId,
                    requestingUserId = user.id
                )

                if (result == null) {

                    _editingRecordError.value =
                        "No se encontró el mantenimiento para este vehículo."

                } else {

                    _editingRecord.value = result
                }

            } catch (e: Exception) {

                _editingRecordError.value =
                    e.message ?: "No fue posible cargar el mantenimiento."

            } finally {

                _isLoadingRecord.value = false
            }
        }
    }


    // =====================================================
    // LIMPIAR EDICIÓN
    // =====================================================

    fun clearEditingRecord() {

        loadRecordJob?.cancel()
        loadRecordJob = null

        _editingRecord.value = null
        _editingRecordError.value = null
        _isLoadingRecord.value = false
    }


    // =====================================================
    // GUARDAR MANTENIMIENTO
    // =====================================================

    fun saveMaintenance(
        vehicleId: Int,
        recordId: Int?,
        draft: MaintenanceDraft,
        onResult: (Boolean, String?) -> Unit
    ) {

        if (_isSaving.value) {
            onResult(false, "La operación ya está en proceso.")
            return
        }

        val user = sessionManager.currentUser.value

        if (
            user == null ||
            user.role != UserRole.WORKSHOP.name
        ) {

            val message =
                "Solo el taller puede registrar o modificar mantenimientos."

            _operationMessage.value = message

            onResult(false, message)

            return
        }

        _isSaving.value = true

        viewModelScope.launch {

            try {

                repository.saveMaintenance(
                    workshopUserId = user.id,
                    vehicleId = vehicleId,
                    recordId = recordId,
                    draft = draft
                )

                _operationMessage.value =
                    "Mantenimiento guardado correctamente."

                _editingRecord.value = null

                _editingRecordError.value = null

                onResult(true, null)

            } catch (e: Exception) {

                val message = e.message
                    ?: "No fue posible guardar el mantenimiento."

                _operationMessage.value = message

                onResult(false, message)

            } finally {

                _isSaving.value = false
            }
        }
    }


    // =====================================================
    // ELIMINAR MANTENIMIENTO
    // =====================================================

    fun deleteMaintenance(recordId: Int) {

        val user = sessionManager.currentUser.value

        if (
            user == null ||
            user.role != UserRole.WORKSHOP.name
        ) {

            _operationMessage.value =
                "No tienes permisos para eliminar mantenimientos."

            return
        }

        viewModelScope.launch {

            try {

                repository.deleteMaintenance(
                    recordId = recordId,
                    workshopUserId = user.id
                )

                _operationMessage.value =
                    "Mantenimiento eliminado correctamente."

            } catch (e: Exception) {

                _operationMessage.value =
                    e.message ?: "No fue posible eliminar el mantenimiento."
            }
        }
    }


    // =====================================================
    // LIMPIAR MENSAJES
    // =====================================================

    fun clearMessage() {
        _operationMessage.value = null
    }
}
