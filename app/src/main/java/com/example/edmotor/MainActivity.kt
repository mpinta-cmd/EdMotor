package com.example.edmotor
//Librerias importantes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.edmotor.ui.theme.EdMotorTheme
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.style.TextAlign

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdMotorTheme {
                EdMotor()
            }
        }
    }
}


data class Product(
    val id: Int,
    val name: String,
    val description: String,
    val price: String,
    @DrawableRes val imageRes: Int,
    val contentDescription: String = name,
    val rating: Double,
    val badge: String = "",
    val category: String = ""
)

data class Category(
    val emoji: String,
    val nombre: String
)

data class Vehicle(
    val id: Int,
    val brand: String,          // Ej: Chevrolet
    val model: String,          // Ej: Aveo
    val year: Int,              // Ej: 2012
    val engine: String,         // Ej: 1.4L
    val plate: String = "",     // Ej: ABC-123
    val mileage: String = "",   // Ej: 125.000 km
    val notes: String = ""      // Ej: "Llantas infladas a 30 PSI"
)

data class DashboardLight(
    val id: Int,
    val icon: String,              // Emoji o icono representativo
    val name: String,              // Nombre del testigo (ej: Check Engine)
    val severity: String,          // Severidad ("Rojo 🔴", "Amarillo 🟡")
    val meaning: String,           // ¿Qué significa?
    val recommendedAction: String, // ¿Qué debe hacer el conductor?
    val relatedCategory: String = "" // Categoría de repuestos vinculada
)

// Modelo de datos para el buscador de síntomas y ruidos
data class Symptom(
    val id: Int,
    val title: String,
    val emoji: String,
    val probableCause: String,
    val solution: String
)

object Routes {
    const val HOME = "Inicio"
    const val CAR = "Carrito"
    const val PROFILE = "Mi Garaje"
    const val DIAGNOSTIC = "Diagnóstico"
}

val productList = listOf(
    Product(
        id = 1,
        name = "Aceite Mobil 20W-50",
        description = "Aceite lubricante multigrado de alta viscosidad diseñado para motores",
        price = "$203.900",
        imageRes = R.drawable.mobil_20_w_50,
        contentDescription = "Aceite mobil 20 w 50",
        rating = 4.5,
        category = "Aceites y Lubricantes"
    ),
    Product(
        id = 2,
        name = "Aceite Havoline 20w-50",
        description = "Aceite lubricante multigrado mineral de alta viscosidad diseñado para motores a gasolina",
        price = "$87.200",
        imageRes = R.drawable.havoline_20_w_50,
        contentDescription = "Aceite havoline 20 w 50",
        rating = 4.6,
        category = "Aceites y Lubricantes"
    ),
    Product(
        id = 3,
        name = "Filtro de aceite A-111",
        description = "Diseñado para retener impurezas y partículas en el lubricante",
        price = "$20.140",
        imageRes = R.drawable.filtro_a,
        contentDescription = "Filtro de aceite A 111",
        rating = 4.7,
        badge = "Agotado ",
        category = "Filtros"
    ),
    Product(
        id = 4,
        name = "Filtro de aire Aveo",
        description = "Encargado de retener el polvo e impurezas del aire externo antes de que ingresen al motor.",
        price = "$20.000",
        imageRes = R.drawable.filtro_de_aire_aveo,
        contentDescription = "Filtro de aire para aveo",
        rating = 4.6,
        badge = "Nuevo",
        category = "Filtros"
    ),
    Product(
        id = 5,
        name = "Bujias NGK x 4",
        description = "Componentes del sistema de encendido diseñados para generar la chispa eléctrica",
        price = "$14.000",
        imageRes = R.drawable.bujias_ngk,
        contentDescription = "Bujías NGK 4 unidades",
        rating = 5.0,
        badge = "Oferta",
        category = "Bujias"

    )
)

val categoryList = listOf(
    Category(
        emoji = "🛢️",
        nombre = "Aceites y Lubricantes"
    ),
    Category(
        emoji = "🌬️",
        nombre = "Filtros"
    ),
    Category(
        emoji = "⚡",
        nombre = "Bujias"
    ),
    Category(
        emoji = "🌡️",
        nombre = "Refrigeración"
    ),
    Category(
        emoji = "🔋",
        nombre = "Baterías e Iluminación"
    ),
    Category(
        emoji = "🔩",
        nombre = "Suspensión y Dirección"
    ),
    Category(
        emoji = "⚙️",
        nombre = "Motor y Transmisión"
    )
)

val dashboardLightList = listOf(
    DashboardLight(
        id = 1,
        icon = "🛢️",
        name = "Presión de Aceite",
        severity = "Rojo 🔴",
        meaning = "La presión del lubricante descendió a niveles críticos.",
        recommendedAction = "¡DETÉN EL VEHÍCULO DE INMEDIATO! Apaga el motor y verifica el nivel de aceite.",
        relatedCategory = "Aceites y Lubricantes"
    ),
    DashboardLight(
        id = 2,
        icon = "🌡️",
        name = "Temperatura del Motor",
        severity = "Rojo 🔴",
        meaning = "El motor supera la temperatura máxima segura de operación.",
        recommendedAction = "Detente en un lugar seguro, apaga el auto y espera a que el motor se enfríe.",
        relatedCategory = "Refrigeración"
    ),
    DashboardLight(
        id = 3,
        icon = "💻",
        name = "Check Engine",
        severity = "Amarillo 🟡",
        meaning = "La computadora detectó una anomalía en la combustión o sensores.",
        recommendedAction = "Conduce a baja velocidad y agenda un diagnóstico con escáner OBD-II.",
        relatedCategory = "Bujias"
    ),
    DashboardLight(
        id = 4,
        icon = "🛑",
        name = "Frenos / ABS",
        severity = "Amarillo 🟡",
        meaning = "Desgaste en pastillas o baja presión en el sistema hidráulico.",
        recommendedAction = "Revisa el nivel de líquido de frenos y la viscosidad de las pastillas.",
        relatedCategory = "Frenos"
    ),
    DashboardLight(
        id = 5,
        icon = "🔋",
        name = "Batería / Alternador",
        severity = "Rojo 🔴",
        meaning = "El alternador no está suministrando energía al sistema eléctrico.",
        recommendedAction = "Apaga el aire acondicionado y radio. Dirígete al taller antes de agotar la batería.",
        relatedCategory = "Baterías e Iluminación"
    )
)

val symptomList = listOf(
    Symptom(
        id = 1,
        title = "Chillido agudo al frenar",
        emoji = "🔊",
        probableCause = "Pastillas de freno desgastadas o cristalizadas.",
        solution = "Cambio de juego de pastillas de freno y rectificado de discos."
    ),
    Symptom(
        id = 2,
        title = "El auto tiembla al acelerar",
        emoji = "📳",
        probableCause = "Falla de encendido en bujías, cables o inyectores sucios.",
        solution = "Remplazo de bujías de encendido y limpieza de inyectores."
    ),
    Symptom(
        id = 3,
        title = "Humo o vapor en el capó",
        emoji = "💨",
        probableCause = "Fuga en manguera de refrigerante o empaque de tapa de válvulas.",
        solution = "Remplazo de empaques o manguera de refrigeración dañada."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdMotor() {

    var searchQuery by remember { mutableStateOf("") }

    var selectedCategory by remember { mutableStateOf<String?>(null) }

    var cartQuantity by remember {
        mutableStateOf<Map<Int, Int>>(emptyMap())
    }


    fun inCreaseQuantity(productId: Int) {
        val currentlyQuantity = cartQuantity[productId] ?: 0
        cartQuantity = cartQuantity + (
                productId to (currentlyQuantity + 1)
                )
    }

    fun decreaseQuantity(productId: Int) {
        val currentlyQuantity = cartQuantity[productId] ?: 0
        if (currentlyQuantity > 1) {
            cartQuantity = cartQuantity + (
                    productId to (currentlyQuantity - 1)
                    )
        } else {
            cartQuantity = cartQuantity - productId
        }
    }

    val carCount = cartQuantity.values.sum()

    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val currentRoute = navBackStackEntry?.destination?.route

    fun navigateTo(route: String){
        navController.navigate(route){
            popUpTo(navController.graph.findStartDestination().id){
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }


    // Lista de vehículos registrados (Inicia con un vehículo de ejemplo)
    var vehicleList by remember {
        mutableStateOf(
            listOf(
                Vehicle(
                    id = 1,
                    brand = "Chevrolet",
                    model = "Aveo",
                    year = 2012,
                    engine = "1.4L",
                    plate = "KLS-456",
                    mileage = "124.500 km",
                    notes = "Llantas infladas a 30 PSI • Usar refrigerante verde"
                )
            )
        )
    }

    // Función para agregar un auto
    fun addVehicle(
        brand: String,
        model: String,
        year: Int,
        engine: String,
        plate: String,
        mileage: String,
        notes: String
    ) {
        val newId = (vehicleList.maxOfOrNull { it.id } ?: 0) + 1
        val newVehicle = Vehicle(
            id = newId,
            brand = brand,
            model = model,
            year = year,
            engine = engine,
            plate = plate,
            mileage = mileage,
            notes = notes
        )
        vehicleList = vehicleList + newVehicle
    }

    fun deleteVehicle(vehicleId: Int) {
        vehicleList = vehicleList.filter { it.id != vehicleId }
    }

    fun updateVehicleNotes(vehicleId: Int, newNotes: String) {
        vehicleList = vehicleList.map { vehicle ->
            if (vehicle.id == vehicleId) vehicle.copy(notes = newNotes) else vehicle
        }
    }

    fun updateVehicleMileage(vehicleId: Int, newMileage: String) {
        // Si el usuario no escribió "km", se lo agregamos automáticamente
        val formattedMileage = if (newMileage.isNotBlank() && !newMileage.lowercase().contains("km")) {
            "$newMileage km"
        } else newMileage

        vehicleList = vehicleList.map { vehicle ->
            if (vehicle.id == vehicleId) vehicle.copy(mileage = formattedMileage) else vehicle
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("⚙️ EdMotor Automotriz 🧰")
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.HOME ,
                    onClick = {
                        navigateTo(Routes.HOME)
                    },
                    icon = {
                        Text("\uD83C\uDFE0")
                    },
                    label = {
                        Text("Inicio")
                    }
                )

                NavigationBarItem(
                    selected = currentRoute == Routes.CAR,
                    onClick = {
                        navigateTo(Routes.CAR)
                    },
                    icon = {
                        Text("\uD83C\uDFCE\uFE0F")
                    },
                    label = {
                        Text(if (carCount == 0) "Carrito" else "Carrito ($carCount)")
                    }
                )

                NavigationBarItem(
                    selected = currentRoute == Routes.PROFILE,
                    onClick = {
                        navigateTo(Routes.PROFILE)
                    },
                    icon = {
                        Text("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83D\uDD27")
                    },
                    label = {
                        Text("Mi garaje")
                    }
                )

                NavigationBarItem(
                    selected = currentRoute == Routes.DIAGNOSTIC,
                    onClick = { navigateTo(Routes.DIAGNOSTIC) },
                    icon = { Text("🩺") },
                    label = { Text("Diagnóstico") }
                )
            }
        }

    ) { innerPadding ->


        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            //Vista de HOME
            composable(Routes.HOME) {
                HomeScreen(
                    searchQuery = searchQuery,
                    onSearchChange = { newText -> searchQuery = newText },
                    categories = categoryList,
                    product = productList,
                    cartQuantity = cartQuantity,
                    selectedCategory = selectedCategory,
                    onCategorySelect = { categoryName ->
                        selectedCategory = if (selectedCategory == categoryName) null else categoryName
                    },
                    onIncrease = { productId -> inCreaseQuantity(productId) },
                    onDecrease = { productId -> decreaseQuantity(productId) }
                )
            }
            composable(Routes.CAR){
                CartScreen(
                    products = productList,
                    cartQuantity = cartQuantity,
                    onIncrease = {productId ->
                        inCreaseQuantity(productId)
                    },
                    onDecrease = { productId ->
                        decreaseQuantity(productId)
                    }
                )
            }
            composable(Routes.PROFILE) {
                myGargeScreen(
                    vehicles = vehicleList,
                    onAddVehicle = { brand, model, year, engine, plate, mileage, notes ->
                        addVehicle(brand, model, year, engine, plate, mileage, notes)
                    },
                    onDeleteVehicle = { vehicleId ->
                        deleteVehicle(vehicleId)
                    },
                    onUpdateNotes = { vehicleId, newNotes ->
                        updateVehicleNotes(vehicleId, newNotes)
                    },
                    onUpdateMileage = { vehicleId, newMileage -> // <- Nuevo callback
                        updateVehicleMileage(vehicleId, newMileage)
                    }
                )
            }
            composable(Routes.DIAGNOSTIC) {
                DiagnosticScreen(
                    onNavigateToCategory = { categoryName ->
                        selectedCategory = categoryName
                        navigateTo(Routes.HOME) // Redirige a Inicio filtrando por la categoría relacionada
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    categories: List<Category>,
    product: List<Product>,
    cartQuantity: Map<Int, Int>,
    selectedCategory: String?,
    onCategorySelect: (String) -> Unit,
    onIncrease: (Int) -> Unit,
    onDecrease: (Int) -> Unit
) {

    // FILTRADO DOBLE: Por búsqueda escrita Y por categoría seleccionada
    val productosFiltrados = product.filter { p ->
        val coincideBusqueda = p.name.contains(searchQuery, ignoreCase = true)
        val coincideCategoria = selectedCategory == null || p.category.equals(selectedCategory, ignoreCase = true)

        coincideBusqueda && coincideCategoria
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally

    ) {
        // Título Principal

        Spacer(modifier = Modifier.height(20.dp))

        // Subtítulo
        Text(
            text = "¡Hola! \uD83D\uDC4B ",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(5.dp))


        Text(
            text = "¿Que repuesto necesitas hoy? \uD83D\uDD27",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Campo de Búsqueda Dinámico
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange ,
            label = { Text("Buscar repuestos...") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "Categorias",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        //CATEGORIAS usando LazyRow
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories.size) { index ->
                val cat = categories[index]
                CategoryBadge(
                    category = cat,
                    isSelected = selectedCategory == cat.nombre,
                    onClick = { onCategorySelect(cat.nombre) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Título Sección
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "Repuestos Destacados",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recorrido de Productos
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (productosFiltrados.isEmpty()) {
                item {
                    Text(
                        text = "No se encontraron repuestos con ese nombre",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                items(productosFiltrados) { product ->
                    val cantidad = cartQuantity[product.id] ?: 0
                    ProductCard(
                        product = product,
                        cantidad = cantidad,
                        onIncrase = { onIncrease(product.id) },
                        onDecrease = { onDecrease(product.id) }
                    )
                }
            }

        }
    }


}

@Composable
fun CartScreen(
    products: List<Product>,
    cartQuantity: Map<Int, Int>,
    onIncrease: (Int) -> Unit,
    onDecrease: (Int) -> Unit
) {
    // 1. Filtrar solo los productos cuya cantidad en el carrito sea mayor a 0
    val cartItems = products.filter { (cartQuantity[it.id] ?: 0) > 0 }

    // Función auxiliar para extraer el valor numérico del precio (ej: "$203.900" -> 203900.0)
    fun parsePrice(priceStr: String): Double {
        return priceStr.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
    }

    // 2. Calcular la cuenta total sumando (precio * cantidad) de cada producto
    val totalAmount = cartItems.sumOf { product ->
        val quantity = cartQuantity[product.id] ?: 0
        parsePrice(product.price) * quantity
    }

    // Formatear el total con separadores de miles
    val formattedTotal = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "CO"))
        .format(totalAmount)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Carrito de Compras 🛒",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 3. Si no hay productos, mostrar el mensaje solicitado
        if (cartItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Selecciona productos en la ventana de inicio 🛠️",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 4. Lista de productos seleccionados
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cartItems) { product ->
                    val cantidad = cartQuantity[product.id] ?: 0
                    CartItemCard(
                        product = product,
                        cantidad = cantidad,
                        onIncrease = { onIncrease(product.id) },
                        onDecrease = { onDecrease(product.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Tarjeta inferior con la cuenta total y el botón Pagar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total a pagar:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$$formattedTotal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E88E5)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { /* Acción al presionar Pagar */ },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Pagar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun myGargeScreen(
    vehicles: List<Vehicle>,
    onAddVehicle: (
        brand: String,
        model: String,
        year: Int,
        engine: String,
        plate: String,
        mileage: String,
        notes: String
    ) -> Unit,
    onDeleteVehicle: (Int) -> Unit,
    onUpdateNotes: (vehicleId: Int, newNotes: String) -> Unit,
    onUpdateMileage: (vehicleId: Int, newMileage: String) -> Unit // <- Agregar parámetro
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🚗 Mi Garaje",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Control personal de tus vehículos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { showDialog = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ Agregar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (vehicles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aún no has registrado ningún vehículo 🚘\nPresiona '+ Agregar' para comenzar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(vehicles) { vehicle ->
                    VehicleCard(
                        vehicle = vehicle,
                        onDelete = { onDeleteVehicle(vehicle.id) },
                        onUpdateNotes = { newNotes -> onUpdateNotes(vehicle.id, newNotes) },
                        onUpdateMileage = { newMileage -> onUpdateMileage(vehicle.id, newMileage) } // <- Pasar al card
                    )
                }
            }
        }
    }

    if (showDialog) {
        AddVehicleDialog(
            onDismiss = { showDialog = false },
            onVehicleAdded = { brand, model, year, engine, plate, mileage, notes ->
                onAddVehicle(brand, model, year, engine, plate, mileage, notes)
            }
        )
    }
}

@Composable
fun DiagnosticScreen(
    onNavigateToCategory: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf("Testigos") } // "Testigos" o "Síntomas"
    var selectedSeverity by remember { mutableStateOf("Todos") }

    // Filtrado de luces por severidad
    val filteredLights = when (selectedSeverity) {
        "Críticos 🔴" -> dashboardLightList.filter { it.severity.contains("Rojo") }
        "Advertencias 🟡" -> dashboardLightList.filter { it.severity.contains("Amarillo") }
        else -> dashboardLightList
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Encabezado
        Text(
            text = "🩺 Guía de Diagnóstico",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Identifica fallas y luces del tablero en segundos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Banner de Alerta Crítica SOS
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🚨", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "¿Luz Roja Encendida?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Si una luz roja parpadea, detén el auto y apaga el motor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selector de Pestañas (Testigos Tablero vs Síntomas)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedTab = "Testigos" },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "Testigos") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedTab == "Testigos") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("💡 Testigos Tablero", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { selectedTab = "Síntomas" },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "Síntomas") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedTab == "Síntomas") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("🔊 Síntomas / Ruidos", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // MODO 1: TESTIGOS DEL TABLERO
        if (selectedTab == "Testigos") {
            val severities = listOf("Todos", "Críticos 🔴", "Advertencias 🟡")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(severities) { filter ->
                    val isSelected = selectedSeverity == filter
                    Card(
                        onClick = { selectedSeverity = filter },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = filter,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredLights) { light ->
                    DashboardLightCard(
                        light = light,
                        onShopRelated = { onNavigateToCategory(light.relatedCategory) }
                    )
                }
            }
        }
        // MODO 2: SÍNTOMAS Y RUIDOS
        else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(symptomList) { symptom ->
                    SymptomCard(symptom = symptom)
                }
            }
        }
    }
}

@Composable
fun DashboardLightCard(
    light: DashboardLight,
    onShopRelated: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = light.icon, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = light.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = if (light.severity.contains("Rojo")) Color(0xFFFFDADA) else Color(0xFFFFECC0),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = light.severity,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (light.severity.contains("Rojo")) Color(0xFFB91C1C) else Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (expanded) "Ocultar 🔼" else "Ver más 🔽")
                }
            }

            Text(
                text = light.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🛠️ Acción Recomendada:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = light.recommendedAction,
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (light.relatedCategory.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onShopRelated,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Ver repuestos de ${light.relatedCategory} 🛒")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SymptomCard(symptom: Symptom) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = symptom.emoji, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = symptom.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "🔎 Causa probable: ${symptom.probableCause}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "💡 Solución: ${symptom.solution}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    onDelete: () -> Unit,
    onUpdateNotes: (String) -> Unit,
    onUpdateMileage: (String) -> Unit // <- Recibe la acción para actualizar kilometraje
) {
    var isEditingNotes by remember { mutableStateOf(false) }
    var editedNotesText by remember(vehicle.notes) { mutableStateOf(vehicle.notes) }

    // Estado para el diálogo de edición de kilometraje
    var showMileageDialog by remember { mutableStateOf(false) }
    var tempMileageText by remember(vehicle.mileage) { mutableStateOf(vehicle.mileage) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabecera: Ícono, Nombre del Auto y Botón Eliminar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "🚘", style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${vehicle.brand} ${vehicle.model}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Año ${vehicle.year} • Motor ${vehicle.engine}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("🗑️", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Datos rápidos: Placa y Kilometraje (Clickeable para editar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (vehicle.plate.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "🏷️ Placa: ${vehicle.plate}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // ETIQUETA DE KILOMETRAJE EDITABLE
                Surface(
                    onClick = { showMileageDialog = true }, // Abrir ventana flotante al hacer clic
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "📈 ${if (vehicle.mileage.isNotBlank()) vehicle.mileage else "Sin km"}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "✏️",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // --- SECCIÓN DE NOTAS PERSONALES (EDITABLE) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📝 Notas personales:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedButton(
                            onClick = {
                                if (isEditingNotes) {
                                    editedNotesText = vehicle.notes
                                }
                                isEditingNotes = !isEditingNotes
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (isEditingNotes) "Cancelar" else "✏️ Editar")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isEditingNotes) {
                        OutlinedTextField(
                            value = editedNotesText,
                            onValueChange = { editedNotesText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Escribe notas para este vehículo...") },
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                onUpdateNotes(editedNotesText)
                                isEditingNotes = false
                            },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Guardar nota")
                        }
                    } else {
                        Text(
                            text = if (vehicle.notes.isNotBlank()) vehicle.notes else "Sin notas registradas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // DIÁLOGO PARA EDITAR KILOMETRAJE
    if (showMileageDialog) {
        AlertDialog(
            onDismissRequest = { showMileageDialog = false },
            title = {
                Text(text = "📈 Actualizar Kilometraje", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ingresa el kilometraje actual del ${vehicle.brand} ${vehicle.model}:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = tempMileageText,
                        onValueChange = { tempMileageText = it },
                        label = { Text("Kilometraje (Ej: 130.000)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateMileage(tempMileageText)
                        showMileageDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showMileageDialog = false }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun CartItemCard(
    product: Product,
    cantidad: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Imagen del repuesto
            Image(
                painter = painterResource(id = product.imageRes),
                contentDescription = product.contentDescription,
                modifier = Modifier
                    .size(60.dp)
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Información del producto (Nombre y Precio unitario)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = product.price,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1E88E5),
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Controles de cantidad (+ / -)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDecrease,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("-")
                }

                Text(
                    text = cantidad.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = onIncrease,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    product: Product,
    cantidad: Int,
    onIncrase: () -> Unit,
    onDecrease: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Image(
                painter = painterResource(id = product.imageRes),
                contentDescription = product.contentDescription,
                modifier = Modifier.padding(8.dp),
                contentScale = ContentScale.Fit
            )
        }

        if (product.badge.isNotBlank()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = product.badge,
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    ),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }


    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Contenedor suave para la imagen


            Spacer(modifier = Modifier.height(12.dp))

            // Nombre del producto
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Descripción con tipografía ajustada
            Text(
                text = product.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⭐ ${product.rating}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Fila de Precio y Botón
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.price,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E88E5)
                )

                if (cantidad == 0) {
                    Button(
                        onClick = onIncrase
                    ) {
                        Text("Agregar")
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDecrease,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("-")
                        }
                        Text(
                            text = cantidad.toString(),
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedButton(
                            onClick = onIncrase,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("+")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBadge(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            // Cambia de color al estar seleccionada
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = category.emoji)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = category.nombre,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun AddVehicleDialog(
    onDismiss: () -> Unit,
    onVehicleAdded: (
        brand: String,
        model: String,
        year: Int,
        engine: String,
        plate: String,
        mileage: String,
        notes: String
    ) -> Unit
) {
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (brand.isNotBlank() && model.isNotBlank()) {
                        onVehicleAdded(
                            brand,
                            model,
                            year.toIntOrNull() ?: 2024,
                            engine.ifBlank { "1.6L" },
                            plate.uppercase(),
                            if (mileage.isNotBlank()) "$mileage km" else "No registrado",
                            notes
                        )
                        onDismiss()
                    }
                }
            ) {
                Text("Guardar Auto")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        title = {
            Text("🚘 Registrar Nuevo Vehículo", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marca (Ej: Chevrolet)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Modelo (Ej: Aveo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Año (2012)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = engine,
                        onValueChange = { engine = it },
                        label = { Text("Motor (1.4L)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = plate,
                        onValueChange = { plate = it },
                        label = { Text("Placa (ABC-123)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mileage,
                        onValueChange = { mileage = it },
                        label = { Text("Kilometraje (125.000)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas rápidas (Opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun EdMotorPreview() {
    EdMotorTheme {
        Surface {
            EdMotor()
        }
    }
}