package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.repository.OrderOperationResult
import com.example.edmotor.data.repository.OrderRepository
import com.example.edmotor.model.OrderReference
import com.example.edmotor.model.OrderSource
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.OrderWithItems
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class OrderViewModel(
    private val repository: OrderRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val history = sessionManager.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(OrderHistory(null, emptyList()))
            } else {
                repository.observeOrders(user)
                    .map { OrderHistory(user.id, it) }
                    .onStart { emit(OrderHistory(user.id, emptyList())) }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0),
            OrderHistory(null, emptyList())
        )

    val orders = combine(sessionManager.currentUser, history) { user, currentHistory ->
        if (user?.id == currentHistory.userId) currentHistory.orders else emptyList()
    }.distinctUntilChanged()

    val pendingOrdersCount = orders
        .map { orders ->
            orders.count {
                it.order.reference.source == OrderSource.LOCAL &&
                    it.order.status == OrderStatus.PENDING
            }
        }
        .distinctUntilChanged()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val feedback = MutableStateFlow<OrderFeedback?>(null)
    private val accountFeedback = combine(sessionManager.currentUser, feedback) { user, result ->
        result?.takeIf { it.userId == user?.id }
    }
    val message = accountFeedback.map { it?.message }.distinctUntilChanged()
    val isError = accountFeedback.map { it?.isError == true }.distinctUntilChanged()

    fun submitOrder() = perform(
        role = UserRole.CLIENT,
        permissionMessage = "Solo los clientes pueden guardar pedidos.",
        action = repository::createPendingOrder
    )

    fun confirmOrder(reference: OrderReference) = perform(
        role = UserRole.WORKSHOP,
        permissionMessage = "Solo el taller puede confirmar pedidos."
    ) { workshop -> repository.confirmOrder(workshop, reference) }

    fun rejectOrder(reference: OrderReference) = perform(
        role = UserRole.WORKSHOP,
        permissionMessage = "Solo el taller puede rechazar pedidos."
    ) { workshop -> repository.rejectOrder(workshop, reference) }

    fun clearMessage() {
        feedback.value = null
    }

    private fun perform(
        role: UserRole,
        permissionMessage: String,
        action: suspend (UserEntity) -> OrderOperationResult
    ) {
        if (_isProcessing.value) return
        val user = sessionManager.currentUser.value
        if (user == null || user.role != role.name) {
            feedback.value = OrderFeedback(user?.id, permissionMessage, isError = true)
            return
        }

        _isProcessing.value = true
        clearMessage()
        viewModelScope.launch {
            try {
                val result = action(user)
                if (sessionManager.currentUser.value?.id == user.id) {
                    feedback.value = when (result) {
                        is OrderOperationResult.Success -> OrderFeedback(user.id, result.message, isError = false)
                        is OrderOperationResult.Error -> OrderFeedback(user.id, result.message, isError = true)
                    }
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private data class OrderHistory(val userId: Int?, val orders: List<OrderWithItems>)

    private data class OrderFeedback(val userId: Int?, val message: String, val isError: Boolean)
}
