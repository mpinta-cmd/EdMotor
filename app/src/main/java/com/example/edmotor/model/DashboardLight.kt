package com.example.edmotor.model

data class DashboardLight(
    val id: Int,
    val icon: String,
    val name: String,
    val severity: String,
    val meaning: String,
    val recommendedAction: String,
    val relatedCategory: String = ""
)