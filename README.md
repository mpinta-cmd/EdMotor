# ⚙️ EdMotor Automotriz

## 🏢 Descripción del Negocio

**EdMotor Automotriz** es una solución tecnológica e integral diseñada para simplificar el mantenimiento, la gestión y el cuidado de los vehículos automotores. La plataforma combina en un solo ecosistema digital la comercialización de repuestos automotrices, la administración personal de vehículos y una herramienta interactiva de asistencia diagnóstica para conductores.

### 📌 Pilares del Negocio

* **🛒 Comercio de Repuestos y Autopartes:** Catálogo digital especializado en autopartes para diversas marcas del mercado, abarcando categorías como lubricantes, filtros, bujías, sistemas de refrigeración, baterías, iluminación, suspensión, dirección, motor y transmisión.
* **🚘 Gestión de Garaje Virtual ("Mi Garaje"):** Módulo de control que permite a los usuarios registrar y monitorear sus vehículos, administrando información como kilometraje, especificaciones del motor, placa y notas personales de mantenimiento.
* **🩺 Guía y Asistencia de Diagnóstico Técnico:** Sistema de orientación para conductores que ayuda a identificar el significado de las luces de advertencia (testigos) del tablero e interpretar síntomas o ruidos mecánicos comunes, conectando las fallas identificadas directamente con las categorías de repuestos requeridas para su reparación.


## 🎯 Objetivo de la Aplicación

El objetivo principal de **EdMotor Automotriz** es proporcionar a los conductores y propietarios de vehículos una herramienta móvil integral y centralizada que simplifique el mantenimiento automotriz, optimice la búsqueda y compra de repuestos, y mejore la seguridad vial mediante la identificación oportuna de fallas mecánicas.

### 📌 Objetivos Específicos

* **Facilitar la adquisición de repuestos:** Ofrecer un catálogo digital interactivo con soporte para búsqueda en tiempo real y filtrado por categorías especializadas, permitiendo a los usuarios seleccionar y gestionar sus compras de forma rápida y transparente.
* **Optimizar la gestión vehicular:** Permitir a los usuarios llevar un registro organizado de sus vehículos personales o de trabajo, controlando datos clave como kilometraje, especificaciones del motor, placa y notas de mantenimiento preventivo.
* **Asistir en el diagnóstico preventivo:** Guiar a los conductores en la interpretación de los testigos de advertencia del tablero y ruidos mecánicos comunes, ofreciendo recomendaciones inmediatas y vinculando directamente las fallas detectadas con los repuestos correspondientes.
* **Garantizar una experiencia de usuario moderna:** Implementar una interfaz nativa fluida, accesible e intuitiva desarrollada bajo los estándares de Jetpack Compose y Material Design 3.


## 📱 Descripción de las Pantallas Implementadas

La aplicación se compone de cuatro pantallas principales integradas mediante un contenedor `Scaffold` con navegación por barra inferior (`NavigationBar`) gestionada por `NavHost`:

---

### 1. 🏠 Pantalla de Inicio (`HomeScreen`)

**Descripción:**
Es la pantalla principal y catálogo de la tienda de repuestos. Incluye:
* Campo de búsqueda dinámico por texto (`OutlinedTextField`) en tiempo real.
* Filtro por categorías mediante chips horizontales interactivos (`LazyRow`).
* Lista de repuestos (`LazyColumn`) con tarjetas descriptivas (`ProductCard`) filtradas dinámicamente.
* Control de desplazamiento con `rememberLazyListState` y `LaunchedEffect` para reiniciar el scroll a la primera posición tras realizar una búsqueda o seleccionar una categoría.

**Código implementado:**
```kotlin
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
    // 1. Estado para controlar la posición del scroll de la lista
    val listState = rememberLazyListState()

    // 2. Efecto que detecta cuando cambia la categoría, la búsqueda o al abrir la pantalla
    LaunchedEffect(Unit, selectedCategory, searchQuery) {
        listState.scrollToItem(0)
    }

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
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "¡Hola! 👋 ",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "¿Que repuesto necesitas hoy? 🔧",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Campo de Búsqueda Dinámico
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
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

        // CATEGORIAS usando LazyRow
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

        // Recorrido de Productos con state asignado
        LazyColumn(
            state = listState,
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
```

### 🛒 Pantalla del Carrito (`CartScreen`)

**Descripción:**
Muestra y gestiona los repuestos seleccionados por el usuario. Sus características principales son:
* Filtrado en tiempo real de artículos con cantidad mayor a cero dentro del mapa de cantidades del carrito (`cartQuantity`).
* Lista deslizable (`LazyColumn`) compuesta por tarjetas descriptivas (`CartItemCard`) con controles interactivos para incrementar o decrementar la cantidad de unidades por repuesto.
* Cálculo automático del saldo total a pagar convirtiendo los valores monetarios en cadena a formato numérico y aplicando formato en pesos colombianos (COP - `es_CO`).
* Muestra un estado de vista vacía cuando no hay productos agregados ("Selecciona productos en la ventana de inicio 🛠️").
* Tarjeta inferior resumida con el total acumulado y botón destacado para procesar el pago.
* Control de desplazamiento con `rememberLazyListState` y `LaunchedEffect` para garantizar que la lista inicie desde la posición superior al abrir la pantalla.

**Código implementado:**
```kotlin
@Composable
@Composable
fun CartScreen(
    products: List<Product>,
    cartQuantity: Map<Int, Int>,
    onIncrease: (Int) -> Unit,
    onDecrease: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
    }

    val cartItems = products.filter { (cartQuantity[it.id] ?: 0) > 0 }

    fun parsePrice(priceStr: String): Double {
        return priceStr.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
    }

    val totalAmount = cartItems.sumOf { product ->
        val quantity = cartQuantity[product.id] ?: 0
        parsePrice(product.price) * quantity
    }

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
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                state = listState
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
```
### 🚗 Pantalla de Mi Garaje (`myGargeScreen`)

**Descripción:**
Permite la administración y control del garaje personal de vehículos del usuario. Sus características principales son:
* Lista deslizable (`LazyColumn`) compuesta por tarjetas de vehículos (`VehicleCard`) con información detallada como marca, modelo, año, cilindraje del motor, placa, kilometraje y notas personales.
* Botón de acción principal ("+ Agregar") que despliega un formulario flotante (`AddVehicleDialog`) para registrar nuevos automóviles.
* Actualización dinámica del kilometraje del vehículo mediante un cuadro de diálogo interactivo (`AlertDialog`).
* Campo de edición directa para guardar y modificar notas de mantenimiento preventivo por cada vehículo.
* Botón dedicado para la eliminación de vehículos de la lista.
* Estado de vista vacía cuando no existen vehículos registrados ("Aún no has registrado ningún vehículo 🚘\nPresiona '+ Agregar' para comenzar.").
* Control de desplazamiento con `rememberLazyListState` y `LaunchedEffect` para garantizar que la lista inicie desde la posición superior al abrir la pantalla.

**Código implementado:**
```kotlin
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
    onUpdateMileage: (vehicleId: Int, newMileage: String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
    }
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
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                items(vehicles) { vehicle ->
                    VehicleCard(
                        vehicle = vehicle,
                        onDelete = { onDeleteVehicle(vehicle.id) },
                        onUpdateNotes = { newNotes -> onUpdateNotes(vehicle.id, newNotes) },
                        onUpdateMileage = { newMileage -> onUpdateMileage(vehicle.id, newMileage) }
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
```

### 🩺 Pantalla de Diagnóstico (`DiagnosticScreen`)

**Descripción:**
Módulo de orientación técnica diseñado para asistir al conductor ante posibles fallas vehiculares[cite: 5]. Incluye:
* Banner de advertencia SOS destacado para alertas críticas de emergencia en carretera[cite: 5].
* Selector de pestañas para alternar entre la vista de **"Testigos Tablero"** y **"Síntomas / Ruidos"**[cite: 5].
* Filtro interactivo de testigos del tablero según su nivel de severidad (*Críticos 🔴* o *Advertencias 🟡*)[cite: 5].
* Botón de redirección automática hacia la pantalla de inicio filtrando los repuestos de la categoría vinculada al testigo[cite: 5].
* Módulo explicativo para síntomas y ruidos mecánicos con causa probable y solución sugerida[cite: 5].
* Control de desplazamiento con `rememberLazyListState` y `LaunchedEffect` para reiniciar la posición del scroll al cambiar de pestaña, aplicar un filtro o abrir la pantalla[cite: 5].

**Código implementado:**
```kotlin
@Composable
fun DiagnosticScreen(
    onNavigateToCategory: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf("Testigos") } // "Testigos" o "Síntomas"
    var selectedSeverity by remember { mutableStateOf("Todos") }

    // 1. Estado de scroll para la pantalla de diagnóstico
    val listState = rememberLazyListState()

    // 2. Efecto para reiniciar el scroll al cambiar de pestaña, filtro o al abrir la vista
    LaunchedEffect(Unit, selectedTab, selectedSeverity) {
        listState.scrollToItem(0)
    }

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

        // Banner SOS
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

        // Selector de Pestañas
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
                state = listState, // <-- 3. Asignamos el estado de scroll
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
                state = listState, // <-- 3. Asignamos el estado de scroll
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
```

## 🛠️ Explicación de las Funcionalidades de la Aplicación

La aplicación integra tres módulos principales: uno que expande la base trabajada en el aula y dos funcionalidades completamente nuevas diseñadas específicamente para el sector automotriz.

---

### 1. 🛒 Catálogo de Repuestos y Carrito de Compras *(Corresponde a lo trabajado en clase)*

* **Origen:** Basado e inspirado en la estructura base de la tienda virtual (`ElectroStoreApp`).
* **Descripción:** Es el módulo comercial de la aplicación. Permite a los usuarios explorar un catálogo de autopartes, realizar búsquedas en tiempo real por nombre y filtrar productos por categorías mediante un carrusel horizontal.
* **Mejoras y evolución respecto a clase:**
    * Se implementó un **filtrado doble dinámico** que combina simultáneamente el texto del buscador y la categoría seleccionada (funcionalidad que en la app de clase no estaba vinculada).
    * Se completó la pantalla del **Carrito de Compras (`CartScreen`)**, añadiendo la conversión de precios, el cálculo dinámico de la suma total y el formateo en moneda colombiana (COP).
    * Se integró el control de desplazamiento mediante `rememberLazyListState` y `LaunchedEffect` para reiniciar el scroll al cambiar de filtro o búsqueda.

---

### 2. 🚘 Gestión de Garaje Personal - "Mi Garaje" *(Primera funcionalidad nueva)*

* **Origen:** Funcionalidad totalmente nueva desarrollada para EdMotor Automotriz.
* **Descripción:** Es un sistema de administración vehicular que permite a cada conductor llevar el control de su flota o auto personal directamente desde la aplicación.
* **Características clave:**
    * **Registro de vehículos:** Formulario modal (`AddVehicleDialog`) para ingresar datos completos como marca, modelo, año, cilindrada del motor, placa, kilometraje inicial y notas.
    * **Actualización interactiva de kilometraje:** Permite hacer clic sobre el kilometraje de cualquier auto registrado para actualizarlo rápidamente a través de un diálogo flotante (`AlertDialog`).
    * **Notas mecánicas editables:** Espacio dinámico para guardar apuntes personales (ej. "Llantas infladas a 30 PSI", "Usar refrigerante verde").
    * **Gestión de lista:** Opción para eliminar vehículos del garaje.

---

### 3. 🩺 Guía de Diagnóstico Técnico y Testigos - "Diagnóstico" *(Segunda funcionalidad nueva)*

* **Origen:** Funcionalidad totalmente nueva desarrollada para EdMotor Automotriz.
* **Descripción:** Es un asistente de orientación técnica y preventiva diseñado para ayudar a los conductores a interpretar fallas en su vehículo antes de que se conviertan en averías graves.
* **Características clave:**
    * **Identificador de Testigos del Tablero:** Catálogo explicativo de luces del tablero organizado por niveles de severidad (*Críticos 🔴* y *Advertencias 🟡*), detallando el significado y la acción inmediata recomendada.
    * **Vinculación directa con la Tienda:** Cada tarjeta de testigo crítico incluye un botón interactivo que redirige al usuario a la pantalla de Inicio, filtrando automáticamente los repuestos de la categoría necesaria para solucionar dicha falla.
    * **Buscador de Síntomas y Ruidos:** Sección dedicada a fallas comunes (ej. chillidos al frenar, vibración al acelerar), explicando la causa probable y la solución sugerida.
    * **Banner de Alerta SOS:** Componente destacado de advertencia visual para emergencias en carretera.

---

### 🏠 1. Pantalla de Inicio
![Pantalla de Inicio](C:\Users\user\AndroidStudioProjects\EdMotor\screenshots\img.png)

### 🛒 2. Carrito de Compras
![Carrito de Compras](C:\Users\user\AndroidStudioProjects\EdMotor\screenshots\img_1.png)

### 🚗 3. Mi Garaje
![Mi Garaje](C:\Users\user\AndroidStudioProjects\EdMotor\screenshots\img_2.png)

### 🩺 4. Guía de Diagnóstico
![Guía de Diagnóstico](C:\Users\user\AndroidStudioProjects\EdMotor\screenshots\img_3.png) 


## 🚀 Instrucciones Básicas para Ejecutar el Proyecto

Sigue esta guía paso a paso para compilar y ejecutar **EdMotor Automotriz** en tu entorno local mediante Android Studio.

---

### 📋 Prerrequisitos

Antes de iniciar, asegúrate de contar con los siguientes elementos configurados en tu equipo:

* **Android Studio:** Versión Iguana, Jellyfish, Ladybug o superior.
* **Java Development Kit (JDK):** JDK 17 o superior (configurado por defecto en Android Studio).
* **Configuración del SDK de Android:**
  * **Compile SDK / Target SDK:** API 34 o 35 (Android 14 / 15).
  * **Minimum SDK:** API 24 (Android 7.0 Nougat).
* **Dispositivo de ejecución:**
  * Emulador de Android (AVD) creado con API 24 o superior, **o**
  * Dispositivo físico Android conectado por USB con la **Depuración por USB** activada.

---

### 💻 Pasos para la Configuración y Compilación

#### Paso 1: Clonar o Descargar el Proyecto
Obtén el código fuente clonando el repositorio desde GitHub o descargando el archivo ZIP descomprimido en tu equipo:

```bash
git clone https://github.com/mpinta-cmd/EdMotor