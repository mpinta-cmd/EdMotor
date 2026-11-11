package com.example.edmotor.model

enum class UserRole {
    WORKSHOP,
    CLIENT;

    companion object {
        fun from(value: String): UserRole =
            entries.firstOrNull { it.name == value } ?: CLIENT
    }
}
