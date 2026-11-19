package com.example.edmotor.model

data class DemoProfile(
    val key: String,
    val displayName: String,
    val role: UserRole,
    val remoteId: String? = null
)
