package com.novofitclub.dashboard.feature.members

import android.content.Context
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.objects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Every member in one call (`novo-staff/v1/members/index`, same keys as
 * sale-creator/v1/members), shared by Miembros and the member pickers. The
 * last list is kept on disk so they open instantly while a fresh copy loads.
 */
object MemberIndex {
    private var file: File? = null
    @Volatile private var memory: List<JSONObject>? = null
    @Volatile private var fetchedAt = 0L
    private val mutex = Mutex()

    /** A fetch this recent is reused (e.g. the picker right after Miembros loaded). */
    private const val FRESH_MS = 60_000L

    fun init(context: Context) {
        file = File(context.noBackupFilesDir, "members-index.json")
    }

    /** The last list, from memory or the disk cache; null the first time. */
    suspend fun cached(): List<JSONObject>? = memory ?: withContext(Dispatchers.IO) {
        file?.takeIf { it.exists() }?.let { f -> runCatching { parse(f.readText()) }.getOrNull() }
    }?.also { if (memory == null) memory = it }

    /** A fresh list from the server (or the one fetched less than a minute ago, unless [force]). */
    suspend fun fetch(api: WPApiClient, force: Boolean = false): List<JSONObject> = mutex.withLock {
        val recent = memory
        if (!force && recent != null && System.currentTimeMillis() - fetchedAt < FRESH_MS) return@withLock recent
        val response = api.getObject("novo-staff/v1/members/index")
        val items = response.optJSONArray("items")?.objects().orEmpty()
        memory = items
        fetchedAt = System.currentTimeMillis()
        withContext(Dispatchers.IO) { runCatching { file?.writeText(response.toString()) } }
        items
    }

    /** Signing out: the list (names, cédulas, phones) doesn't stay on the phone. */
    fun clear() {
        memory = null
        fetchedAt = 0L
        file?.delete()
    }

    private fun parse(text: String): List<JSONObject> = JSONObject(text).optJSONArray("items")?.objects().orEmpty()
}
