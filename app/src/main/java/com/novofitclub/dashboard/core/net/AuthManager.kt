package com.novofitclub.dashboard.core.net

import android.content.Context
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

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
    /** Staff role from `novo-staff/v1/me`: "admin", "receptionist", "coach" or "other"; cached across launches. */
    val role: String? = null,
    val displayName: String = "",
    /** What the role may do, from `novo-staff/v1/me` (`can`). Convenience only: the server checks again. */
    val can: StaffCan = StaffCan(),
    /** Set when the role couldn't be loaded and there's no cached one — the tabs can't be decided yet. */
    val profileError: String? = null,
)

/** The `can` object of `novo-staff/v1/me`. */
data class StaffCan(
    val editMemberDates: Boolean = false,
    val editFechaAdmin: Boolean = false,
    val createSales: Boolean = false,
    val editSales: Boolean = false,
    val editMembers: Boolean = false,
    val deleteMembers: Boolean = false,
    val seeAllSales: Boolean = false,
    val seeMonthTotals: Boolean = false,
    val seeCash: Boolean = false,
    val requestFreezes: Boolean = false,
) {
    companion object {
        fun from(json: JSONObject?) = StaffCan(
            editMemberDates = json?.bool("edit_member_dates") ?: false,
            editFechaAdmin = json?.bool("edit_fecha_admin") ?: false,
            createSales = json?.bool("create_sales") ?: false,
            editSales = json?.bool("edit_sales") ?: false,
            editMembers = json?.bool("edit_members") ?: false,
            deleteMembers = json?.bool("delete_members") ?: false,
            seeAllSales = json?.bool("see_all_sales") ?: false,
            seeMonthTotals = json?.bool("see_month_totals") ?: false,
            seeCash = json?.bool("see_cash") ?: false,
            requestFreezes = json?.bool("request_freezes") ?: false,
        )
    }
}

/** A coach an admin is looking at the app as ("Ver como entrenador"): their gym-scheduler employee. */
data class ViewedCoach(val id: Int, val name: String, val color: String?) {
    val firstName: String get() = name.substringBefore(' ')
}

const val DEFAULT_SITE_URL = "https://novofitclub.com"

/**
 * Owns the signed-in state for the app. The user signs in with their normal
 * WordPress username and password; `novo-staff/v1/login` trades them for an
 * Application Password for this phone, which is all the app keeps. The real
 * password is never stored.
 */
class AuthManager(context: Context) {
    private val prefs = context.getSharedPreferences("novo_auth", Context.MODE_PRIVATE)
    private val store = CredentialStore(context)
    private val http = OkHttpClient()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** Admins only, not saved: the coach whose app they're looking at. */
    val viewingCoach = MutableStateFlow<ViewedCoach?>(null)

    init {
        val savedUrl = prefs.getString(KEY_URL, null) ?: DEFAULT_SITE_URL
        val savedUser = prefs.getString(KEY_USERNAME, null)
        _state.value = if (savedUser != null && store.read(savedUser) != null) {
            AuthState(
                isAuthenticated = true,
                siteUrl = savedUrl,
                username = savedUser,
                role = prefs.getString(KEY_ROLE, null),
                displayName = prefs.getString(KEY_NAME, null).orEmpty(),
                can = StaffCan.from(prefs.getString(KEY_CAN, null)?.let { runCatching { JSONObject(it) }.getOrNull() }),
            )
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

    /** Identifies this install, so signing in again replaces this phone's Application Password. */
    private val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    /**
     * Signs in with the normal WordPress username (or email) and password.
     * The server checks them like wp-login.php and answers with a new
     * Application Password named after this phone.
     */
    suspend fun login(rawUsername: String, password: String) {
        _state.update { it.copy(loginError = null, isValidating = true) }
        val body = JSONObject()
            .put("username", rawUsername.trim())
            .put("password", password)
            .put("device", Build.MODEL ?: "Android")
            .put("device_id", deviceId)
        val request = Request.Builder()
            .url("$DEFAULT_SITE_URL/wp-json/novo-staff/v1/login")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val result: Pair<JSONObject?, String?> = withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                    when (response.code) {
                        in 200..299 -> json to null
                        404 -> null to "El sitio todavía no permite iniciar sesión con la contraseña de WordPress."
                        else -> null to (json?.str("message") ?: "Error del servidor (${response.code}).")
                    }
                }
            } catch (e: IOException) {
                null to (e.localizedMessage ?: "No se pudo contactar al servidor.")
            }
        }

        val login = result.first?.str("username")
        val appPassword = result.first?.str("password")
        if (login == null || appPassword == null) {
            _state.update { it.copy(isValidating = false, loginError = result.second ?: "No se pudo leer la respuesta del servidor.") }
            return
        }
        store.save(login, appPassword)
        prefs.edit().putString(KEY_URL, DEFAULT_SITE_URL).putString(KEY_USERNAME, login).apply()
        _state.value = AuthState(isAuthenticated = true, siteUrl = DEFAULT_SITE_URL, username = login)
        refreshProfile()
    }

    /**
     * Fetches `novo-staff/v1/me`: who is signed in and their role, which
     * decides the tabs. Called after login and on every launch, so a role
     * changed in wp-admin reaches the app without signing out.
     */
    suspend fun refreshProfile() {
        val credentials = currentCredentials ?: return
        val request = Request.Builder()
            .url("${credentials.baseUrl}/wp-json/novo-staff/v1/me")
            .header("Authorization", credentials.basicAuthHeader)
            .header("Accept", "application/json")
            .build()
        val outcome: Any? = withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                        401 -> 401
                        404 -> "El sitio todavía no tiene instalado el plugin Novo Staff API."
                        else -> "No se pudo cargar tu perfil (error ${response.code})."
                    }
                }
            } catch (e: IOException) {
                e.localizedMessage ?: "No se pudo contactar al servidor."
            }
        }
        when (outcome) {
            // Application Password revoked in wp-admin: same as signing out.
            401 -> logout(revoke = false)
            is JSONObject -> {
                val role = outcome.str("role") ?: "other"
                val name = outcome.str("name").orEmpty()
                val can = outcome.obj("can")
                prefs.edit().putString(KEY_ROLE, role).putString(KEY_NAME, name).putString(KEY_CAN, can?.toString()).apply()
                _state.update { it.copy(role = role, displayName = name, can = StaffCan.from(can), profileError = null) }
            }
            // Offline launch keeps the cached role; without one, say why the app can't continue.
            is String -> _state.update { if (it.role == null) it.copy(profileError = outcome) else it }
        }
    }

    /** [revoke] also deletes this phone's Application Password on the server. */
    fun logout(revoke: Boolean = true) {
        val credentials = currentCredentials
        if (revoke && credentials != null) {
            background.launch {
                val request = Request.Builder()
                    .url("${credentials.baseUrl}/wp-json/novo-staff/v1/session")
                    .header("Authorization", credentials.basicAuthHeader)
                    .delete()
                    .build()
                runCatching { http.newCall(request).execute().close() }
            }
        }
        store.delete(_state.value.username)
        prefs.edit().remove(KEY_USERNAME).remove(KEY_ROLE).remove(KEY_NAME).remove(KEY_CAN).apply()
        viewingCoach.value = null
        _state.update { it.copy(isAuthenticated = false, username = "", role = null, displayName = "", can = StaffCan(), profileError = null) }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val KEY_URL = "wp_site_url"
        const val KEY_USERNAME = "wp_username"
        const val KEY_ROLE = "staff_role"
        const val KEY_NAME = "staff_name"
        const val KEY_CAN = "staff_can"
        const val KEY_DEVICE_ID = "staff_device_id"
    }
}
