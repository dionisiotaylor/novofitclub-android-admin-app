package com.novofitclub.dashboard

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.novofitclub.dashboard.core.ui.NovoTheme
import com.novofitclub.dashboard.feature.lock.LockScreen
import com.novofitclub.dashboard.feature.login.LoginScreen
import com.novofitclub.dashboard.feature.root.RootScreen

// FragmentActivity (not ComponentActivity) because BiometricPrompt needs it.
class MainActivity : FragmentActivity() {

    private val app get() = application as NovoFitClubApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Re-lock whenever the whole app goes to the background, like the iOS scenePhase handler.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = app.appLock.lock()
        })

        setContent {
            NovoTheme {
                val auth by app.auth.state.collectAsState()
                val unlocked by app.appLock.isUnlocked.collectAsState()
                val lockError by app.appLock.lastError.collectAsState()

                if (!auth.isAuthenticated) {
                    LoginScreen(app.auth)
                } else {
                    Box {
                        RootScreen(app)
                        if (!unlocked) {
                            LockScreen(lockError) { app.appLock.authenticate(this@MainActivity) }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (app.auth.state.value.isAuthenticated && !app.appLock.isUnlocked.value) {
            app.appLock.authenticate(this)
        }
    }
}
