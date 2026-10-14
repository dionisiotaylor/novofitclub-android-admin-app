package com.novofitclub.dashboard.core.net

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class WPCredentials(
    val baseUrl: String,
    val username: String,
    val appPassword: String,
) {
    val basicAuthHeader: String
        get() {
            val raw = "$username:$appPassword"
            return "Basic " + Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)
        }
}

data class AuthState(
    val isAuthenticated: Boolean = false,
    val siteUrl: String = DEFAULT_SITE_URL,
    val username: String = "",
    val loginError: String? = null,
    val isValidating: Boolean = false,
)

const val DEFAULT_SITE_URL = "https://novofitclub.com"

/**
 * Owns the signed-in state for the app. Credentials are the user's WordPress
 * username + an Application Password (Usuarios > Perfil > Application
 * Passwords) — never their main account password.
 */
class AuthManager(context: Context) {
    private val prefs = context.getSharedPreferences("novo_auth", Context.MODE_PRIVATE)
    private val store = CredentialStore(context)
    private val http = OkHttpClient()

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        val savedUrl = prefs.getString(KEY_URL, null) ?: DEFAULT_SITE_URL
        val savedUser = prefs.getString(KEY_USERNAME, null)
        _state.value = if (savedUser != null && store.read(savedUser) != null) {
            AuthState(isAuthenticated = true, siteUrl = savedUrl, username = savedUser)
        } else {
            AuthState(siteUrl = savedUrl)
        }
    }

    val currentCredentials: WPCredentials?
        get() {
            val s = _state.value
            if (!s.isAuthenticated) return null
            val password = store.read(s.username) ?: return null
            return WPCredentials(s.siteUrl, s.username, password)
        }

    /**
     * Validates credentials against WordPress core's own "me" endpoint before
     * trusting them, so a typo surfaces immediately instead of failing later
     * on some deep, plugin-specific screen.
     */
    suspend fun login(rawUrl: String, username: String, appPassword: String) {
        _state.update { it.copy(loginError = null, isValidating = true) }

        var normalized = rawUrl.trim()
        if (!normalized.lowercase().startsWith("http")) normalized = "https://$normalized"
        normalized = normalized.trimEnd('/')

        val credentials = WPCredentials(normalized, username.trim(), appPassword)
        val request = try {
            Request.Builder()
                .url("$normalized/wp-json/wp/v2/users/me")
                .header("Authorization", credentials.basicAuthHeader)
                .header("Accept", "application/json")
                .build()
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(isValidating = false, loginError = "La URL del sitio no es válida.") }
            return
        }

        val error: String? = withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> null
                        401, 403 -> "Usuario o contraseña de aplicación incorrectos."
                        else -> {
                            val body = response.body?.string().orEmpty()
                            runCatching { JSONObject(body).optString("message") }.getOrNull()
                                ?.takeIf { it.isNotBlank() }
                                ?: "Error del servidor (${response.code})."
                        }
                    }
                }
            } catch (e: IOException) {
                e.localizedMessage ?: "No se pudo contactar al servidor."
            }
        }

        if (error != null) {
            _state.update { it.copy(isValidating = false, loginError = error) }
            return
        }

        store.save(credentials.username, appPassword)
        prefs.edit()
            .putString(KEY_URL, normalized)
            .putString(KEY_USERNAME, credentials.username)
            .apply()
        _state.value = AuthState(
            isAuthenticated = true,
            siteUrl = normalized,
            username = credentials.username,
        )
    }

    fun logout() {
        val user = _state.value.username
        store.delete(user)
        prefs.edit().remove(KEY_USERNAME).apply()
        _state.update { it.copy(isAuthenticated = false, username = "") }
    }

    private companion object {
        const val KEY_URL = "wp_site_url"
        const val KEY_USERNAME = "wp_username"
    }
}
