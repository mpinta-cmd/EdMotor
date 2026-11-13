package com.example.edmotor.session

data class RemoteSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
)
