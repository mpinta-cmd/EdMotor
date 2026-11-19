package com.example.edmotor.data

import com.example.edmotor.model.Category
import com.example.edmotor.model.categoryKey

const val FAVORITES_FILTER_KEY = "filters:favorites"

fun catalogCategoryFilterKey(name: String): String = "category:${categoryKey(name)}"

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
