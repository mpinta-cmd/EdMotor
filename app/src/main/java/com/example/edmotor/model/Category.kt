package com.example.edmotor.model

import java.text.Normalizer
import java.util.Locale

data class Category(
    val emoji: String,
    val nombre: String
)

internal fun categoryKey(name: String): String =
    Normalizer.normalize(name.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC)
