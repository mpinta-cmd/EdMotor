package com.example.edmotor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.edmotor.ui.theme.AppThemeMode
import com.example.edmotor.ui.theme.EdMotorTheme
import com.example.edmotor.ui.theme.ThemePreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun EdMotorRoot() {

    val context = LocalContext.current.applicationContext

    val preferences = remember(context) {
        ThemePreferencesRepository(context)
    }

    val themeMode by preferences.themeMode.collectAsStateWithLifecycle(
        initialValue = AppThemeMode.SYSTEM
    )

    val scope = rememberCoroutineScope()

    EdMotorTheme(themeMode = themeMode) {

        EdMotorApp(
            themeMode = themeMode,

            onThemeModeChange = { selectedMode ->

                scope.launch {
                    preferences.setThemeMode(selectedMode)
                }
            }
        )
    }
}
