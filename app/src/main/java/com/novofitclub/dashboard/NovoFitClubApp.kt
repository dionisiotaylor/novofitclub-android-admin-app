package com.novofitclub.dashboard

import android.app.Application
import com.novofitclub.dashboard.core.lock.AppLockManager
import com.novofitclub.dashboard.core.net.AuthManager
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.push.PushManager

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

    override fun onCreate() {
        super.onCreate()
        auth = AuthManager(this)
        api = WPApiClient(auth)
        appLock = AppLockManager(this)
        push = PushManager(this).also { it.createChannel() }
    }
}
