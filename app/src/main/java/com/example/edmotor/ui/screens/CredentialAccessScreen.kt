package com.example.edmotor.ui.screens

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
import com.example.edmotor.data.remote.PrivateCredentialValidation

private enum class CredentialMode { SIGN_IN, REGISTER, VERIFY_EMAIL }

@Composable
fun CredentialAccessScreen(
    isSubmitting: Boolean,
    message: String?,
    isError: Boolean,
    canRetry: Boolean,
    isConfigured: Boolean,
    verificationEmail: String?,
    onSignIn: (username: String, password: String) -> Unit,
    onRegisterClient: (username: String, displayName: String, email: String, password: String) -> Unit,
    onVerifyEmail: (email: String, code: String) -> Unit,
    onClearMessage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by rememberSaveable { mutableStateOf(CredentialMode.SIGN_IN) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var verificationCode by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val cleanUsername = username.trim()
    val cleanName = displayName.trim()
    val cleanEmail = email.trim()
    val isRegistering = mode == CredentialMode.REGISTER
    val isVerifying = mode == CredentialMode.VERIFY_EMAIL
    val canEdit = isConfigured && !isSubmitting
    val canSubmit = canEdit && when (mode) {
        CredentialMode.SIGN_IN -> cleanUsername.isNotEmpty() && passwordInput.isNotEmpty()
        CredentialMode.REGISTER -> cleanName.isNotEmpty() && cleanUsername.isNotEmpty() &&
            cleanEmail.isNotEmpty() && passwordInput.isNotEmpty()
        CredentialMode.VERIFY_EMAIL -> cleanEmail.isNotEmpty() && verificationCode.length == 6
    }

    LaunchedEffect(verificationEmail) {
        if (verificationEmail != null) {
            focusManager.clearFocus()
            email = verificationEmail
            displayName = ""
            passwordInput = ""
            passwordVisible = false
            verificationCode = ""
            mode = CredentialMode.VERIFY_EMAIL
        }
    }
    LaunchedEffect(verificationEmail, message, isError) {
        if (!isError && !message.isNullOrBlank()) {
            if (isRegistering && verificationEmail != null) {
                email = verificationEmail
                displayName = ""
                mode = CredentialMode.VERIFY_EMAIL
            } else if (isVerifying && verificationEmail == null) {
                email = ""
                verificationCode = ""
                mode = CredentialMode.SIGN_IN
            }
        }
    }
    LaunchedEffect(mode) { scrollState.scrollTo(0) }

    fun clearFeedback() {
        validationMessage = null
        if (!canRetry && isConfigured) onClearMessage()
    }

    fun selectMode(nextMode: CredentialMode) {
        focusManager.clearFocus()
        clearFeedback()
        passwordInput = ""
        passwordVisible = false
        verificationCode = ""
        mode = nextMode
    }

    fun submit() {
        if (!canSubmit) return
        validationMessage = when (mode) {
            CredentialMode.SIGN_IN -> PrivateCredentialValidation.loginError(cleanUsername, passwordInput)
            CredentialMode.REGISTER -> PrivateCredentialValidation.registrationError(
                cleanUsername, cleanName, cleanEmail, passwordInput
            )
            CredentialMode.VERIFY_EMAIL -> PrivateCredentialValidation.verificationError(cleanEmail, verificationCode)
        }
        if (validationMessage != null) return

        focusManager.clearFocus()
        val submittedPassword = passwordInput
        val submittedCode = verificationCode
        passwordInput = ""
        passwordVisible = false
        verificationCode = ""
        when (mode) {
            CredentialMode.SIGN_IN -> onSignIn(cleanUsername, submittedPassword)
            CredentialMode.REGISTER -> onRegisterClient(cleanUsername, cleanName, cleanEmail, submittedPassword)
            CredentialMode.VERIFY_EMAIL -> onVerifyEmail(cleanEmail, submittedCode)
        }
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
                            CredentialMode.REGISTER -> "Crear cuenta de cliente"
                            CredentialMode.VERIFY_EMAIL -> "Confirma tu correo"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    val status = validationMessage ?: message ?: "Configura Supabase para habilitar el acceso privado."
                        .takeIf { !isConfigured }
                    if (!status.isNullOrBlank()) {
                        Text(
                            status,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = if (validationMessage != null || isError || !isConfigured) {
                                MaterialTheme.colorScheme.error
                            } else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (canRetry) {
                        TextButton(onClick = onRetry, enabled = !isSubmitting) { Text("Reintentar conexión") }
                    }
                    if (isRegistering) {
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
                    if (!isVerifying) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                clearFeedback()
                            },
                            label = { Text("Nombre de usuario") },
                            supportingText = if (isRegistering) {
                                { Text("Usa de 3 a 20 minúsculas, números o guiones bajos.") }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canEdit,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                    }
                    if (isRegistering || isVerifying) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                clearFeedback()
                            },
                            label = { Text("Correo electrónico") },
                            supportingText = if (isRegistering) {
                                { Text("Recibirás un código para confirmar tu cuenta.") }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canEdit,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                    }
                    if (isVerifying) {
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = {
                                verificationCode = it.filter { digit -> digit in '0'..'9' }.take(6)
                                clearFeedback()
                            },
                            label = { Text("Código de 6 dígitos") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canEdit,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() })
                        )
                    } else {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                clearFeedback()
                            },
                            label = { Text("Contraseña") },
                            supportingText = if (isRegistering) { { Text("Usa al menos 8 caracteres.") } } else null,
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
                    }
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
                                CredentialMode.REGISTER -> if (isSubmitting) "Creando cuenta…" else "Crear cuenta"
                                CredentialMode.VERIFY_EMAIL -> if (isSubmitting) "Confirmando…" else "Confirmar correo"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = {
                            selectMode(if (mode == CredentialMode.SIGN_IN) CredentialMode.REGISTER else CredentialMode.SIGN_IN)
                        },
                        enabled = canEdit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (mode == CredentialMode.SIGN_IN) "Crear cuenta de cliente" else "Volver al inicio de sesión")
                    }
                    if (mode == CredentialMode.SIGN_IN) {
                        TextButton(
                            onClick = { selectMode(CredentialMode.VERIFY_EMAIL) },
                            enabled = canEdit,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirmar correo")
                        }
                    }
                }
            }
        }
    }
}
