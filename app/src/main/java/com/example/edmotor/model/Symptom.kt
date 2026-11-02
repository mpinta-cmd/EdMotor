package com.example.edmotor.model

data class Symptom(
    val id: Int,
    val title: String,
    val emoji: String,
    val probableCause: String,
    val solution: String
)