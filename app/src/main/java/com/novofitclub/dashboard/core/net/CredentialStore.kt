package com.novofitclub.dashboard.core.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android counterpart of the iOS KeychainStore: the Application Password is
 * kept in EncryptedSharedPreferences (key material lives in the Android
 * Keystore), never in plain preferences.
 */
class CredentialStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "novo_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun read(account: String): String? = prefs.getString(account, null)

    fun save(account: String, value: String) {
        prefs.edit().putString(account, value).apply()
    }

    fun delete(account: String) {
        prefs.edit().remove(account).apply()
    }
}
