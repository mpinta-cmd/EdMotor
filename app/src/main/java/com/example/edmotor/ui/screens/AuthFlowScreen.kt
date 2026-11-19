package com.example.edmotor.ui.screens

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.R
import com.example.edmotor.model.DemoProfile
import com.example.edmotor.model.UserRole

@Composable
fun AuthFlowScreen(
    profiles: List<DemoProfile>,
    mockApiConfigured: Boolean,
    isLoading: Boolean,
    selectingKey: String?,
    message: String?,
    onSelectProfile: (String) -> Unit,
    onRetry: () -> Unit
) {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedProfile = profiles.firstOrNull { it.key == selectedKey }
    val isSelecting = selectingKey != null
    val localProfiles = profiles.filter { it.remoteId == null }
    val remoteProfiles = profiles.filter { it.remoteId != null }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            LazyColumn(
                modifier = Modifier.weight(1f).selectableGroup(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Selecciona un perfil",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Elige el perfil con el que quieres continuar. Los perfiles en línea permiten gestionar citas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Text("En este dispositivo", style = MaterialTheme.typography.titleMedium)
                }
                items(localProfiles, key = { it.key }) { profile ->
                    DemoProfileCard(profile, selectedKey == profile.key, !isSelecting) {
                        selectedKey = profile.key
                    }
                }

                item {
                    Text("Perfiles en línea", style = MaterialTheme.typography.titleMedium)
                }
                items(remoteProfiles, key = { it.key }) { profile ->
                    DemoProfileCard(profile, selectedKey == profile.key, !isSelecting) {
                        selectedKey = profile.key
                    }
                }
                item {
                    when {
                        !mockApiConfigured -> Text(
                            "MockAPI está sin configurar. Los perfiles de este dispositivo siguen disponibles.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        isLoading -> Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Consultando perfiles…")
                        }
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            if (remoteProfiles.isEmpty() && message == null) {
                                Text("No hay perfiles disponibles en línea.", modifier = Modifier.weight(1f))
                            }
                            TextButton(onClick = onRetry, enabled = !isSelecting) { Text("Actualizar perfiles") }
                        }
                    }
                }
                if (message != null) {
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
            }

            Button(
                onClick = { selectedProfile?.let { onSelectProfile(it.key) } },
                enabled = selectedProfile != null && !isSelecting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp).heightIn(min = 52.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                if (isSelecting) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                }
                Text(if (isSelecting) "Abriendo perfil…" else "Continuar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DemoProfileCard(
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (profile.role == UserRole.WORKSHOP) "Dueño" else "Cliente",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}
