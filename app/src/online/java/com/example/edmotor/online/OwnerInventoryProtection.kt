package com.example.edmotor.online

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.edmotor.ui.components.EdMotorBottomSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class OwnerInventoryAccess(
    val locked: Boolean,
    val busy: Boolean,
    val notice: String?,
    val openSettings: () -> Unit,
    val unlock: () -> Unit,
    val usePassword: () -> Unit
)

private enum class InventoryVerification {
    Enable, Unlock, Disable
}

@Composable
internal fun rememberOwnerInventoryAccess(
    sessionIdentity: Any,
    profileScope: String,
    inventoryOpen: Boolean,
    isCurrentSession: (Any) -> Boolean,
    confirmPassword: suspend (Any, String) -> Unit
): OwnerInventoryAccess = key(sessionIdentity) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preferences = remember(context) {
        context.getSharedPreferences("online_owner_inventory_protection", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    val prompt = rememberOwnerBiometricPrompt(sessionIdentity)
    val inventoryVisible by rememberUpdatedState(inventoryOpen)
    var enabled by remember { mutableStateOf(preferences.getBoolean(profileScope, false)) }
    var unlocked by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var biometricAction by remember { mutableStateOf<InventoryVerification?>(null) }
    var passwordAction by remember { mutableStateOf<InventoryVerification?>(null) }
    var completingAction by remember { mutableStateOf<InventoryVerification?>(null) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var password by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    val busy = prompt.isAuthenticating || completingAction != null

    fun closePassword() {
        passwordAction = null
        password = ""
    }

    fun finishVerification(action: InventoryVerification, enteredPassword: String? = null) {
        if (!isCurrentSession(sessionIdentity) || completingAction != null) return
        if (action == InventoryVerification.Unlock && !inventoryVisible) return
        completingAction = action
        notice = null
        operation = scope.launch {
            try {
                if (enteredPassword != null) confirmPassword(sessionIdentity, enteredPassword)
                if (!isCurrentSession(sessionIdentity)) return@launch
                if (action != InventoryVerification.Unlock) {
                    val newEnabled = action == InventoryVerification.Enable
                    @Suppress("UseKtx") // The KTX extension discards the commit result.
                    val saved = withContext(Dispatchers.IO) {
                        preferences.edit().putBoolean(profileScope, newEnabled).commit()
                    }
                    if (!saved) throw OnlineInputError("No se pudo guardar la protección. Inténtalo de nuevo.")
                    if (!isCurrentSession(sessionIdentity)) return@launch
                    enabled = newEnabled
                }
                unlocked = enabled && inventoryVisible && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                closePassword()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notice = onlineError(error)
            } finally {
                completingAction = null
                operation = null
            }
        }
    }

    fun authenticate(action: InventoryVerification) {
        if (busy || !isCurrentSession(sessionIdentity)) return
        notice = null
        biometricAction = action
        val title = when (action) {
            InventoryVerification.Enable -> "Activar huella para Inventario"
            InventoryVerification.Unlock -> "Desbloquear Inventario"
            InventoryVerification.Disable -> "Desactivar protección de Inventario"
        }
        prompt.authenticate(
            title,
            description = if (action == InventoryVerification.Enable) "Confirma con la biometría registrada en Android."
            else "Confirma tu identidad con la verificación de Android.",
            allowDeviceCredential = action != InventoryVerification.Enable
        ) { result ->
            if (biometricAction != action || !isCurrentSession(sessionIdentity)) return@authenticate
            biometricAction = null
            when (result) {
                OwnerBiometricResult.Authenticated -> finishVerification(action)
                OwnerBiometricResult.Cancelled -> Unit
                is OwnerBiometricResult.Failed -> notice = result.message
            }
        }
    }

    fun usePassword(action: InventoryVerification) {
        if (busy || !enabled) return
        notice = null
        password = ""
        passwordAction = action
    }

    LaunchedEffect(inventoryOpen) {
        if (!inventoryOpen) {
            unlocked = false
            if (biometricAction == InventoryVerification.Unlock) {
                biometricAction = null
                prompt.cancel()
            }
            if (completingAction == InventoryVerification.Unlock) operation?.cancel()
            if (passwordAction == InventoryVerification.Unlock) closePassword()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        unlocked = false
        if (passwordAction != null) {
            operation?.cancel()
            closePassword()
        }
    }

    val passwordRequest = passwordAction
    if (passwordRequest != null) {
        val submit: () -> Unit = {
            if (!busy && password.isNotBlank()) {
                val entered = password
                password = ""
                finishVerification(passwordRequest, entered)
            }
        }
        EdMotorBottomSheet(
            onDismissRequest = { if (!busy) { closePassword(); notice = null } },
            dismissEnabled = !busy,
            title = { Text("Confirma tu contraseña") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Escribe la contraseña de tu cuenta de dueño en EdMotor.")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; notice = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Contraseña de EdMotor") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )
                    InventoryProtectionNotice(notice)
                }
            },
            confirmButton = {
                Button(onClick = submit, enabled = !busy && password.isNotBlank()) {
                    Text(if (busy) "Verificando…" else "Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { closePassword(); notice = null }, enabled = !busy) { Text("Cancelar") }
            }
        )
    } else if (settingsOpen) {
        EdMotorBottomSheet(
            onDismissRequest = { if (!busy) { settingsOpen = false; notice = null } },
            dismissEnabled = !busy,
            icon = { Icon(Icons.Outlined.Fingerprint, contentDescription = null) },
            title = { Text("Huella para Inventario") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (enabled) "Protección activada en este celular." else "Protección desactivada.",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Cuando la protección está activa, EdMotor pide verificar tu identidad al volver al inventario.")
                    if (enabled) {
                        TextButton(onClick = { usePassword(InventoryVerification.Disable) }, enabled = !busy) {
                            Text("Desactivar con contraseña")
                        }
                    }
                    InventoryProtectionNotice(notice)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        authenticate(if (enabled) InventoryVerification.Disable else InventoryVerification.Enable)
                    },
                    enabled = !busy
                ) {
                    Text(if (busy) "Verificando…" else if (enabled) "Desactivar protección" else "Activar con huella")
                }
            },
            dismissButton = {
                TextButton(onClick = { settingsOpen = false; notice = null }, enabled = !busy) { Text("Cerrar") }
            }
        )
    }

    OwnerInventoryAccess(
        locked = enabled && !unlocked,
        busy = busy,
        notice = notice.takeIf { !settingsOpen && passwordAction == null },
        openSettings = { notice = null; settingsOpen = true },
        unlock = { authenticate(InventoryVerification.Unlock) },
        usePassword = { usePassword(InventoryVerification.Unlock) }
    )
}

@Composable
internal fun OwnerInventoryLockedScreen(access: OwnerInventoryAccess) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, colors.outlineVariant),
            color = colors.surface
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.padding(10.dp).size(28.dp))
                }
                Text(
                    "Inventario protegido",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Confirma tu identidad para ver y editar los repuestos de tu taller.")
                InventoryProtectionNotice(access.notice)
                Button(onClick = access.unlock, enabled = !access.busy, modifier = Modifier.fillMaxWidth()) {
                    if (access.busy) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Verificando…")
                        }
                    } else Text("Desbloquear con huella")
                }
                TextButton(onClick = access.usePassword, enabled = !access.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Usar contraseña de EdMotor")
                }
            }
        }
    }
}

@Composable
private fun InventoryProtectionNotice(notice: String?) {
    notice?.let {
        Text(
            it,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
