package com.example.edmotor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.DemoProfile
import com.example.edmotor.model.UserRole

private enum class AccessPage { WELCOME, CLIENTS, CREATE_CLIENT }

@Composable
fun AuthFlowScreen(
    profiles: List<DemoProfile>,
    mockApiConfigured: Boolean,
    isLoading: Boolean,
    selectingKey: String?,
    isCreating: Boolean,
    message: String?,
    onSelectOwner: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onCreateClientProfile: (String) -> Unit,
    onClearMessage: () -> Unit,
    onRetry: () -> Unit
) {
    var page by rememberSaveable { mutableStateOf(AccessPage.WELCOME) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var clientName by rememberSaveable { mutableStateOf("") }
    val isSelecting = selectingKey != null
    val isBusy = isSelecting || isCreating
    val clients = profiles.filter { it.role == UserRole.CLIENT }
    val selectedClient = clients.firstOrNull { it.key == selectedKey }
    val localClients = clients.filter { it.remoteId == null }
    val remoteClients = clients.filter { it.remoteId != null }
    val cleanName = clientName.trim()
    val validName = cleanName.length in 2..50 && cleanName.none(Char::isISOControl)
    val canCreate = validName && !isBusy
    val listState = key(page) { rememberLazyListState() }

    fun goBack() {
        if (isBusy) return
        onClearMessage()
        page = when (page) {
            AccessPage.WELCOME, AccessPage.CLIENTS -> AccessPage.WELCOME
            AccessPage.CREATE_CLIENT -> AccessPage.CLIENTS
        }
    }

    BackHandler(enabled = page != AccessPage.WELCOME || isBusy, onBack = ::goBack)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).then(
                    if (page == AccessPage.CLIENTS) Modifier.selectableGroup() else Modifier
                ),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 26.dp),
                verticalArrangement = if (page == AccessPage.WELCOME) {
                    Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                } else {
                    Arrangement.spacedBy(12.dp)
                }
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            "EdMotor Tecnológica",
                            modifier = Modifier.fillMaxWidth().padding(24.dp).semantics { heading() },
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (page != AccessPage.WELCOME) {
                    item {
                        TextButton(onClick = ::goBack, enabled = !isBusy) { Text("Volver") }
                    }
                }

                when (page) {
                    AccessPage.WELCOME -> {
                        item {
                            Button(
                                onClick = onSelectOwner,
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = RoundedCornerShape(15.dp)
                            ) {
                                if (isSelecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(end = 8.dp).size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                Text(if (isSelecting) "Abriendo perfil…" else "Entrar como dueño · EdMotor")
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = {
                                    onClearMessage()
                                    page = AccessPage.CLIENTS
                                },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = RoundedCornerShape(15.dp)
                            ) { Text("Soy cliente") }
                        }
                    }

                    AccessPage.CLIENTS -> {
                        item {
                            Text("Clientes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        }
                        item {
                            OutlinedButton(
                                onClick = {
                                    onClearMessage()
                                    clientName = ""
                                    page = AccessPage.CREATE_CLIENT
                                },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = RoundedCornerShape(15.dp)
                            ) { Text("Crear perfil de cliente") }
                        }
                        if (localClients.isNotEmpty()) {
                            item { Text("En este dispositivo", style = MaterialTheme.typography.titleMedium) }
                            items(localClients, key = { it.key }) { profile ->
                                ClientProfileCard(profile, selectedKey == profile.key, !isBusy) {
                                    selectedKey = profile.key
                                }
                            }
                        }
                        if (mockApiConfigured) {
                            item { Text("Perfiles en línea", style = MaterialTheme.typography.titleMedium) }
                            items(remoteClients, key = { it.key }) { profile ->
                                ClientProfileCard(profile, selectedKey == profile.key, !isBusy) {
                                    selectedKey = profile.key
                                }
                            }
                            item {
                                if (isLoading) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("Consultando perfiles…")
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (remoteClients.isEmpty() && message == null) {
                                            Text("No hay perfiles de cliente en línea.", modifier = Modifier.weight(1f))
                                        }
                                        TextButton(onClick = onRetry, enabled = !isBusy) { Text("Actualizar perfiles") }
                                    }
                                }
                            }
                        } else {
                            item {
                                Text(
                                    "Puedes crear y usar perfiles de este dispositivo sin conexión.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AccessPage.CREATE_CLIENT -> {
                        item {
                            Text("Crear perfil de cliente", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        }
                        item {
                            Text(
                                if (mockApiConfigured) "El nombre que elijas se mostrará en la lista de perfiles en línea."
                                else "El perfil se guardará en este dispositivo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = clientName,
                                onValueChange = {
                                    clientName = it
                                    onClearMessage()
                                },
                                label = { Text("Nombre para mostrar") },
                                supportingText = { Text("Entre 2 y 50 caracteres.") },
                                isError = clientName.isNotBlank() && !validName,
                                singleLine = true,
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { if (canCreate) onCreateClientProfile(cleanName) }
                                )
                            )
                        }
                    }
                }

                if (message != null) {
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
            }

            if (page != AccessPage.WELCOME) {
                Button(
                    onClick = {
                        if (page == AccessPage.CREATE_CLIENT) {
                            onCreateClientProfile(cleanName)
                        } else {
                            selectedClient?.let { onSelectProfile(it.key) }
                        }
                    },
                    enabled = if (page == AccessPage.CREATE_CLIENT) canCreate else selectedClient != null && !isBusy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        when {
                            isCreating -> "Creando perfil…"
                            isSelecting -> "Abriendo perfil…"
                            page == AccessPage.CREATE_CLIENT -> "Crear perfil y continuar"
                            else -> "Continuar"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientProfileCard(
    profile: DemoProfile,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick
        ),
        color = if (selected) colors.primaryContainer else colors.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
