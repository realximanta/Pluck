package xyz.ximanta.pluck.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.IOException

data class AuthState(
    val apiKey: String? = null,
    val name: String = "",
    val planName: String = "",
    val expiresAt: Long = 0L,
    val loaded: Boolean = false
) {
    val isLoggedIn: Boolean get() = !apiKey.isNullOrBlank()
}

private val Context.pluckAuthDataStore by preferencesDataStore("pluck_auth")

class AuthStore(context: Context) {
    private val dataStore = context.applicationContext.pluckAuthDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .catch { e ->
                    if (e is IOException) emit(emptyPreferences()) else throw e
                }
                .collect { prefs ->
                    _state.value = AuthState(
                        apiKey = prefs[KEY_API],
                        name = prefs[KEY_NAME].orEmpty(),
                        planName = prefs[KEY_PLAN].orEmpty(),
                        expiresAt = prefs[KEY_EXPIRES] ?: 0L,
                        loaded = true
                    )
                }
        }
    }

    fun currentKey(): String? = _state.value.apiKey

    suspend fun save(key: String, name: String, planName: String, expiresAt: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_API] = key
            prefs[KEY_NAME] = name
            prefs[KEY_PLAN] = planName
            prefs[KEY_EXPIRES] = expiresAt
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_API = stringPreferencesKey("api_key")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_PLAN = stringPreferencesKey("plan_name")
        val KEY_EXPIRES = longPreferencesKey("expires_at")
    }
}
