package com.apkup.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

interface PrefsBackend {
    fun getString(key: String, defValue: String?): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, defValue: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, defValue: Long): Long
    fun putLong(key: String, value: Long)
    fun contains(key: String): Boolean
    fun remove(key: String)
    fun clear()
}

class InMemoryPrefsBackend : PrefsBackend {
    private val map = ConcurrentHashMap<String, String>()

    override fun getString(key: String, defValue: String?): String? = map[key] ?: defValue

    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        map[key]?.toBooleanStrictOrNull() ?: defValue

    override fun putBoolean(key: String, value: Boolean) {
        map[key] = value.toString()
    }

    override fun getInt(key: String, defValue: Int): Int =
        map[key]?.toIntOrNull() ?: defValue

    override fun putInt(key: String, value: Int) {
        map[key] = value.toString()
    }

    override fun getLong(key: String, defValue: Long): Long =
        map[key]?.toLongOrNull() ?: defValue

    override fun putLong(key: String, value: Long) {
        map[key] = value.toString()
    }

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun clear() {
        map.clear()
    }
}

/**
 * Production encrypted preference backend powered by Google Tink AES-256-GCM.
 *
 * Security guarantees (F-08):
 * 1. Replaces unmaintained 2019 `KryptoPrefs:0.4.3` and removes `jitpack.io`.
 * 2. Binds every encrypted value to its key name via AEAD Associated Data (`associatedData = key`),
 *    preventing cross-key ciphertext substitution.
 * 3. Performs a one-time atomic migration from legacy `ApkUp.xml` / `ApkUp-hybrid.xml` and wipes legacy files.
 * 4. **Zero Plaintext Secret Fallback**: If Android Keystore hardware ever fails completely after recovery,
 *    secret tokens (`githubToken`, `gitlabToken`, `playAuthData`, `apkMirrorCustomApiKey`) are kept strictly in
 *    volatile memory for the current process lifetime and are NEVER written in cleartext to disk.
 */
class TinkSecurePrefsBackend private constructor(
    private val sharedPreferences: SharedPreferences,
    private val aead: Aead?
) : PrefsBackend {

    private val lock = Any()
    private val volatileSecretFallback = ConcurrentHashMap<String, String>()

    override fun getString(key: String, defValue: String?): String? = synchronized(lock) {
        val cipher = aead
        if (cipher == null) {
            return if (key in SECRET_KEYS) {
                volatileSecretFallback[key] ?: defValue
            } else {
                sharedPreferences.getString(fallbackPlainKey(key), defValue)
            }
        }
        val storedKey = storageKey(key)
        val encoded = sharedPreferences.getString(storedKey, null) ?: return defValue
        return runCatching {
            val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
            val plaintext = cipher.decrypt(ciphertext, key.toByteArray(StandardCharsets.UTF_8))
            String(plaintext, StandardCharsets.UTF_8)
        }.getOrElse {
            Log.w(TAG, "Corrupted or unreadable encrypted entry for key '$key'; resetting entry.")
            sharedPreferences.edit().remove(storedKey).apply()
            defValue
        }
    }

    override fun putString(key: String, value: String?): Unit = synchronized(lock) {
        if (value == null) {
            remove(key)
            return
        }
        val cipher = aead
        if (cipher == null) {
            if (key in SECRET_KEYS) {
                volatileSecretFallback[key] = value
                sharedPreferences.edit().remove(storageKey(key)).remove(fallbackPlainKey(key)).apply()
            } else {
                sharedPreferences.edit().putString(fallbackPlainKey(key), value).apply()
            }
            return
        }
        runCatching {
            val ciphertext = cipher.encrypt(
                value.toByteArray(StandardCharsets.UTF_8),
                key.toByteArray(StandardCharsets.UTF_8)
            )
            val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            sharedPreferences.edit().putString(storageKey(key), encoded).apply()
        }.onFailure { e ->
            Log.e(TAG, "Failed to encrypt value for '$key'", e)
            if (key in SECRET_KEYS) {
                volatileSecretFallback[key] = value
            }
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        getString(key, null)?.toBooleanStrictOrNull() ?: defValue

    override fun putBoolean(key: String, value: Boolean) =
        putString(key, value.toString())

    override fun getInt(key: String, defValue: Int): Int =
        getString(key, null)?.toIntOrNull() ?: defValue

    override fun putInt(key: String, value: Int) =
        putString(key, value.toString())

    override fun getLong(key: String, defValue: Long): Long =
        getString(key, null)?.toLongOrNull() ?: defValue

    override fun putLong(key: String, value: Long) =
        putString(key, value.toString())

    override fun contains(key: String): Boolean = synchronized(lock) {
        if (aead == null) {
            if (key in SECRET_KEYS) volatileSecretFallback.containsKey(key)
            else sharedPreferences.contains(fallbackPlainKey(key))
        } else {
            sharedPreferences.contains(storageKey(key))
        }
    }

    override fun remove(key: String): Unit = synchronized(lock) {
        volatileSecretFallback.remove(key)
        sharedPreferences.edit()
            .remove(storageKey(key))
            .remove(fallbackPlainKey(key))
            .apply()
    }

    override fun clear(): Unit = synchronized(lock) {
        volatileSecretFallback.clear()
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val TAG = "SecurePrefsStore"
        private const val PREFS_FILE_NAME = "apkup_secure_prefs_v2"
        private const val KEYSET_PREFS_NAME = "apkup_tink_keyset"
        private const val KEYSET_NAME = "apkup_master_keyset"
        private const val MASTER_KEY_URI = "android-keystore://apkup_master_key_v2"
        private const val MIGRATION_MARKER_KEY = "__legacy_kryptoprefs_migrated_v1"

        private const val LEGACY_PREFS_NAME = "ApkUp"
        private const val LEGACY_HYBRID_PREFS_NAME = "ApkUp-hybrid"

        internal val SECRET_KEYS = setOf(
            "githubToken",
            "gitlabToken",
            "codebergToken",
            "playAuthData",
            "apkMirrorCustomApiUser",
            "apkMirrorCustomApiKey"
        )

        private val KNOWN_LEGACY_KEYS = listOf(
            "ignoredApps",
            "showSystemApps",
            "hasSeenInstallPermissionPrompt",
            "ignoredVersions",
            "ignoredVersionEntries",
            "ignoreAppsFilterActive",
            "ignoreVersionsFilterActive",
            "appsViewMode",
            "useApkMirror",
            "directApkMirrorDownloads",
            "usePlay",
            "useIzzyOnDroid",
            "useGitHub",
            "useGitLab",
            "useCodeberg",
            "useCustomFDroidRepos",
            "customFDroidRepos",
            "watchedApps",
            "incompatibleRepoSignatures",
            "githubToken",
            "gitlabToken",
            "codebergToken",
            "customRepoBindings",
            "enableAlarm",
            "backgroundCheckIntervalHours",
            "theme",
            "playAuthData",
            "lastPlayCheck",
            "useShizukuInstaller",
            "useRootInstaller",
            "customDownloadFolderUri",
            "autoInstallUpdates",
            "autoInstallWifiOnly",
            "autoInstallChargingOnly",
            "downloadWifiOnly",
            "downloadChargingOnly",
            "backgroundCheckWifiOnly",
            "last_seen_version_code",
            "cachedUpdateSnapshot",
            "updateHistoryEntries"
        )

        fun create(context: Context): TinkSecurePrefsBackend {
            val appContext = context.applicationContext ?: context
            val targetPrefs = appContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
            val aead = initOrRecoverTinkAead(appContext, targetPrefs)
            val backend = TinkSecurePrefsBackend(targetPrefs, aead)
            migrateLegacyKryptoPrefsIfNeeded(appContext, targetPrefs, backend)
            return backend
        }

        private fun initOrRecoverTinkAead(
            context: Context,
            targetPrefs: SharedPreferences
        ): Aead? {
            return runCatching {
                AeadConfig.register()
                buildTinkAead(context)
            }.recoverCatching { firstError ->
                Log.w(TAG, "Primary Tink keyset init failed; resetting invalidated keystore entry", firstError)
                // Recover from KeyPermanentlyInvalidatedException / OS backup keyset mismatch
                context.getSharedPreferences(KEYSET_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
                targetPrefs.edit().clear().commit()
                runCatching {
                    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    if (ks.containsAlias("apkup_master_key_v2")) {
                        ks.deleteEntry("apkup_master_key_v2")
                    }
                }
                buildTinkAead(context)
            }.onFailure { fatalError ->
                Log.e(TAG, "Android Keystore unavailable; secrets restricted to volatile RAM only", fatalError)
            }.getOrNull()
        }

        private fun buildTinkAead(context: Context): Aead {
            val handle = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            return handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }

        private fun migrateLegacyKryptoPrefsIfNeeded(
            context: Context,
            targetPrefs: SharedPreferences,
            backend: TinkSecurePrefsBackend
        ) {
            if (targetPrefs.getBoolean(MIGRATION_MARKER_KEY, false)) return

            runCatching {
                val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                val rawAll = legacyPrefs.all
                if (rawAll.isNotEmpty()) {
                    val migratedValues = LinkedHashMap<String, String>()

                    // 1. Migrate any unencrypted legacy/nocrypt keys first
                    for (knownKey in KNOWN_LEGACY_KEYS) {
                        val rawVal = rawAll[knownKey]
                        if (rawVal != null) {
                            migratedValues[knownKey] = rawVal.toString()
                        }
                    }

                    // 2. Decrypt KryptoBuilder.hybrid(context, "ApkUp") entries if present
                    val hybridSecretKey = deriveLegacyHybridSecretKey(context)
                    if (hybridSecretKey != null) {
                        for (knownKey in KNOWN_LEGACY_KEYS) {
                            val hashedKey = legacySha512Base64(knownKey)
                            val cipherTextB64 = rawAll[hashedKey] as? String ?: continue
                            val decrypted = decryptLegacyAesGcm(hybridSecretKey, cipherTextB64)
                            if (decrypted != null) {
                                migratedValues[knownKey] = decrypted
                            }
                        }
                    }

                    var migratedAny = false
                    for ((key, value) in migratedValues) {
                        if (!backend.contains(key)) {
                            backend.putString(key, value)
                            migratedAny = true
                        }
                    }
                    // Existing users upgrading with saved settings already consented via prior setup
                    if (migratedAny && !backend.contains("hasCompletedOnboarding")) {
                        backend.putBoolean("hasCompletedOnboarding", true)
                    }
                }

                // Wipe legacy preference files & legacy KeyStore key
                legacyPrefs.edit().clear().apply()
                context.getSharedPreferences(LEGACY_HYBRID_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                runCatching {
                    context.deleteSharedPreferences(LEGACY_PREFS_NAME)
                    context.deleteSharedPreferences(LEGACY_HYBRID_PREFS_NAME)
                }
                runCatching {
                    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    if (ks.containsAlias(LEGACY_HYBRID_PREFS_NAME)) {
                        ks.deleteEntry(LEGACY_HYBRID_PREFS_NAME)
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Legacy preferences migration completed with warnings", e)
            }

            targetPrefs.edit().putBoolean(MIGRATION_MARKER_KEY, true).apply()
        }

        private fun deriveLegacyHybridSecretKey(context: Context): java.security.Key? = runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(LEGACY_HYBRID_PREFS_NAME)) return null
            val keystoreKey = ks.getKey(LEGACY_HYBRID_PREFS_NAME, null) ?: return null
            val hybridPrefs = context.getSharedPreferences(LEGACY_HYBRID_PREFS_NAME, Context.MODE_PRIVATE)
            val encPass = hybridPrefs.getString(legacySha512Base64("HybridKey-ApkUp-pass"), null) ?: return null
            val encSalt = hybridPrefs.getString(legacySha512Base64("HybridKey-ApkUp-salt"), null) ?: return null
            val pass = decryptLegacyAesGcm(keystoreKey, encPass) ?: return null
            val salt = decryptLegacyAesGcm(keystoreKey, encSalt) ?: return null
            val spec = PBEKeySpec(
                pass.toCharArray(),
                salt.toByteArray(StandardCharsets.UTF_8),
                1000,
                256
            )
            val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec)
            SecretKeySpec(secret.encoded, "AES")
        }.getOrNull()

        private fun decryptLegacyAesGcm(key: java.security.Key, base64Payload: String): String? = runCatching {
            val data = Base64.decode(base64Payload, Base64.NO_WRAP)
            if (data.size <= 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
            val plain = cipher.doFinal(data, 12, data.size - 12)
            String(plain, StandardCharsets.UTF_8)
        }.getOrNull()

        private fun legacySha512Base64(text: String): String {
            val digest = MessageDigest.getInstance("SHA-512")
                .digest(text.toByteArray(StandardCharsets.UTF_8))
            return Base64.encodeToString(digest, Base64.NO_WRAP)
        }

        private fun storageKey(key: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(key.toByteArray(StandardCharsets.UTF_8))
            return "enc_" + Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        private fun fallbackPlainKey(key: String): String = "plain_$key"
    }
}
