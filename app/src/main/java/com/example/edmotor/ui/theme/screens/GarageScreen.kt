
package com.example.edmotor.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Vehicle
import com.example.edmotor.ui.theme.components.AddVehicleBottomSheet
import com.example.edmotor.ui.theme.components.VehicleCard

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
        notes: String
    ) -> Unit,
    onDeleteVehicle: (Int) -> Unit,
    onUpdateNotes: (vehicleId: Int, newNotes: String) -> Unit,
    onUpdateMileage: (vehicleId: Int, newMileage: String) -> Unit
) {
    val listState = rememberLazyListState()

    var showAddVehicleSheet by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ENCABEZADO PREMIUM
            item(key = "garage_header") {
                GarageHeroCard(
                    vehicleCount = vehicles.size,
                    onAddVehicle = {
                        showAddVehicleSheet = true
                    }
                )
            }

            // TÍTULO DE LA LISTA
            item(key = "garage_section_title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Tu colección",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Todos tus vehículos en un solo lugar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${vehicles.size}",
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 9.dp
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // ESTADO VACÍO
            if (vehicles.isEmpty()) {
                item(key = "garage_empty") {
                    EmptyGarageCard(
                        onAddVehicle = {
                            showAddVehicleSheet = true
                        }
                    )
                }
            } else {
                // LISTA DE VEHÍCULOS
                items(
                    items = vehicles,
                    key = { vehicle -> vehicle.id }
                ) { vehicle ->
                    VehicleCard(
                        vehicle = vehicle,
                        onDelete = {
                            onDeleteVehicle(vehicle.id)
                        },
                        onUpdateNotes = { newNotes ->
                            onUpdateNotes(
                                vehicle.id,
                                newNotes
                            )
                        },
                        onUpdateMileage = { newMileage ->
                            onUpdateMileage(
                                vehicle.id,
                                newMileage
                            )
                        }
                    )
                }
            }
        }
    }

    // MENÚ INFERIOR PARA AGREGAR VEHÍCULOS
    if (showAddVehicleSheet) {
        AddVehicleBottomSheet(
            onDismiss = {
                showAddVehicleSheet = false
            },
            onVehicleAdded = {
                    brand,
                    model,
                    year,
                    engine,
                    plate,
                    mileage,
                    notes ->

                onAddVehicle(
                    brand,
                    model,
                    year,
                    engine,
                    plate,
                    mileage,
                    notes
                )
            }
        )
    }
}

@Composable
private fun GarageHeroCard(
    vehicleCount: Int,
    onAddVehicle: () -> Unit
) {
    val graphite = Color(0xFF1E293B)
    val electricBlue = Color(0xFF0066FF)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = graphite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF334155)
                ) {
                    Text(
                        text = "🚘",
                        modifier = Modifier.padding(13.dp),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "EDMOTOR / GARAGE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF93C5FD)
                    )

                    Text(
                        text = "Tu garaje, bajo control",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Text(
                text = "Toda la información de tus vehículos, " +
                        "organizada en un solo lugar.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.16f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = vehicleCount.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Text(
                        text = if (vehicleCount == 1) {
                            "Vehículo registrado"
                        } else {
                            "Vehículos registrados"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Button(
                    onClick = onAddVehicle,
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = electricBlue,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 15.dp,
                        vertical = 12.dp
                    )
                ) {
                    Text(
                        text = "+ Agregar",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGarageCard(
    onAddVehicle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "🚗",
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.headlineLarge
                )
            }

            Text(
                text = "Tu garaje comienza aquí",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Registra tu primer vehículo para guardar " +
                        "su placa, kilometraje y notas de mantenimiento.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onAddVehicle,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) {
                Text("Registrar mi primer vehículo")
            }
        }
    }
}
