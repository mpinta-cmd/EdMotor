package com.example.edmotor.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["remoteAuthId"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val username: String,
    val displayName: String,
    val phone: String? = null,
    val passwordHash: String,
    val salt: String,
    val role: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val remoteAuthId: String? = null
)
