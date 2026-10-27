package com.cordobot.cuentaclara.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cordobot.cuentaclara.domain.ConnectivityMonitor
import com.cordobot.cuentaclara.domain.SessionStore
import com.cordobot.cuentaclara.domain.model.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session details stored with EncryptedSharedPreferences: keys and values are encrypted with
 * AES-256 and the master key lives in the Android Keystore. Passwords are never stored; Firebase
 * keeps its own token.
 */
@Singleton
class EncryptedSessionStore @Inject constructor(@ApplicationContext context: Context) : SessionStore {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override var lastEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit { putString(KEY_EMAIL, value) }

    override var lastGroupId: String?
        get() = prefs.getString(KEY_GROUP, null)
        set(value) = prefs.edit { putString(KEY_GROUP, value) }

    override fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_EMAIL = "last_email"
        const val KEY_GROUP = "last_group"
    }
}

@Singleton
class AndroidConnectivityMonitor @Inject constructor(@ApplicationContext context: Context) : ConnectivityMonitor {

    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override val state: Flow<ConnectionState> = callbackFlow {
        fun current(): ConnectionState {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            return if (online) ConnectionState.Online else ConnectionState.Offline
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(current()) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(current()) }
        }
        trySend(current())
        manager.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            callback
        )
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

object NotificationChannels {
    const val EXPENSES = "expenses"

    fun create(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(EXPENSES, "Gastos del grupo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos cuando alguien agrega un gasto en tus grupos"
            }
        )
    }
}
