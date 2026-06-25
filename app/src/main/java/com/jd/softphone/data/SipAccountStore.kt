package com.jd.softphone.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the SIP account. Credentials are kept in [EncryptedSharedPreferences]
 * (AES256, backed by the Android Keystore) so the password never sits in plaintext.
 */
@Singleton
class SipAccountStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "sip_account",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _account = MutableStateFlow(load())
    val account: StateFlow<SipAccount?> = _account.asStateFlow()

    fun save(account: SipAccount) {
        prefs.edit()
            .putString(KEY_USERNAME, account.username)
            .putString(KEY_PASSWORD, account.password)
            .putString(KEY_DOMAIN, account.domain)
            .putString(KEY_DISPLAY_NAME, account.displayName)
            .putString(KEY_TRANSPORT, account.transport.name)
            .apply()
        _account.value = account
    }

    fun clear() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    private fun load(): SipAccount? {
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val domain = prefs.getString(KEY_DOMAIN, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return SipAccount(
            username = username,
            password = password,
            domain = domain,
            displayName = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty(),
            transport = runCatching {
                SipTransport.valueOf(prefs.getString(KEY_TRANSPORT, SipTransport.TLS.name)!!)
            }.getOrDefault(SipTransport.TLS),
        )
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_DOMAIN = "domain"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_TRANSPORT = "transport"
    }
}
