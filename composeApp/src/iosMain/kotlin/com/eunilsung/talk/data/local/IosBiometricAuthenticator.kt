package com.eunilsung.talk.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import com.eunilsung.talk.util.Log
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

/** iOS 생체인증 — LocalAuthentication ([LAContext]) Face ID / Touch ID. */
@OptIn(ExperimentalForeignApi::class)
class IosBiometricAuthenticator : BiometricAuthenticator {

    override fun isAvailable(): Boolean =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)

    override suspend fun authenticate(
        title: String,
        subtitle: String,
        negativeText: String,
    ): BiometricResult = suspendCancellableCoroutine { cont ->
        val ctx = LAContext()
        ctx.localizedCancelTitle = negativeText
        if (!ctx.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)) {
            if (cont.isActive) cont.resume(BiometricResult.UNAVAILABLE)
            return@suspendCancellableCoroutine
        }
        ctx.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = subtitle.ifBlank { title },
        ) { success, error ->
            val result = when {
                success -> BiometricResult.SUCCESS
                error != null -> when (error.code) {
                    -2L, -4L, -9L, -3L -> BiometricResult.CANCELED
                    -6L, -7L, -8L -> BiometricResult.UNAVAILABLE
                    else -> BiometricResult.FAILED
                }
                else -> BiometricResult.FAILED
            }
            if (cont.isActive) cont.resume(result)
        }
    }
}
