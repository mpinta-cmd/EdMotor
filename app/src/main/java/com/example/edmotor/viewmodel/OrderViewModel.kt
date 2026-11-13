
package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.OrderWithItems
import com.example.edmotor.data.repository.OrderOperationResult
import com.example.edmotor.data.repository.OrderRepository
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class OrderViewModel(
    private val repository: OrderRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // ==========================================
    // PEDIDOS SEGÚN EL USUARIO AUTENTICADO
    // ==========================================

    val orders = sessionManager.currentUser
        .flatMapLatest { user ->

            if (user == null) {

                flowOf(emptyList<OrderWithItems>())

            } else {

                when (user.role) {

                    UserRole.WORKSHOP.name ->
                        repository.observeAllOrders()

                    UserRole.CLIENT.name ->
                        repository.observeOrdersForClient(user.id)

                    else ->
                        flowOf(emptyList())
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val pendingOrdersCount = orders
        .map { list -> list.count { it.order.status == com.example.edmotor.data.local.OrderStatus.PENDING } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            0
        )

    // ==========================================
    // ESTADO DE OPERACIONES
    // ==========================================

    private val _isProcessing = MutableStateFlow(false)

    val isProcessing = _isProcessing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    val message = _message.asStateFlow()

    private val _isError = MutableStateFlow(false)

    val isError = _isError.asStateFlow()

    // ==========================================
    // ENVIAR PEDIDO
    // EXCLUSIVO DEL CLIENTE
    // ==========================================

    fun submitOrder() {

        val user = sessionManager.currentUser.value

        if (user == null || user.role != UserRole.CLIENT.name) {

            showMessage(
                "Solo los clientes pueden enviar pedidos.",
                true
            )

            return
        }

        if (_isProcessing.value) return

        viewModelScope.launch {

            _isProcessing.value = true
            _message.value = null

            try {

                handleResult(
                    repository.createPendingOrder(user)
                )

            } finally {

                _isProcessing.value = false
            }
        }
    }

    // ==========================================
    // CONFIRMAR VENTA
    // EXCLUSIVO DEL TALLER
    // ==========================================

    fun confirmOrder(orderId: Int) {

        val user = sessionManager.currentUser.value

        if (user == null || user.role != UserRole.WORKSHOP.name) {

            showMessage(
                "No tienes permisos para confirmar ventas.",
                true
            )

            return
        }

        if (_isProcessing.value) return

        viewModelScope.launch {

            _isProcessing.value = true
            _message.value = null

            try {

                handleResult(
                    repository.confirmOrder(user, orderId)
                )

            } finally {

                _isProcessing.value = false
            }
        }
    }

    // ==========================================
    // RECHAZAR PEDIDO
    // EXCLUSIVO DEL TALLER
    // ==========================================

    fun rejectOrder(orderId: Int) {

        val user = sessionManager.currentUser.value

        if (user == null || user.role != UserRole.WORKSHOP.name) {

            showMessage(
                "No tienes permisos para rechazar pedidos.",
                true
            )

            return
        }

        if (_isProcessing.value) return

        viewModelScope.launch {

            _isProcessing.value = true
            _message.value = null

            try {

                handleResult(
                    repository.rejectOrder(user, orderId)
                )

            } finally {

                _isProcessing.value = false
            }
        }
    }

    // ==========================================
    // PROCESAR RESULTADOS
    // ==========================================

    private fun handleResult(
        result: OrderOperationResult
    ) {

        when (result) {

            is OrderOperationResult.Success -> {

                showMessage(result.message)
            }

            is OrderOperationResult.Error -> {

                showMessage(result.message, true)
            }
        }
    }

    private fun showMessage(
        text: String,
        error: Boolean = false
    ) {

        _message.value = text
        _isError.value = error
    }

    fun clearMessage() {
        _message.value = null
        _isError.value = false
    }
}
