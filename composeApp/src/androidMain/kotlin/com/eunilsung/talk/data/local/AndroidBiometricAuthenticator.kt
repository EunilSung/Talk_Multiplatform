package com.eunilsung.talk.data.local

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.eunilsung.talk.util.Log
import kotlin.coroutines.resume

/** Android 생체인증 — [BiometricPrompt] (BIOMETRIC_WEAK). [FragmentActivity] 필요. */
class AndroidBiometricAuthenticator(
    private val context: Context,
) : BiometricAuthenticator {

    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK

    override fun isAvailable(): Boolean {
        val result = BiometricManager.from(context).canAuthenticate(authenticators)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun authenticate(
        title: String,
        subtitle: String,
        negativeText: String,
    ): BiometricResult = withContext(Dispatchers.Main) {
        val activity = AndroidActivityHolder.activity as? FragmentActivity
        if (activity == null) {
            Log.message("[Biometric/Android] no FragmentActivity — UNAVAILABLE")
            return@withContext BiometricResult.UNAVAILABLE
        }
        if (!isAvailable()) return@withContext BiometricResult.UNAVAILABLE

        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(BiometricResult.SUCCESS)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val canceled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        if (cont.isActive) {
                            cont.resume(if (canceled) BiometricResult.CANCELED else BiometricResult.UNAVAILABLE)
                        }
                    }

                    override fun onAuthenticationFailed() {
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeText)
                .setAllowedAuthenticators(authenticators)
                .build()
            runCatching { prompt.authenticate(info) }
                .onFailure {
                    Log.message("[Biometric/Android] authenticate failed: ${it.message}")
                    if (cont.isActive) cont.resume(BiometricResult.UNAVAILABLE)
                }
            cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
        }
    }
}
