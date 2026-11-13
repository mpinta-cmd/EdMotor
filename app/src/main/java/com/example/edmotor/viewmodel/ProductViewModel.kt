package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.data.repository.ProductOperationResult
import com.example.edmotor.data.repository.ProductRepository
import com.example.edmotor.model.Category
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModel(
    private val repository: ProductRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    val isWorkshop = sessionManager.currentUser
        .map { it?.role == UserRole.WORKSHOP.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val products = sessionManager.currentUser
        .flatMapLatest { user ->
            if (user?.role == UserRole.WORKSHOP.name) {
                repository.observeAllProducts()
            } else {
                repository.observeCatalog()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Category>())

    private val _selectedProduct = MutableStateFlow<ProductEntity?>(null)
    val selectedProduct = _selectedProduct.asStateFlow()

    fun selectProduct(product: ProductEntity?) {
        _selectedProduct.value = product
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(value: String) {
        _searchQuery.value = value
    }

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError = _isError.asStateFlow()

    private val _catalogInitialized = MutableStateFlow(false)
    val catalogInitialized = _catalogInitialized.asStateFlow()

    fun initializeCatalog() {
        if (_catalogInitialized.value) return
        viewModelScope.launch {
            repository.initializeCatalog()
                .onSuccess {
                    _catalogInitialized.value = true
                }
                .onFailure {
                    showMessage(
                        it.message ?: "No se pudo inicializar el catálogo.",
                        true
                    )
                }
        }
    }

    fun createCategory(
        name: String,
        onResult: (ProductOperationResult) -> Unit = {}
    ) {
        val user = sessionManager.currentUser.value
        if (user?.role != UserRole.WORKSHOP.name) {
            val result = ProductOperationResult.Error("Solo EdMotor puede crear categorías.")
            showMessage(result.message, true)
            onResult(result)
            return
        }
        if (_isSaving.value) {
            onResult(ProductOperationResult.Error("Espera a que termine la operación actual."))
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                val result = repository.createCategory(UserRole.WORKSHOP, name)
                when (result) {
                    is ProductOperationResult.Success -> showMessage(result.message)
                    is ProductOperationResult.Error -> showMessage(result.message, true)
                }
                onResult(result)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun createProduct(
        name: String,
        description: String,
        category: String,
        priceCop: Long,
        imageRes: Int,
        contentDescription: String,
        stock: Int,
        isNew: Boolean,
        isOnPromotion: Boolean,
        promotionPriceCop: Long?,
        imageUri: String? = null,
        onResult: (ProductOperationResult) -> Unit = {}
    ) {
        val user = sessionManager.currentUser.value
        if (user?.role != UserRole.WORKSHOP.name) {
            val result = ProductOperationResult.Error("Solo EdMotor puede crear repuestos.")
            showMessage(result.message, true)
            onResult(result)
            return
        }
        if (_isSaving.value) {
            onResult(ProductOperationResult.Error("Ya hay una operación de inventario en curso."))
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                val result = repository.createProduct(
                    role = UserRole.WORKSHOP,
                    name = name,
                    description = description,
                    category = category,
                    priceCop = priceCop,
                    imageRes = imageRes,
                    contentDescription = contentDescription,
                    stock = stock,
                    isNew = isNew,
                    isOnPromotion = isOnPromotion,
                    promotionPriceCop = promotionPriceCop,
                    imageUri = imageUri
                )
                when (result) {
                    is ProductOperationResult.Success -> showMessage(result.message)
                    is ProductOperationResult.Error -> showMessage(result.message, true)
                }
                onResult(result)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateProduct(
        product: ProductEntity,
        onResult: (ProductOperationResult) -> Unit = {}
    ) {
        val user = sessionManager.currentUser.value
        if (user?.role != UserRole.WORKSHOP.name) {
            val result = ProductOperationResult.Error("Solo EdMotor puede modificar repuestos.")
            showMessage(result.message, true)
            onResult(result)
            return
        }
        if (_isSaving.value) {
            onResult(ProductOperationResult.Error("Ya hay una operación de inventario en curso."))
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                val result = repository.updateProduct(UserRole.WORKSHOP, product)
                when (result) {
                    is ProductOperationResult.Success -> {
                        _selectedProduct.value = null
                        showMessage(result.message)
                    }
                    is ProductOperationResult.Error -> showMessage(result.message, true)
                }
                onResult(result)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun adjustStock(
        productId: Int,
        delta: Int,
        onResult: (ProductOperationResult) -> Unit = {}
    ) {
        val user = sessionManager.currentUser.value
        if (user?.role != UserRole.WORKSHOP.name) {
            val result = ProductOperationResult.Error("No tienes permisos para modificar el stock.")
            showMessage(result.message, true)
            onResult(result)
            return
        }
        if (_isSaving.value) {
            onResult(ProductOperationResult.Error("Ya hay una operación de inventario en curso."))
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                val result = repository.adjustStock(
                    role = UserRole.WORKSHOP,
                    productId = productId,
                    delta = delta
                )
                when (result) {
                    is ProductOperationResult.Success -> showMessage(result.message)
                    is ProductOperationResult.Error -> showMessage(result.message, true)
                }
                onResult(result)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateStock(productId: Int, quantity: Int) {
        val user = sessionManager.currentUser.value
        if (user?.role != UserRole.WORKSHOP.name) {
            showMessage("No tienes permisos para modificar el stock.", true)
            return
        }
        if (_isSaving.value) return

        _isSaving.value = true
        viewModelScope.launch {
            try {
                when (val result = repository.updateStock(
                    UserRole.WORKSHOP,
                    productId,
                    quantity
                )) {
                    is ProductOperationResult.Success -> showMessage(result.message)
                    is ProductOperationResult.Error -> showMessage(result.message, true)
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteProduct(
        productId: Int,
        onResult: (ProductOperationResult) -> Unit = {}
    ) {
        val user = sessionManager.currentUser.value
        if (user?.role != UserRole.WORKSHOP.name) {
            val result = ProductOperationResult.Error("Solo EdMotor puede eliminar repuestos.")
            showMessage(result.message, true)
            onResult(result)
            return
        }
        if (_isSaving.value) {
            onResult(ProductOperationResult.Error("Ya hay una operación de inventario en curso."))
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                val result = repository.deleteProduct(UserRole.WORKSHOP, productId)
                when (result) {
                    is ProductOperationResult.Success -> {
                        _selectedProduct.value = null
                        showMessage(result.message)
                    }
                    is ProductOperationResult.Error -> showMessage(result.message, true)
                }
                onResult(result)
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun showMessage(text: String, error: Boolean = false) {
        _message.value = text
        _isError.value = error
    }

    fun clearMessage() {
        _message.value = null
        _isError.value = false
    }

    init {
        initializeCatalog()
    }
}
