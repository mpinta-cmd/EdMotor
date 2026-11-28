package com.example.edmotor.online

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal sealed interface OwnerBiometricResult {
    data object Authenticated : OwnerBiometricResult
    data object Cancelled : OwnerBiometricResult
    data class Failed(val message: String) : OwnerBiometricResult
}

@Composable
internal fun rememberOwnerBiometricPrompt(identity: Any? = Unit): OwnerBiometricPrompt {
    val context = LocalContext.current
    val prompt = remember(context, identity) { OwnerBiometricPrompt(context) }
    DisposableEffect(prompt) {
        onDispose { prompt.cancel() }
    }
    return prompt
}

internal class OwnerBiometricPrompt(private val context: Context) {
    var isAuthenticating by mutableStateOf(false)
        private set
    private var activeSignal: CancellationSignal? = null

    fun unavailableReason(allowDeviceCredential: Boolean = false): String? = try {
        when {
            Build.VERSION.SDK_INT >= 30 -> OwnerBiometricsApi30.unavailableReason(context, allowDeviceCredential)
            Build.VERSION.SDK_INT >= 29 -> OwnerBiometricsApi29.unavailableReason(context)
            Build.VERSION.SDK_INT >= 28 -> if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
                null
            } else {
                NO_BIOMETRIC_HARDWARE
            }
            else -> "La protección biométrica requiere Android 9 o posterior."
        }
    } catch (_: SecurityException) {
        "Android no permite usar la verificación del dispositivo."
    }

    @MainThread
    fun authenticate(
        title: String,
        description: String,
        allowDeviceCredential: Boolean = false,
        onResult: (OwnerBiometricResult) -> Unit
    ) {
        if (activeSignal != null) return
        if (Build.VERSION.SDK_INT < 28) {
            onResult(OwnerBiometricResult.Failed("La protección biométrica requiere Android 9 o posterior."))
            return
        }
        val unavailable = unavailableReason(allowDeviceCredential)
        if (unavailable != null) {
            onResult(OwnerBiometricResult.Failed(unavailable))
            return
        }

        val signal = CancellationSignal()
        activeSignal = signal
        isAuthenticating = true
        val complete: (OwnerBiometricResult) -> Unit = { result ->
            if (activeSignal === signal) {
                activeSignal = null
                isAuthenticating = false
                onResult(result)
            }
        }
        try {
            OwnerBiometricsApi28.authenticate(context, title, description, allowDeviceCredential, signal, complete)
        } catch (_: RuntimeException) {
            complete(OwnerBiometricResult.Failed("No se pudo abrir la verificación de Android. Inténtalo de nuevo."))
        }
    }

    @MainThread
    fun cancel() {
        val signal = activeSignal ?: return
        activeSignal = null
        isAuthenticating = false
        signal.cancel()
    }
}

@RequiresApi(28)
private object OwnerBiometricsApi28 {
    fun authenticate(
        context: Context,
        title: String,
        description: String,
        allowDeviceCredential: Boolean,
        signal: CancellationSignal,
        onResult: (OwnerBiometricResult) -> Unit
    ) {
        val executor = context.mainExecutor
        val builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setDescription(description)
        if (Build.VERSION.SDK_INT >= 30) {
            OwnerBiometricsApi30.configure(builder, allowDeviceCredential)
        }
        if (Build.VERSION.SDK_INT < 30 || !allowDeviceCredential) {
            builder.setNegativeButton("Cancelar", executor) { _, _ ->
                onResult(OwnerBiometricResult.Cancelled)
            }
        }
        builder.build().authenticate(signal, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(OwnerBiometricResult.Authenticated)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(
                    when (errorCode) {
                        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
                        BiometricPrompt.BIOMETRIC_ERROR_CANCELED -> OwnerBiometricResult.Cancelled
                        BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT ->
                            OwnerBiometricResult.Failed(NO_BIOMETRIC_HARDWARE)
                        BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ->
                            OwnerBiometricResult.Failed(NO_BIOMETRIC_ENROLLMENT)
                        else -> OwnerBiometricResult.Failed(
                            errString.toString().ifBlank { "No se pudo verificar tu identidad. Inténtalo de nuevo." }
                        )
                    }
                )
            }
        })
    }
}

@RequiresApi(29)
private object OwnerBiometricsApi29 {
    @Suppress("DEPRECATION")
    fun unavailableReason(context: Context): String? {
        val manager = context.getSystemService(BiometricManager::class.java) ?: return NO_BIOMETRIC_HARDWARE
        return reasonForCapability(manager.canAuthenticate())
    }
}

@RequiresApi(30)
private object OwnerBiometricsApi30 {
    fun unavailableReason(context: Context, allowDeviceCredential: Boolean): String? {
        val manager = context.getSystemService(BiometricManager::class.java) ?: return NO_BIOMETRIC_HARDWARE
        return reasonForCapability(manager.canAuthenticate(authenticators(allowDeviceCredential)))
    }

    fun configure(builder: BiometricPrompt.Builder, allowDeviceCredential: Boolean) {
        builder.setAllowedAuthenticators(authenticators(allowDeviceCredential))
    }

    private fun authenticators(allowDeviceCredential: Boolean): Int {
        val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
        return if (allowDeviceCredential) strong or BiometricManager.Authenticators.DEVICE_CREDENTIAL else strong
    }
}

private const val NO_BIOMETRIC_HARDWARE = "Este dispositivo no tiene biometría compatible."
private const val NO_BIOMETRIC_ENROLLMENT = "Registra una huella compatible en los ajustes de Android."

@RequiresApi(29)
private fun reasonForCapability(result: Int): String? = when (result) {
    BiometricManager.BIOMETRIC_SUCCESS -> null
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> NO_BIOMETRIC_HARDWARE
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> NO_BIOMETRIC_ENROLLMENT
    else -> "La verificación del dispositivo no está disponible en este momento."
}
