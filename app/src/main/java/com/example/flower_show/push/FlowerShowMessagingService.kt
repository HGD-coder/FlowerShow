package com.example.flower_show.push

import android.app.Application
import com.example.flower_show.BuildConfig
import com.example.flower_show.FlowerShowApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FlowerShowMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        FlowerShowApplication.handleFirebaseRegistration(
            application = application as Application,
            installationId = installationId,
        )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        FlowerShowNotifications.showForegroundMessage(applicationContext, message)
    }
}
