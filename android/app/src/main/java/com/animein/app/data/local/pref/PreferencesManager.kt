package com.animein.app.data.local.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anime_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val DARK_MODE_KEY = stringPreferencesKey("dark_mode")
        val HOME_CACHE_KEY = stringPreferencesKey("home_cache")
        val ACTIVE_SOURCE_KEY = stringPreferencesKey("active_source")
    }

    enum class ThemeMode {
        DARK, LIGHT, SYSTEM
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[DARK_MODE_KEY] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val activeSourceFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_SOURCE_KEY] ?: "animeinweb"
    }

    suspend fun getActiveSource(): String {
        val preferences = context.dataStore.data.first()
        return preferences[ACTIVE_SOURCE_KEY] ?: "animeinweb"
    }

    suspend fun setActiveSource(source: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_SOURCE_KEY] = source
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = mode.name
        }
    }

    suspend fun getHomeCache(): String? {
        val preferences = context.dataStore.data.first()
        return preferences[HOME_CACHE_KEY]
    }

    suspend fun saveHomeCache(json: String) {
        context.dataStore.edit { preferences ->
            preferences[HOME_CACHE_KEY] = json
        }
    }
}
