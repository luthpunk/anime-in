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
        val HOME_CACHE_KEY = stringPreferencesKey("home_cache_v2") // v2: invalidates old cache
        private const val CACHE_VERSION = "2"
        val CACHE_VERSION_KEY = stringPreferencesKey("cache_version")
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

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = mode.name
        }
    }

    suspend fun getHomeCache(): String? {
        val preferences = context.dataStore.data.first()
        // Return null if cache version doesn't match (forces refresh)
        val version = preferences[CACHE_VERSION_KEY]
        if (version != CACHE_VERSION) return null
        return preferences[HOME_CACHE_KEY]
    }

    suspend fun saveHomeCache(json: String) {
        context.dataStore.edit { preferences ->
            preferences[HOME_CACHE_KEY] = json
            preferences[CACHE_VERSION_KEY] = CACHE_VERSION
        }
    }

    suspend fun clearHomeCache() {
        context.dataStore.edit { preferences ->
            preferences.remove(HOME_CACHE_KEY)
            preferences.remove(CACHE_VERSION_KEY)
        }
    }
}
