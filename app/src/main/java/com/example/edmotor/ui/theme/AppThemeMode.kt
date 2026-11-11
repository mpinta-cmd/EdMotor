
package com.example.edmotor.ui.theme

enum class AppThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode {
            return entries.firstOrNull {
                it.storageValue == value
            } ?: SYSTEM
        }
    }
}
