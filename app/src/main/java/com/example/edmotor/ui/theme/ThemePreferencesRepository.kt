
package com.example.edmotor.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.edMotorPreferences by preferencesDataStore(
    name = "edmotor_preferences"
)

class ThemePreferencesRepository(context: Context) {

    private val dataStore = context.edMotorPreferences

    private val themeModeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<AppThemeMode> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            AppThemeMode.fromStorage(preferences[themeModeKey])
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[themeModeKey] = mode.storageValue
        }
    }
}
