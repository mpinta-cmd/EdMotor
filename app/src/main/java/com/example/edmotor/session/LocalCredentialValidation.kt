package com.example.edmotor.session

import java.util.Locale

object LocalCredentialValidation {
    private val usernamePattern = Regex("[a-z0-9_]{3,20}")
    private const val MAX_PASSWORD_BYTES = 128

    fun normalizeUsername(username: String): String = username.trim().lowercase(Locale.ROOT)

    internal fun isStoredUsername(username: String): Boolean = usernamePattern.matches(username)

    fun loginError(username: String, password: String): String? = when {
        !usernamePattern.matches(normalizeUsername(username)) -> "Ingresa un nombre de usuario válido."
        password.isBlank() -> "Ingresa tu contraseña."
        password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES -> "La contraseña supera el límite permitido."
        else -> null
    }

    fun registrationError(username: String, displayName: String, password: String): String? {
        val name = displayName.trim()
        return when {
            !usernamePattern.matches(normalizeUsername(username)) ->
                "El usuario debe tener entre 3 y 20 letras, números o guiones bajos."
            name.length !in 2..50 || name.any(Char::isISOControl) -> "Escribe un nombre de entre 2 y 50 caracteres."
            password.isBlank() || password.codePointCount(0, password.length) < 8 ->
                "La contraseña debe tener al menos 8 caracteres."
            password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES -> "La contraseña supera el límite permitido."
            else -> null
        }
    }

    fun ownerError(username: String, password: String): String? = registrationError(username, "EdMotor", password)
}
