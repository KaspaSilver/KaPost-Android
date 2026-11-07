package com.kachat.app.util

import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kachat.app.R

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * What the prompt accepts: a strong biometric or the screen lock. Android 9 and 10 (API 28-29)
 * don't support that pairing in androidx.biometric - every check there reported "unsupported" and
 * every gated action failed - so they take the weak tier with the screen lock, which is what
 * their system prompt offers anyway.
 */
private val deviceAuthenticators: Int
    get() = if (android.os.Build.VERSION.SDK_INT in 28..29) BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    else BIOMETRIC_STRONG or DEVICE_CREDENTIAL

/** A screen lock or biometric is set up, so [authenticateWithDeviceCredential] can ask. */
fun Context.hasDeviceCredential(): Boolean =
    BiometricManager.from(this).canAuthenticate(deviceAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Gates [onSuccess] behind whatever the device's own lock screen is set to (fingerprint/face
 * unlock or PIN/pattern/password) — used for the wallet's most sensitive actions (viewing the
 * seed phrase, unlocking a saved account after logout) so they can't be reached by anyone who
 * just picked up an already-unlocked phone.
 *
 * A phone with no secure lock screen at all used to fall straight through to [onSuccess]: the
 * "gate" on the recovery phrase was then no gate, and anyone holding the unlocked phone could
 * read it. It now refuses, says what to set, and calls [onFailure] — matching iOS's `DeviceAuth`.
 */
fun Context.authenticateWithDeviceCredential(
    title: String,
    subtitle: String? = null,
    onSuccess: () -> Unit,
    onFailure: () -> Unit = {}
) {
    val activity = findFragmentActivity()
    if (activity == null) {
        // MainActivity is an AppCompatActivity, so this is unreachable in the app as it stands.
        // Refusing rather than passing keeps it that way: a future host with no FragmentActivity
        // must not silently turn the gate off.
        onFailure()
        return
    }
    val allowedAuthenticators = deviceAuthenticators
    if (BiometricManager.from(activity).canAuthenticate(allowedAuthenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        presentScreenLockRequired(activity)
        onFailure()
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure()
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .apply { subtitle?.let { setSubtitle(it) } }
        .setAllowedAuthenticators(allowedAuthenticators)
        .build()
    prompt.authenticate(promptInfo)
}

/**
 * One dialog, from wherever the reveal was asked: the callers are spread over several screens
 * that have no shared message surface. Mirrors iOS's `presentPasscodeRequired`, naming Android's
 * own setting instead of the iPhone's.
 *
 * iOS 33095ac moved its alert into a window of its own, because one presented from a sheet that
 * was closing went away with it. An Android AlertDialog already is its own window over the
 * activity's, so a Compose sheet closing (Delete Account's dismiss-then-confirm) can't take it
 * down; what is ported is iOS's one-at-a-time guard (`passcodeAlertWindow == nil`).
 */
private fun presentScreenLockRequired(activity: FragmentActivity) {
    if (screenLockAlert?.get()?.isShowing == true || activity.isFinishing) return
    val dialog = AlertDialog.Builder(activity)
        .setTitle(activity.getString(R.string.screen_lock_required))
        .setMessage(activity.getString(R.string.screen_lock_required_message))
        .setPositiveButton(android.R.string.ok, null)
        .create()
    dialog.setOnDismissListener { screenLockAlert = null }
    screenLockAlert = java.lang.ref.WeakReference(dialog)
    dialog.show()
}

/** The alert above, while it's up (iOS 33095ac `passcodeAlertWindow`). Weak, so it never holds
 *  the activity past its life. */
private var screenLockAlert: java.lang.ref.WeakReference<AlertDialog>? = null
