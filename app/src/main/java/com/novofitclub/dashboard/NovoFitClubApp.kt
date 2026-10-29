package com.novofitclub.dashboard

import android.app.Application
import com.novofitclub.dashboard.core.lock.AppLockManager
import com.novofitclub.dashboard.core.net.AuthManager
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.push.PushManager
import kotlinx.coroutines.flow.MutableStateFlow

/** Holds the app-wide singletons (the Android equivalent of the iOS app's environment objects). */
class NovoFitClubApp : Application() {
    lateinit var auth: AuthManager
        private set
    lateinit var api: WPApiClient
        private set
    lateinit var appLock: AppLockManager
        private set
    lateinit var push: PushManager
        private set

    /** A screen a tapped push asks for (`open` in its data): "schedule" opens a coach's Horario. */
    val pendingOpen = MutableStateFlow<String?>(null)

    /** The sale whose push was tapped (`sale_id`); RootScreen opens it for admins, once unlocked. */
    val pendingSaleId = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        auth = AuthManager(this)
        api = WPApiClient(auth)
        appLock = AppLockManager(this)
        push = PushManager(this).also { it.createChannels() }
    }
}
