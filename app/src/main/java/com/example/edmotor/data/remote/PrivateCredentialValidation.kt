package com.example.edmotor.data.remote

import java.util.Locale

object PrivateCredentialValidation {
    private val usernamePattern = Regex("[a-z0-9_]{3,20}")
    private val emailCodePattern = Regex("[0-9]{6}")

    fun normalizeUsername(username: String): String = username.trim().lowercase(Locale.ROOT)

    fun loginError(username: String, password: String): String? = when {
        !usernamePattern.matches(normalizeUsername(username)) -> "Ingresa un nombre de usuario válido."
        password.isEmpty() -> "Ingresa tu contraseña."
        password.toByteArray(Charsets.UTF_8).size > 72 -> "La contraseña supera el límite permitido."
        else -> null
    }

    fun registrationError(username: String, displayName: String, email: String, password: String): String? {
        val name = displayName.trim()
        return when {
            !usernamePattern.matches(normalizeUsername(username)) ->
                "El usuario debe tener entre 3 y 20 letras, números o guiones bajos."
            name.length !in 2..50 || name.any(Char::isISOControl) -> "Escribe un nombre de entre 2 y 50 caracteres."
            !validEmail(email) -> "Escribe un correo válido para confirmar tu cuenta."
            password.codePointCount(0, password.length) < 8 -> "La contraseña debe tener al menos 8 caracteres."
            password.toByteArray(Charsets.UTF_8).size > 72 -> "La contraseña supera el límite permitido."
            else -> null
        }
    }

    fun verificationError(email: String, code: String): String? = when {
        !validEmail(email) -> "Escribe el correo con el que te registraste."
        !emailCodePattern.matches(code.trim()) -> "Escribe el código de 6 dígitos que recibiste."
        else -> null
    }

    private fun validEmail(value: String): Boolean {
        val email = value.trim()
        if (email.length !in 5..254 || email.any { it.isWhitespace() || it.isISOControl() }) return false
        val parts = email.split('@')
        return parts.size == 2 && parts[0].length in 1..64 &&
            parts[1].split('.').let { labels -> labels.size >= 2 && labels.none(String::isBlank) }
    }
}
