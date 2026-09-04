package com.hasyame.marvelchampions.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts a short secret with a key held in the Android Keystore.
 *
 * The app stores one secret and would rather not: a BoardGameGeek password,
 * because BGG has no token or OAuth flow for logging plays. Since it has to be
 * kept, it is kept encrypted with a key that never leaves the Keystore and is
 * hardware-backed where the device supports it. An attacker with the app's
 * files alone gets ciphertext.
 *
 * This is not protection against a rooted device with the app unlocked —
 * nothing at this layer is. It removes the far more likely case: a backup, a
 * file browser, or another app reading a password sitting in plain text.
 *
 * Written directly against the Keystore rather than pulling in a library, since
 * the whole requirement is one string.
 *
 * Open, and only for that: a test runs on a JVM with no Android Keystore in it,
 * where every call here would return null and every caller would correctly
 * conclude there was no stored secret. Tests substitute a plain-text stand-in so
 * they can exercise what the caller does with a secret rather than what it does
 * without one.
 */
@Singleton
open class SecretStore @Inject constructor() {

    /** Returns the ciphertext, IV included, or null if encryption is unavailable. */
    open fun encrypt(plainText: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())

        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // The IV is generated per encryption and is not secret, but it is
        // required to decrypt, so it travels with the ciphertext.
        val packed = cipher.iv + encrypted
        Base64.encodeToString(packed, Base64.NO_WRAP)
    }.getOrNull()

    /**
     * Returns the plain text, or null when it cannot be recovered.
     *
     * Null is the normal outcome after a device restore or a reinstall: the
     * Keystore key does not travel, so the stored ciphertext becomes so much
     * noise. Callers treat that as "not signed in" rather than as an error.
     */
    open fun decrypt(encoded: String): String? = runCatching {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        if (packed.size <= IV_LENGTH) {
            return null
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, packed, 0, IV_LENGTH),
        )
        String(
            cipher.doFinal(packed, IV_LENGTH, packed.size - IV_LENGTH),
            Charsets.UTF_8,
        )
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Deliberately not requiring authentication: plays are reported
                // in the background after a game, and a key that needed a
                // fingerprint would fail there with nobody watching.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mcc_secret_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
