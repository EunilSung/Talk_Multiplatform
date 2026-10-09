package com.eunilsung.talk

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.eunilsung.talk.data.remote.push.PushTokenBridge
import com.eunilsung.talk.di.appModule
import com.eunilsung.talk.util.Log
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.eunilsung.talk.data.local.AndroidLocalSecretSetup.init(this)

        startKoin {
            androidContext(this@MyApplication)
            modules(appModule, platformModule)
        }

        FirebaseMessaging.getInstance().isAutoInitEnabled = false

        PushTokenBridge.setRefreshAction { refreshFcmToken() }
    }

    private fun refreshFcmToken() {
        FirebaseMessaging.getInstance().deleteToken()
            .addOnCompleteListener { deleteTask ->
                Log.message("[Push/Android] pushToken Active")
                if (!deleteTask.isSuccessful) {
                    Log.message("[Push/Android] deleteToken failed: ${deleteTask.exception?.message}")
                }
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            Log.message("[Push/Android] getToken failed: ${task.exception?.message}")
                            return@addOnCompleteListener
                        }
                        val token = task.result.orEmpty()
                        if (token.isNotBlank()) {
                            Log.message("[Push/Android] reissued token: ${token}")
                            PushTokenBridge.onTokenReceived(token)
                        }
                    }
            }
    }
}