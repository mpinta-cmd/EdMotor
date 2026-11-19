package com.example.edmotor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.edmotor.session.LocalCredentialValidation

private enum class CredentialMode { SIGN_IN, REGISTER_CLIENT, SETUP_OWNER }

@Composable
fun CredentialAccessScreen(
    isSubmitting: Boolean,
    message: String?,
    isError: Boolean,
    canRetry: Boolean,
    ownerConfigured: Boolean?,
    onSignIn: (username: String, password: String) -> Unit,
    onRegisterClient: (username: String, displayName: String, password: String) -> Unit,
    onSetupOwner: (username: String, password: String) -> Unit,
    onClearMessage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var requestedMode by rememberSaveable { mutableStateOf(CredentialMode.SIGN_IN) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val canSetupOwner = ownerConfigured == false
    val mode = if (requestedMode == CredentialMode.SETUP_OWNER && !canSetupOwner) {
        CredentialMode.SIGN_IN
    } else requestedMode
    val cleanUsername = username.trim()
    val cleanName = displayName.trim()
    val canEdit = ownerConfigured != null && !isSubmitting
    val canSubmit = canEdit && cleanUsername.isNotEmpty() && passwordInput.isNotEmpty() &&
        (mode != CredentialMode.REGISTER_CLIENT || cleanName.isNotEmpty())

    LaunchedEffect(mode) { scrollState.scrollTo(0) }

    fun clearFeedback() {
        validationMessage = null
        if (!canRetry) onClearMessage()
    }

    fun selectMode(nextMode: CredentialMode) {
        focusManager.clearFocus()
        clearFeedback()
        passwordInput = ""
        passwordVisible = false
        requestedMode = nextMode
    }

    fun submit() {
        if (!canSubmit) return
        validationMessage = when (mode) {
            CredentialMode.SIGN_IN -> LocalCredentialValidation.loginError(cleanUsername, passwordInput)
            CredentialMode.REGISTER_CLIENT -> LocalCredentialValidation.registrationError(cleanUsername, cleanName, passwordInput)
            CredentialMode.SETUP_OWNER -> LocalCredentialValidation.ownerError(cleanUsername, passwordInput)
        }
        if (validationMessage != null) return

        focusManager.clearFocus()
        val submittedPassword = passwordInput
        passwordInput = ""
        passwordVisible = false
        when (mode) {
            CredentialMode.SIGN_IN -> onSignIn(cleanUsername, submittedPassword)
            CredentialMode.REGISTER_CLIENT -> onRegisterClient(cleanUsername, cleanName, submittedPassword)
            CredentialMode.SETUP_OWNER -> onSetupOwner(cleanUsername, submittedPassword)
        }
    }

    BackHandler(enabled = mode != CredentialMode.SIGN_IN) {
        if (canEdit) selectMode(CredentialMode.SIGN_IN)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 448.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "EdMotor Tecnológica",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        when (mode) {
                            CredentialMode.SIGN_IN -> "Accede a tu cuenta"
                            CredentialMode.REGISTER_CLIENT -> "Crear cuenta de cliente"
                            CredentialMode.SETUP_OWNER -> "Configura la cuenta del dueño"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        if (mode == CredentialMode.SETUP_OWNER) {
                            "La cuenta de EdMotor permite administrar el inventario, los pedidos y el mantenimiento."
                        } else {
                            "Las cuentas se guardan en este dispositivo."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val status = validationMessage ?: message
                    if (!status.isNullOrBlank()) {
                        Text(
                            status,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = if (isError || validationMessage != null) {
                                MaterialTheme.colorScheme.error
                            } else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (canRetry) {
                        TextButton(onClick = onRetry, enabled = !isSubmitting) { Text("Reintentar") }
                    }
                    if (mode == CredentialMode.REGISTER_CLIENT) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                                clearFeedback()
                            },
                            label = { Text("Nombre para mostrar") },
                            supportingText = { Text("Entre 2 y 50 caracteres.") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canEdit,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            clearFeedback()
                        },
                        label = { Text("Nombre de usuario") },
                        supportingText = if (mode != CredentialMode.SIGN_IN) {
                            { Text("Usa de 3 a 20 minúsculas, números o guiones bajos.") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canEdit,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            clearFeedback()
                        },
                        label = { Text("Contraseña") },
                        supportingText = if (mode != CredentialMode.SIGN_IN) {
                            { Text("Usa al menos 8 caracteres.") }
                        } else null,
                        modifier = Modifier.fillMaxWidth().semantics { password() },
                        enabled = canEdit,
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = canEdit
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                                )
                            }
                        }
                    )
                    Button(
                        onClick = ::submit,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 10.dp).size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            when (mode) {
                                CredentialMode.SIGN_IN -> if (isSubmitting) "Iniciando sesión…" else "Iniciar sesión"
                                CredentialMode.REGISTER_CLIENT -> if (isSubmitting) "Creando cuenta…" else "Crear cuenta"
                                CredentialMode.SETUP_OWNER -> if (isSubmitting) "Guardando cuenta…" else "Crear cuenta del dueño"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (mode == CredentialMode.SIGN_IN) {
                        TextButton(
                            onClick = { selectMode(CredentialMode.REGISTER_CLIENT) },
                            enabled = canEdit,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Crear cuenta de cliente") }
                        if (canSetupOwner) {
                            TextButton(
                                onClick = { selectMode(CredentialMode.SETUP_OWNER) },
                                enabled = canEdit,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Configurar cuenta del dueño") }
                        }
                    } else {
                        TextButton(
                            onClick = { selectMode(CredentialMode.SIGN_IN) },
                            enabled = canEdit,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Volver al inicio de sesión") }
                    }
                }
            }
        }
    }
}
