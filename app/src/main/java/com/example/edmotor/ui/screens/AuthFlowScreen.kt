package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private enum class AuthMode {
    LOGIN,
    REGISTER
}

@Composable
fun AuthFlowScreen(
    message: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String?) -> Unit,
    onClearMessage: () -> Unit
) {

    var mode by rememberSaveable {
        mutableStateOf(AuthMode.LOGIN)
    }

    var username by rememberSaveable {
        mutableStateOf("")
    }

    var password by rememberSaveable {
        mutableStateOf("")
    }

    var confirmPassword by rememberSaveable {
        mutableStateOf("")
    }

    var displayName by rememberSaveable {
        mutableStateOf("")
    }

    var phone by rememberSaveable {
        mutableStateOf("")
    }

    var localError by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val isRegister = mode == AuthMode.REGISTER

    val title = if (isRegister) {
        "Crear cuenta de cliente"
    } else {
        "Bienvenido a EdMotor"
    }

    val subtitle = if (isRegister) {
        "Regístrate para guardar tus vehículos y consultar su historial."
    } else {
        "Ingresa para consultar tus vehículos y servicios."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {

            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {

                Text(
                    text = "ED MOTOR",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ==================================
                // DATOS DEL CLIENTE
                // ==================================

                if (isRegister) {

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            localError = null
                            onClearMessage()
                        },
                        label = {
                            Text("Nombre completo")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ==================================
                // USUARIO
                // ==================================

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        localError = null
                        onClearMessage()
                    },
                    label = {
                        Text("Nombre de usuario")
                    },
                    supportingText = {
                        Text(
                            "3 a 20 caracteres: letras, números o guion bajo."
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ==================================
                // TELÉFONO
                // ==================================

                if (isRegister) {

                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                        },
                        label = {
                            Text("Teléfono (opcional)")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ==================================
                // CONTRASEÑA
                // ==================================

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                        onClearMessage()
                    },
                    label = {
                        Text("Contraseña")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ==================================
                // CONFIRMAR CONTRASEÑA
                // ==================================

                if (isRegister) {

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            localError = null
                        },
                        label = {
                            Text("Confirmar contraseña")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ==================================
                // MENSAJES
                // ==================================

                val visibleMessage = localError ?: message

                if (!visibleMessage.isNullOrBlank()) {

                    Text(
                        text = visibleMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // ==================================
                // BOTÓN PRINCIPAL
                // ==================================

                Button(
                    onClick = {

                        localError = null

                        if (isRegister) {

                            when {

                                password != confirmPassword -> {

                                    localError =
                                        "Las contraseñas no coinciden."
                                }

                                displayName.isBlank() -> {

                                    localError =
                                        "Escribe tu nombre."
                                }

                                password.length < 6 -> {

                                    localError =
                                        "La contraseña debe tener al menos 6 caracteres."
                                }

                                else -> {

                                    onRegister(
                                        username,
                                        password,
                                        displayName,
                                        phone
                                    )
                                }
                            }

                        } else {

                            onLogin(
                                username,
                                password
                            )
                        }
                    },
                    enabled = username.isNotBlank() &&
                            password.isNotBlank() &&
                            (
                                    !isRegister ||
                                            (
                                                    displayName.isNotBlank() &&
                                                            confirmPassword.isNotBlank()
                                                    )
                                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {

                    Text(
                        text = if (isRegister) {
                            "Crear mi cuenta"
                        } else {
                            "Iniciar sesión"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                // ==================================
                // CAMBIAR ENTRE REGISTRO Y LOGIN
                // ==================================

                if (isRegister) {

                    TextButton(
                        onClick = {

                            mode = AuthMode.LOGIN
                            localError = null
                            onClearMessage()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "Ya tengo una cuenta · Iniciar sesión"
                        )
                    }

                } else {

                    TextButton(
                        onClick = {

                            mode = AuthMode.REGISTER
                            localError = null
                            onClearMessage()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "¿Eres cliente nuevo? Crear cuenta"
                        )
                    }
                }
            }
        }
    }
}