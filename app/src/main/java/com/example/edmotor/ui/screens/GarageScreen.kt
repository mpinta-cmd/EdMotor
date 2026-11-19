package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Vehicle
import com.example.edmotor.ui.components.AddVehicleBottomSheet
import com.example.edmotor.ui.components.VehicleCard

@Composable
fun GarageScreen(
    vehicles: List<Vehicle>,
    onAddVehicle: (
        brand: String,
        model: String,
        year: Int,
        engine: String,
        plate: String,
        mileage: String,
        notes: String,
        onResult: (Result<Long>) -> Unit
    ) -> Unit,
    onDeleteVehicle: (Int) -> Unit,
    onUpdateNotes: (vehicleId: Int, newNotes: String) -> Unit,
    onUpdateMileage: (vehicleId: Int, newMileage: String) -> Unit
) {
    var showAddVehicleSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Encabezado discreto: deja el protagonismo al vehículo.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "ED MOTOR  /  ESPACIO PERSONAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Mi garaje",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tu vehículo, su información y sus cuidados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { showAddVehicleSheet = true },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(
                    horizontal = 13.dp,
                    vertical = 10.dp
                )
            ) {
                Text(
                    text = "+ Agregar",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (vehicles.isEmpty()) {
            EmptyGarageState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                onAddVehicle = { showAddVehicleSheet = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Reduce el área visible cuando aparece el teclado.
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "garage_list_heading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (vehicles.size == 1) "TU VEHÍCULO" else "TUS VEHÍCULOS",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (vehicles.size == 1) {
                                    "Un espacio reservado para tu carro"
                                } else {
                                    "Toda tu información en un solo lugar"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = vehicles.size.toString(),
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 7.dp
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                items(
                    items = vehicles,
                    key = { vehicle -> vehicle.id }
                ) { vehicle ->
                    VehicleCard(
                        vehicle = vehicle,
                        onDelete = { onDeleteVehicle(vehicle.id) },
                        onUpdateNotes = { newNotes ->
                            onUpdateNotes(vehicle.id, newNotes)
                        },
                        onUpdateMileage = { newMileage ->
                            onUpdateMileage(vehicle.id, newMileage)
                        }
                    )
                }

                item(key = "garage_footer") {
                    Text(
                        text = "Tu garaje es privado y está organizado para acompañarte en el cuidado de tu vehículo.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showAddVehicleSheet) {
        AddVehicleBottomSheet(
            onDismiss = { showAddVehicleSheet = false },
            onVehicleAdded = onAddVehicle
        )
    }
}

@Composable
private fun EmptyGarageState(
    modifier: Modifier = Modifier,
    onAddVehicle: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "ED",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Este espacio es tuyo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Añade tu vehículo para tener a mano sus datos, kilometraje y notas de mantenimiento. Puedes registrar uno solo; no necesitas llenar ningún espacio adicional.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onAddVehicle,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(
                        horizontal = 22.dp,
                        vertical = 13.dp
                    )
                ) {
                    Text(
                        text = "Registrar mi vehículo",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Sin espacios vacíos ni registros innecesarios.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
