package com.example.edmotor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.edmotor.navigation.Routes
import com.example.edmotor.ui.components.EdMotorBottomBar
import com.example.edmotor.ui.theme.AppThemeMode
import com.example.edmotor.ui.theme.EdMotorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdMotorBottomBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pendingCountsFitTheirTabsWithLargeTextAndUpdate() {
        var pendingOrders by mutableIntStateOf(12)
        var pendingAppointments by mutableIntStateOf(100)

        compose.setContent {
            EdMotorTheme(themeMode = AppThemeMode.DARK) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Box(Modifier.width(320.dp)) {
                        EdMotorBottomBar(
                            currentRoute = Routes.APPOINTMENTS,
                            pendingOrderCount = pendingOrders,
                            pendingAppointmentCount = pendingAppointments,
                            onNavigate = {}
                        )
                    }
                }
            }
        }

        assertBadgeFitsTab("12", Routes.PROFILE)
        assertBadgeFitsTab("99+", Routes.APPOINTMENTS)
        assertPendingDescription(Routes.PROFILE, "12 pedidos pendientes")
        assertPendingDescription(Routes.APPOINTMENTS, "100 citas pendientes")

        compose.runOnIdle {
            pendingOrders = 0
            pendingAppointments = 1
        }

        compose.onNodeWithText("12", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("99+", useUnmergedTree = true).assertDoesNotExist()
        assertBadgeFitsTab("1", Routes.APPOINTMENTS)
        assertPendingDescription(Routes.APPOINTMENTS, "1 cita pendiente")
    }

    @Test
    fun allTabsNavigateAndHighlightTheActiveDestination() {
        var currentRoute by mutableStateOf(Routes.ORDER_MANAGEMENT)
        val visited = mutableListOf<String>()

        compose.setContent {
            EdMotorTheme(themeMode = AppThemeMode.LIGHT) {
                EdMotorBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        visited += route
                        currentRoute = route
                    }
                )
            }
        }

        compose.onNodeWithText(Routes.PROFILE).assertIsSelected()
        val destinations = listOf(
            Routes.PROFILE to "Mi Garaje",
            Routes.APPOINTMENTS to "Citas",
            Routes.DASHBOARD to "Testigos",
            Routes.HOME to "Inicio"
        )
        destinations.forEach { (_, label) ->
            compose.onNodeWithText(label).performClick()
            compose.onNodeWithText(label).assertIsSelected()
        }
        compose.runOnIdle { assertEquals(destinations.map { it.first }, visited) }
    }

    private fun assertBadgeFitsTab(count: String, label: String) {
        val badge = compose.onNodeWithText(count, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        badge.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals("El contador $count ocupa más de una línea", 1, layout.lineCount)
        assertEquals("Al contador $count le faltan caracteres", count.length, layout.getLineEnd(0, visibleEnd = true))
        assertTrue("El contador $count rebasa su cápsula", layout.getLineRight(0) <= layout.size.width + 1f)

        val badgeBounds = badge.getUnclippedBoundsInRoot()
        val tabBounds = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
        assertTrue(badgeBounds.left >= tabBounds.left && badgeBounds.right <= tabBounds.right)
        assertTrue(badgeBounds.top >= tabBounds.top && badgeBounds.bottom <= tabBounds.bottom)
    }

    private fun assertPendingDescription(label: String, description: String) {
        compose.onNodeWithText(label).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)
        )
    }
}
