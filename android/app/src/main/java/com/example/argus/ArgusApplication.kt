package com.example.argus

import android.app.Application
import android.content.Context
import com.example.argus.data.local.ArgusLocalStore
import com.example.argus.data.local.ArgusPreferences
import com.example.argus.data.remote.ArgusApiClient
import com.example.argus.data.remote.ArgusWebSocketClient
import com.example.argus.data.repository.*

class AppContainer(private val context: Context) {
    val preferences = ArgusPreferences(context)
    val localStore = ArgusLocalStore(context)

    val apiClient = ArgusApiClient(
        getBaseUrl = { preferences.getServerUrl() },
        getAuthToken = { preferences.getAuthToken() }
    )

    val webSocketClient = ArgusWebSocketClient(
        getWsUrl = { preferences.getWebSocketUrl() },
        getAuthToken = { preferences.getAuthToken() },
        getDeviceId = { preferences.getDeviceId() }
    )

    val authRepository = AuthRepository(preferences, localStore, apiClient, webSocketClient)

    val messageRepository = MessageRepository(
        localStore = localStore,
        preferences = preferences,
        apiClient = apiClient,
        webSocketClient = webSocketClient,
        authRepository = authRepository
    )

    val vaultRepository = VaultRepository(
        localStore = localStore,
        storageDir = context.filesDir
    )

    val callRepository = CallRepository(
        localStore = localStore,
        webSocketClient = webSocketClient
    )

    val shieldRepository = ShieldRepository(preferences, localStore)
    val aiAssistantRepository = AiAssistantRepository()
}

class ArgusApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                android.util.Log.e("ArgusCrashHandler", "FATAL EXCEPTION on thread ${thread.name}", throwable)
            }
        } catch (e: Throwable) {
            // Ignore
        }
        try {
            val provider = org.bouncycastle.jce.provider.BouncyCastleProvider()
            java.security.Security.removeProvider(provider.name)
            java.security.Security.addProvider(provider)
        } catch (e: Throwable) {
            android.util.Log.w("ArgusApplication", "BouncyCastle provider registration warning", e)
        }
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: ArgusApplication
            private set
    }
}
