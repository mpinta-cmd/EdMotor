package com.example.edmotor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.ui.screens.ClientGarageScreen
import com.example.edmotor.ui.screens.formatAppointmentDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientGarageScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsCompletionInBothTabsAndCallsThePickupActions() {
        val completed = Appointment(
            id = 41L,
            clientAuthId = "cliente-41",
            clientName = "Andrea",
            clientUsername = "andrea",
            clientPhone = null,
            vehicleBrand = "Toyota",
            vehicleModel = "Corolla",
            vehicleYear = 2021,
            vehiclePlate = "",
            scheduledAtMillis = 1_760_000_000_000L,
            description = "Revisión general",
            status = AppointmentStatus.COMPLETED,
            createdAtMillis = 1_759_900_000_000L,
            updatedAtMillis = 1_760_003_600_000L
        )
        var notice by mutableStateOf<Appointment?>(completed.copy(status = AppointmentStatus.CONFIRMED))
        var viewed: Appointment? = null
        var dismissed: Appointment? = null

        compose.setContent {
            MaterialTheme {
                ClientGarageScreen(
                    profileName = "Andrea",
                    vehicles = emptyList(),
                    orders = emptyList(),
                    onAddVehicle = { _, _, _, _, _, _, _, _ -> },
                    onVehicleClick = {},
                    onLogout = {},
                    pickupNotice = notice,
                    onViewPickup = { viewed = it },
                    onDismissPickup = {
                        dismissed = it
                        notice = null
                    }
                )
            }
        }

        val banner = compose.onNodeWithText("Listo para recoger")
        banner.assertDoesNotExist()
        compose.runOnIdle { notice = completed.copy(updatedAtMillis = 0L) }
        banner.assertDoesNotExist()

        compose.runOnIdle { notice = completed }
        banner.assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        val completion = formatAppointmentDateTime(completed.updatedAtMillis)
        compose.onNodeWithText("Terminada el $completion").assertIsDisplayed()

        compose.onNodeWithText("Mis compras").performClick().assertIsSelected()
        banner.assertIsDisplayed()
        compose.onNodeWithText("Ver cita").performClick()
        compose.onNodeWithText("Entendido").performClick()

        banner.assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(completed, viewed)
            assertEquals(completed, dismissed)
        }
    }
}
