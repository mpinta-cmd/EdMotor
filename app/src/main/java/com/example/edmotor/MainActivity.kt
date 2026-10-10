package com.example.edmotor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.edmotor.ui.theme.EdMotorTheme

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

val productList = listOf(
    Product(
        name = "Aceite Mobil 20W-50",
        description = "Aceite lubricante multigrado de alta viscosidad diseñado para motores",
        price = "$203.900",
        imageRes = R.drawable.mobil_20_w_50,
        contentDescription = "Aceite mobil 20 w 50",
        rating = 4.5
    ),
    Product(
        name = "Aceite Havoline 20w-50",
        description = "Aceite lubricante multigrado mineral de alta viscosidad diseñado para motores a gasolina",
        price = "$87.200",
        imageRes = R.drawable.havoline_20_w_50,
        contentDescription = "Aceite havoline 20 w 50",
        rating = 4.6
    ),
    Product(
        name = "Filtro de aceite A-111",
        description = "Diseñado para retener impurezas y partículas en el lubricante",
        price = "$20.140",
        imageRes = R.drawable.filtro_a,
        contentDescription = "Filtro de aceite A 111",
        rating = 4.7
    ),
    Product(
        name = "Filtro de aire Aveo",
        description = "Encargado de retener el polvo e impurezas del aire externo antes de que ingresen al motor.",
        price = "$20.000",
        imageRes = R.drawable.filtro_de_aire_aveo,
        contentDescription = "Filtro de aire para aveo",
        rating = 4.6
    ),
    Product(
        name = "Bujias NGK x 4",
        description = "Componentes del sistema de encendido diseñados para generar la chispa eléctrica",
        price = "$14.000",
        imageRes = R.drawable.bujias_ngk,
        contentDescription = "Bujías NGK 4 unidades",
        rating = 5.0
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdMotor() {
    // Algo relacionado con la barra de busqueda
    var searchQuery by remember { mutableStateOf("") }

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

    ) { innerPadding ->

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
            //CATEGORIAS usando LazyRow
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
}

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
}

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


@Preview(showBackground = true)
@Composable
fun EdMotorPreview() {
    EdMotorTheme {
        Surface {
            EdMotor()
        }
    }
}