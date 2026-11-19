package com.example.edmotor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.edmotor.R
import com.example.edmotor.ui.components.ProductImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductImageTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun missingOrInvalidCatalogResourcesShowThePlaceholder() {
        compose.setContent {
            MaterialTheme {
                Column {
                    ProductImage(null, Int.MAX_VALUE, null, Modifier.size(96.dp))
                    ProductImage(null, R.string.app_name, null, Modifier.size(96.dp))
                }
            }
        }

        compose.onAllNodesWithText("Sin imagen").assertCountEquals(2)
    }
}
