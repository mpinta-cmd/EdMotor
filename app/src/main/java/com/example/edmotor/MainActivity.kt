package com.example.edmotor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.edmotor.ui.theme.EdMotorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        //Aplicacion como tal solo se va a mostrar lo que este dentro de este metodo
        setContent {
            EdMotorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    EdMotor()
                }
                EdMotor()
            }
        }
    }
}

data class Product(
    val name: String,
    val description: String,
    val price: String,
    @DrawableRes val imageRes: Int,
    val contentDescription: String = name
)

// 2. Colección de datos (Lista con la información de los repuestos)
val productList = listOf(
    Product(
        name = "Aceite Mobil 20W-50",
        description = "Aceite lubricante multigrado de alta viscosidad diseñado para motores",
        price = "$203.900",
        imageRes = R.drawable.mobil_20_w_50,
        contentDescription = "Aceite mobil 20 w 50"
    ),
    Product(
        name = "Aceite Havoline 20w-50",
        description = "Aceite lubricante multigrado mineral de alta viscosidad diseñado para motores a gasolina",
        price = "$87.200",
        imageRes = R.drawable.havoline_20_w_50,
        contentDescription = "Aceite havoline 20 w 50"
    ),
    Product(
        name = "Filtro de aceite A-111",
        description = "Diseñado para retener impurezas y partículas en el lubricante",
        price = "$20.140",
        imageRes = R.drawable.filtro_a,
        contentDescription = "Filtro de aceite A 111"
    ),
    Product(
        name = "Filtro de aire Aveo",
        description = "Encargado de retener el polvo e impurezas del aire externo antes de que ingresen al motor.",
        price = "$20.000",
        imageRes = R.drawable.filtro_de_aire_aveo,
        contentDescription = "Filtro de aire para aveo"
    ),
    Product(
        name = "Bujias NGK x 4",
        description = "Componentes del sistema de encendido diseñados para generar la chispa eléctrica",
        price = "$14.000",
        imageRes = R.drawable.bujias_ngk,
        contentDescription = "Bujías NGK 4 unidades"
    )
)

@Composable
fun EdMotor() {
    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        //Primer Texto

        Text(
            text= "⚙\uFE0F EdMotor Automotriz \uD83E\uDDF0",
            style= MaterialTheme.typography.headlineMedium
        )

        // Primer espacio
        Spacer(modifier = Modifier.height(16.dp))

        //Segundo Texto
        Text(text= "Mecanica automotriz y distribuidora de repuestos automotrices",
            style= MaterialTheme.typography.bodyLarge)

        //Espacio entre el segundo texto y el outline
        Spacer(modifier = Modifier.height(16.dp))

        //Campo de texto
        OutlinedTextField(
            value= " ",
            onValueChange = {},
            label = {
                Text (" Buscar repuestos " )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text="Repuestos Destacados",
            style= MaterialTheme.typography.bodyMedium
        )
        for (product in productList) {
            ProductCard(product)
            Spacer(modifier = Modifier.height(16.dp))
        }

    }
}

// Lo creamos aqui el componente reutilizable
@Composable
fun ProductCard(
   Product: Product,
){
    //Card simplemente es un contenedor
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        )  {
            Image(
                painter = painterResource(
                    id = Product.imageRes
                ),
                contentDescription = Product.contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text= Product.name,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text= Product.description,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text (
                    text=Product.price,
                    style= MaterialTheme.typography.titleMedium
                )

                Button(
                    onClick = {}
                ) {
                    Text("Agregar")
                }

            }
        }

    }
}

@Preview(showBackground = true)
@Composable
fun EdMotorPreview() {
    EdMotorTheme {
        EdMotor()
    }
}