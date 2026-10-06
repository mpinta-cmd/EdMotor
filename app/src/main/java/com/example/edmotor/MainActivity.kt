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
                ) { }
                EdMotor()
            }
        }
    }
}

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
        Spacer(modifier = Modifier.height(16.dp))
        //Se crea el componente
        ProductCard(
            "Aceite Mobil 20W-50",
            "Aceite mobil 20 w 50",
            "Aceite lubricante multigrado de alta viscosidad diseñado para motores",
            "$203.900",
            R.drawable.mobil_20_w_50
        )

        Spacer(modifier = Modifier.height(16.dp))
        ProductCard(
            "Aceite Havoline 20w-50",
            "Aceite havoline 20 w 50",
            "Aceite lubricante multigrado mineral de alta viscosidad diseñado para motores a gasolina",
            "$87.200",
            R.drawable.havoline_20_w_50
        )
        Spacer(modifier = Modifier.height(16.dp))
        // R.drawable.filtro_a is resolved after rebuilding the project resources
        ProductCard(
            "Filtro de aceite A-111",
            "Filtro de aceite A 111",
            "Diseñado para retener impurezas y partículas en el lubricante",
            "$20.140",
            R.drawable.filtro_a
            )

        Spacer(modifier = Modifier.height(16.dp))
        ProductCard(
            "Filtro de aire Aveo",
            "Filtro de aire para aveo",
            "Encargado de retener el polvo e impurezas del aire externo antes de que ingresen al motor.",
            "$20.000",
            R.drawable.filtro_de_aire_aveo
            )
        Spacer(modifier = Modifier.height(16.dp))
        ProductCard(
            "Bujias NGK x 4",
            "Bujiar NGK 4 unidades",
            "componentes del sistema de encendido diseñados para generar la chispa eléctrica",
            "$14.000",
            R.drawable.bujias_ngk
        )
    }
}

// Lo creamos aqui el componente reutilizable
@Composable
fun ProductCard(
    name: String,
    content_description: String,
    descripcion: String,
    price: String,
    @DrawableRes imageRes: Int
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
                    id = imageRes
                ),
                contentDescription = content_description,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text= name,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text= descripcion,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text (
                    text=price,
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