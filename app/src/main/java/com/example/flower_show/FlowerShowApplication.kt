package com.example.flower_show

import android.app.Application
import com.example.flower_show.data.auth.AuthGraph
import com.example.flower_show.push.FlowerShowNotifications
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlowerShowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FlowerShowNotifications.createChannel(this)
        registerWithFirebaseIfConfigured()
    }

    private fun registerWithFirebaseIfConfigured() {
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        val firebaseApp = FirebaseApp.getApps(this).firstOrNull()
            ?: FirebaseApp.initializeApp(this)
            ?: return
        if (firebaseApp.name == FirebaseApp.DEFAULT_APP_NAME) {
            FirebaseMessaging.getInstance().register()
        }
    }

    companion object {
        private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        internal fun handleFirebaseRegistration(
            application: Application,
            installationId: String,
        ) {
            if (!BuildConfig.FIREBASE_CONFIGURED) return
            processScope.launch {
                AuthGraph.get(application).pushRegistrationCoordinator
                    ?.onRegistered(installationId)
            }
        }
    }
}
