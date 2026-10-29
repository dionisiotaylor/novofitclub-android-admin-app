package com.novofitclub.dashboard.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException

sealed class WPApiException(message: String) : Exception(message) {
    class NotAuthenticated : WPApiException("No hay una sesión activa. Inicia sesión de nuevo.")
    class InvalidUrl : WPApiException("La URL del sitio no es válida.")
    class Unauthorized : WPApiException("Usuario o contraseña de aplicación incorrectos.")
    class Server(val status: Int, detail: String) : WPApiException("Error del servidor ($status): $detail")
    class Decoding : WPApiException("No se pudo leer la respuesta del servidor.")
    class Transport(cause: IOException) : WPApiException(cause.localizedMessage ?: "Error de red.")
}

/**
 * Thin wrapper around the WordPress REST API using HTTP Basic Auth with an
 * Application Password — same endpoints as the iOS app (sale-creator,
 * cash-tracking, sobre-tracking, novo-push and the wp/v2 routes).
 *
 * Records are returned as org.json objects: the plugins have no published
 * schema, so each screen picks out the fields it cares about.
 */
class WPApiClient(
    private val auth: AuthManager,
    private val http: OkHttpClient = OkHttpClient(),
) {
    /** Fetches a JSON array endpoint (most list routes in this API). */
    suspend fun getArray(path: String, query: Map<String, String> = emptyMap()): List<JSONObject> {
        val body = send(request("GET", path, query))
        return when (val parsed = parse(body)) {
            is JSONArray -> parsed.objects()
            // Some list endpoints wrap results in an object, e.g. {"items": [...]}
            is JSONObject -> WRAPPER_KEYS.firstNotNullOfOrNull { parsed.optJSONArray(it) }?.objects()
                ?: throw WPApiException.Decoding()
            else -> throw WPApiException.Decoding()
        }
    }

    /** Fetches a JSON object endpoint (summary-style routes). */
    suspend fun getObject(path: String, query: Map<String, String> = emptyMap()): JSONObject =
        parse(send(request("GET", path, query))) as? JSONObject ?: JSONObject()

    /** POSTs and discards the response (e.g. registering a push token). */
    suspend fun postJson(path: String, body: JSONObject) {
        send(request("POST", path, body = body))
    }

    /** POSTs and returns the created resource (e.g. the new sale's id). */
    suspend fun postObject(path: String, body: JSONObject): JSONObject =
        parse(send(request("POST", path, body = body))) as? JSONObject ?: JSONObject()

    /** Partial update (e.g. `wp/v2/member/{id}`); returns the saved record. */
    suspend fun patchObject(path: String, body: JSONObject): JSONObject =
        parse(send(request("PATCH", path, body = body))) as? JSONObject ?: JSONObject()

    /**
     * Full-replace update (e.g. `sale-creator/v1/sales/{id}`): the body must
     * carry the complete resource, since omitting a field wipes it server-side.
     */
    suspend fun putObject(path: String, body: JSONObject): JSONObject =
        parse(send(request("PUT", path, body = body))) as? JSONObject ?: JSONObject()

    /**
     * Deletes a resource. Without `force=true` WordPress moves the post to
     * Trash, so a mistaken delete is recoverable from wp-admin.
     */
    suspend fun deleteObject(path: String) {
        send(request("DELETE", path))
    }

    /** Deletes and returns the server's answer (e.g. the member screen after removing a program). */
    suspend fun deleteReturningObject(path: String): JSONObject =
        parse(send(request("DELETE", path))) as? JSONObject ?: JSONObject()

    /**
     * POSTs raw bytes as the body (a JPEG photo: the MNT access photo, a
     * freeze's evidence) — the plugins read the body itself, not multipart.
     */
    suspend fun uploadBytes(path: String, bytes: ByteArray, contentType: String, query: Map<String, String> = emptyMap()): JSONObject {
        val credentials = auth.currentCredentials ?: throw WPApiException.NotAuthenticated()
        val url = "${credentials.baseUrl}/wp-json/${path.trimStart('/')}".toHttpUrlOrNull()
            ?.newBuilder()
            ?.apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            ?.build()
            ?: throw WPApiException.InvalidUrl()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials.basicAuthHeader)
            .header("Accept", "application/json")
            .post(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        return parse(send(request)) as? JSONObject ?: JSONObject()
    }

    private fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
    ): Request {
        val credentials = auth.currentCredentials ?: throw WPApiException.NotAuthenticated()
        val url = "${credentials.baseUrl}/wp-json/${path.trimStart('/')}".toHttpUrlOrNull()
            ?.newBuilder()
            ?.apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            ?.build()
            ?: throw WPApiException.InvalidUrl()

        return Request.Builder()
            .url(url)
            .header("Authorization", credentials.basicAuthHeader)
            .header("Accept", "application/json")
            .method(method, body?.toString()?.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when (response.code) {
                    in 200..299 -> text
                    401, 403 -> throw WPApiException.Unauthorized()
                    else -> throw WPApiException.Server(response.code, errorDetail(text))
                }
            }
        } catch (e: IOException) {
            throw WPApiException.Transport(e)
        }
    }

    /**
     * WP validation errors (e.g. "Invalid parameter(s): acf") put the actual
     * per-field reason under data.params / data.details — surface it too.
     */
    private fun errorDetail(text: String): String {
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return text.ifBlank { "Sin detalle" }
        val base = obj.optString("message").ifBlank { "Sin detalle" }
        val data = obj.optJSONObject("data")
        val detail = data?.opt("params") ?: data?.opt("details")
        return if (detail != null) "$base — $detail" else base
    }

    private fun parse(text: String): Any? = try {
        JSONTokener(text).nextValue()
    } catch (e: JSONException) {
        throw WPApiException.Decoding()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val WRAPPER_KEYS = listOf("items", "data", "results", "sales", "members", "movements", "products")
    }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
