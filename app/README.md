# ⚙️ EdMotor - Aplicación Móvil Automotriz

## 📌 Introducción

**EdMotor** es una aplicación móvil nativa desarrollada para la plataforma **Android**, diseñada para la exploración, búsqueda y compra de repuestos y autopartes. La aplicación ofrece una experiencia de usuario moderna, fluida e intuitiva, orientada a conductores, talleres y entusiastas de la mecánica que necesitan encontrar repuestos de forma rápida y confiable.

El proyecto está desarrollado utilizando las últimas herramientas recomendadas por Google para el ecosistema Android, empleando **Kotlin** como lenguaje principal y **Jetpack Compose** integrado con **Material Design 3** para la creación de una interfaz de usuario declarativa, responsiva y atractiva.

---

## 📱 Clase Principal (`MainActivity`)

La clase `MainActivity` actúa como el **punto de entrada principal** de la aplicación dentro del sistema operativo Android. Su función central es gestionar el ciclo de vida de la actividad e inicializar el árbol de componentes de la interfaz gráfica mediante Jetpack Compose.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdMotorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    EdMotor()
                }
            }
        }
    }
} 
```
---

## Data class

En este proyecto tambien se implementaron dos data class:

```kotlin
data class Product(
val name: String,
val description: String,
val price: String,
@DrawableRes val imageRes: Int,
val contentDescription: String = name,
val rating: Double
)
data class Category(
val emoji: String,
val nombre: String
) 
```

---

## 🎨 Pantalla Principal (`EdMotor`)

Esta sección de código se denomina **Función Composable Principal** (o *Main Screen / UI Layout*). En Jetpack Compose, las funciones anotadas con `@Composable` son los bloques de construcción fundamentales para definir y renderizar la interfaz de usuario de forma declarativa.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdMotor() {
    // ...
}
```

## 🏗️ Estructura de Navegación y Marco Principal (`Scaffold`)

El componente `Scaffold` sirve como la estructura o esqueleto base de **Material Design 3** para la pantalla principal. Su función es organizar automáticamente los elementos globales de la interfaz (la barra superior y la barra de navegación inferior), reservando el espacio central para el contenido dinámico de la aplicación.

```kotlin
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
                selected = true,
                onClick = {},
                icon = {
                    Text("\uD83C\uDFE0")
                },
                label = {
                    Text("Inicio")
                }
            )

            NavigationBarItem(
                selected = false,
                onClick = {},
                icon = {
                    Text("\uD83C\uDFCE\uFE0F")
                },
                label = {
                    Text("Carrito")
                }
            )

            NavigationBarItem(
                selected = false,
                onClick = {},
                icon = {
                    Text("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83D\uDD27")
                },
                label = {
                    Text("Perfil")
                }
            )
        }
    }
)
```

## 📦 Contenido Principal del Scaffold (`contentBody`)

Esta sección representa el **cuerpo central** (*content lambda*) inyectado dentro del `Scaffold`. Contiene la jerarquía visual de la pantalla de inicio, organizada mediante una `Column` principal que administra el encabezado de bienvenida, el buscador de repuestos, el listado horizontal de categorías y la lista vertical de productos destacados.

```kotlin
{ innerPadding ->

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
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
            onValueChange = { searchQuery = it },
            label = { Text("Buscar repuestos...") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // CATEGORIAS usando LazyRow
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

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categoryList.size) { category ->
                CategoryBadge(
                    category = categoryList[category]
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
            items(productList.size) { product ->
                ProductCard(
                    product = productList[product]
                )
            }
        }
    }
}
```

---

## 🏷️ Componente Tarjeta de Producto (`ProductCard`)

El composable `ProductCard` es un componente visual reutilizable encargado de renderizar la información detallada de un repuesto individual. Combina una vista previa visual de la imagen con una etiqueta de oferta, descripción textual, valoración en estrellas, precio formateado y un botón de acción rápida para agregar al carrito.

```kotlin
@Composable
fun ProductCard(
    product: Product
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

        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Text(
                text = "Oferta",
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 4.dp
                ),
                style = MaterialTheme.typography.labelSmall
            )
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

                Button(
                    onClick = { /* Acción al agregar */ },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFEB3B), // Color de fondo (Amarillo Material)
                        contentColor = Color.Black          // Color del texto/ícono dentro del botón
                    )
                ) {
                    Text("Agregar")
                }
            }
        }
    }
}
```
## 🖼️ Contenedor de Imagen y Badge de Oferta

Este bloque de código define la sección visual superior del producto. Utiliza un contenedor `Box` para superponer una insignia flotante de **"Oferta"** directamente sobre el marco que despliega la imagen del repuesto.

```kotlin
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

    Card(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
    ) {
        Text(
            text = "Oferta",
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 4.dp
            ),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
```
## 💳 Tarjeta de Información e Interacción del Producto (`Card`)

Este bloque representa el cuerpo informativo y comercial del repuesto. Utiliza un componente `Card` elevado con esquinas redondeadas para estructurar jerárquicamente el nombre, la descripción, la calificación del producto, el precio y el botón de acción rápida para añadir al carrito.

```kotlin
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

            Button(
                onClick = { /* Acción al agregar */ },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFEB3B), // Color de fondo (Amarillo Material)
                    contentColor = Color.Black          // Color del texto/ícono dentro del botón
                )
            ) {
                Text("Agregar")
            }
        }
    }
}
```
---

## 🏷️ Componente Insignia de Categoría (`CategoryBadge`)

El composable `CategoryBadge` es un componente visual reutilizable (tipo *chip* o *badge*) diseñado para representar de forma compacta cada una de las categorías de repuestos en la aplicación. Organiza horizontalmente un emoji representativo y el nombre de la categoría dentro de una tarjeta contenedora.

```kotlin
@Composable
fun CategoryBadge(category: Category) {
    Card {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.emoji
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = category.nombre,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
```

---

## 👁️ Vista Previa en Tiempo de Diseño (`EdMotorPreview`)

El composable `EdMotorPreview` es una función de entorno de desarrollo cuya única responsabilidad es **renderizar una vista previa interactiva** de la pantalla principal directamente en la ventana de diseño (*Design / Split View*) de Android Studio, sin necesidad de compilar o ejecutar la aplicación en un emulador o dispositivo físico.

```kotlin
@Preview(showBackground = true)
@Composable
fun EdMotorPreview() {
    EdMotorTheme {
        Surface {
            EdMotor()
        }
    }
}
```


---