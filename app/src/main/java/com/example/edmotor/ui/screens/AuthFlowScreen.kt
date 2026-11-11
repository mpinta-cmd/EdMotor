package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private enum class AuthMode { LOGIN, REGISTER, WORKSHOP_SETUP }

@Composable
fun AuthFlowScreen(
    setupRequired: Boolean,
    message: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String?) -> Unit,
    onCreateWorkshop: (String, String, String, String?) -> Unit,
    onClearMessage: () -> Unit
) {
    var mode by rememberSaveable(setupRequired) {
        mutableStateOf(if (setupRequired) AuthMode.WORKSHOP_SETUP else AuthMode.LOGIN)
    }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val isSetup = mode == AuthMode.WORKSHOP_SETUP
    val isRegister = mode == AuthMode.REGISTER
    val title = when (mode) {
        AuthMode.LOGIN -> "Bienvenido a EdMotor"
        AuthMode.REGISTER -> "Crear cuenta de cliente"
        AuthMode.WORKSHOP_SETUP -> "Configurar cuenta del taller"
    }
    val subtitle = when (mode) {
        AuthMode.LOGIN -> "Ingresa para consultar tus vehículos y servicios."
        AuthMode.REGISTER -> "Regístrate para guardar tus vehículos y consultar su historial."
        AuthMode.WORKSHOP_SETUP -> "Esta configuración se realiza una sola vez en el dispositivo del taller."
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (isRegister || isSetup) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it; localError = null; onClearMessage() },
                        label = { Text(if (isSetup) "Nombre del taller" else "Nombre completo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; localError = null; onClearMessage() },
                    label = { Text("Nombre de usuario") },
                    supportingText = { Text("3 a 20 caracteres: letras, números o guion bajo.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isRegister || isSetup) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono (opcional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; localError = null; onClearMessage() },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isRegister || isSetup) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; localError = null },
                        label = { Text("Confirmar contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val visibleMessage = localError ?: message
                if (!visibleMessage.isNullOrBlank()) {
                    Text(
                        visibleMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        localError = null
                        if (isRegister || isSetup) {
                            if (password != confirmPassword) {
                                localError = "Las contraseñas no coinciden."
                            } else if (displayName.isBlank()) {
                                localError = "Escribe el nombre."
                            } else if (password.length < 6) {
                                localError = "La contraseña debe tener al menos 6 caracteres."
                            } else if (isSetup) {
                                onCreateWorkshop(username, password, displayName, phone)
                            } else {
                                onRegister(username, password, displayName, phone)
                            }
                        } else {
                            onLogin(username, password)
                        }
                    },
                    enabled = username.isNotBlank() && password.isNotBlank() &&
                        (!(isRegister || isSetup) || (displayName.isNotBlank() && confirmPassword.isNotBlank())),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text(
                        when (mode) {
                            AuthMode.LOGIN -> "Iniciar sesión"
                            AuthMode.REGISTER -> "Crear mi cuenta"
                            AuthMode.WORKSHOP_SETUP -> "Configurar taller"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isSetup) {
                    if (isRegister) {
                        TextButton(onClick = { mode = AuthMode.LOGIN; localError = null; onClearMessage() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Ya tengo una cuenta · Iniciar sesión")
                        }
                    } else {
                        TextButton(onClick = { mode = AuthMode.REGISTER; localError = null; onClearMessage() }, modifier = Modifier.fillMaxWidth()) {
                            Text("¿Eres cliente nuevo? Crear cuenta")
                        }
                    }
                }

                if (isSetup) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "La cuenta creada aquí tendrá permisos de administrador del taller. Los clientes se registrarán desde la opción pública de crear cuenta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
