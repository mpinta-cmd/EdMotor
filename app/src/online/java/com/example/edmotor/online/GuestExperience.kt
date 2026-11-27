package com.example.edmotor.online

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.ui.screens.GarageIconTile

@Composable
internal fun GuestGarageScreen(onCreateAccount: () -> Unit, onBrowseCatalog: () -> Unit) {
    GuestPage {
        OnlineSectionTitle("Mi garaje", Icons.Outlined.DirectionsCar)
        GuestActionCard(
            title = "Dale un lugar a tu carro",
            description = "Crea tu cuenta para guardar tu vehículo y consultar lo que se le ha hecho en el taller.",
            actionLabel = "Crear mi garaje",
            onAction = onCreateAccount
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Tu ficha de vehículo",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                GarageFeature(Icons.Outlined.DirectionsCar, "Vehículos", "Marca, modelo, año y placa")
                GarageFeature(Icons.Outlined.CalendarMonth, "Próxima visita", "Fecha y estado de tu cita")
                GarageFeature(Icons.Outlined.History, "Bitácora del taller", "Informes de tus mantenimientos")
            }
        }
        OutlinedButton(onClick = onBrowseCatalog, modifier = Modifier.fillMaxWidth()) {
            Text("Explorar repuestos")
        }
    }
}

@Composable
internal fun GuestAppointmentsScreen(onAuthenticate: () -> Unit) {
    GuestPage {
        OnlineSectionTitle("Citas", Icons.Outlined.CalendarMonth)
        GuestActionCard(
            title = "Agenda una visita al taller",
            description = "Con tu perfil puedes elegir un vehículo y una fecha, indicar el motivo y consultar la confirmación del taller.",
            actionLabel = "Solicitar una cita",
            onAction = onAuthenticate
        )
    }
}

@Composable
private fun GuestPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun GuestActionCard(title: String, description: String, actionLabel: String, onAction: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
        }
    }
}

@Composable
private fun GarageFeature(icon: ImageVector, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        GarageIconTile(icon)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
